/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Fetches and caches item prices from prices.runescape.wiki.
 */
package com.slayersimplified.loot;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches GE and alch prices from prices.runescape.wiki and enriches
 * drop table sections with that data.
 * <p>
 * Both the item mapping (name→id, id→highalch) and the full latest-price
 * snapshot are fetched once per session and cached as static futures so
 * every subsequent monster lookup is served from memory.
 */
@Slf4j
public class WikiPriceCache
{
    private static final String MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
    private static final String PRICES_URL  = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final String USER_AGENT  = RuneLite.USER_AGENT + " (slayer-simplified)";

    // Static Gson: no custom config, equivalent to the Guice-provided default.
    private static final Gson GSON = new Gson();

    // Session-level caches — initialised at most once each.
    private static volatile CompletableFuture<Map<String, Integer>> nameToIdFuture  = null;
    private static volatile CompletableFuture<Map<Integer, Integer>> idToAlchFuture = null;
    private static volatile CompletableFuture<Map<Integer, Integer>> idToPriceFuture = null;

    // -- Public API ----------------------------------------------------------

    /**
     * Enriches all WikiItem objects in the given sections with GE and alch prices.
     * On any network/parse failure the original (price-less) sections are returned.
     */
    public static CompletableFuture<DropTableSection[]> enrichDropTables(
            OkHttpClient client, DropTableSection[] sections)
    {
        if (sections.length == 0)
        {
            return CompletableFuture.completedFuture(sections);
        }

        CompletableFuture<Map<String, Integer>> mappingF = loadNameToId(client);
        CompletableFuture<Map<Integer, Integer>> alchF   = loadIdToAlch(client);
        CompletableFuture<Map<Integer, Integer>> pricesF = loadIdToPrice(client);

        return CompletableFuture.allOf(mappingF, alchF, pricesF)
                .thenApply(ignored ->
                {
                    Map<String, Integer> nameToId  = mappingF.join();
                    Map<Integer, Integer> idToAlch  = alchF.join();
                    Map<Integer, Integer> idToPrice = pricesF.join();
                    applyPrices(sections, nameToId, idToAlch, idToPrice);
                    return sections;
                })
                .exceptionally(ex ->
                {
                    log.warn("Price enrichment failed, showing drops without prices: {}", ex.getMessage());
                    return sections;
                });
    }

    // -- Apply prices to sections --------------------------------------------

    private static void applyPrices(
            DropTableSection[] sections,
            Map<String, Integer> nameToId,
            Map<Integer, Integer> idToAlch,
            Map<Integer, Integer> idToPrice)
    {
        for (DropTableSection section : sections)
        {
            if (section.getTable() == null) continue;
            Map<String, WikiItem[]> newTable = new LinkedHashMap<>();
            for (Map.Entry<String, WikiItem[]> entry : section.getTable().entrySet())
            {
                WikiItem[] oldItems = entry.getValue();
                WikiItem[] newItems = new WikiItem[oldItems.length];
                for (int i = 0; i < oldItems.length; i++)
                {
                    WikiItem item = oldItems[i];
                    Integer id = nameToId.get(item.getName().toLowerCase());
                    int gePrice = (id != null) ? idToPrice.getOrDefault(id, -1) : -1;
                    int alch    = (id != null) ? idToAlch.getOrDefault(id, -1)  : -1;
                    newItems[i] = (gePrice > 0 || alch > 0) ? item.withPrices(gePrice, alch) : item;
                }
                newTable.put(entry.getKey(), newItems);
            }
            section.setTable(newTable);
        }
    }

    // -- Lazy session caches -------------------------------------------------

    private static synchronized CompletableFuture<Map<String, Integer>> loadNameToId(OkHttpClient client)
    {
        if (nameToIdFuture == null)
        {
            nameToIdFuture = fetchMappingNameToId(client);
        }
        return nameToIdFuture;
    }

    private static synchronized CompletableFuture<Map<Integer, Integer>> loadIdToAlch(OkHttpClient client)
    {
        if (idToAlchFuture == null)
        {
            idToAlchFuture = fetchMappingIdToAlch(client);
        }
        return idToAlchFuture;
    }

    private static synchronized CompletableFuture<Map<Integer, Integer>> loadIdToPrice(OkHttpClient client)
    {
        if (idToPriceFuture == null)
        {
            idToPriceFuture = fetchAllLatestPrices(client);
        }
        return idToPriceFuture;
    }

    // -- Fetch helpers -------------------------------------------------------

    /**
     * Parses the /mapping response into a lowercase-name → item-id map.
     */
    private static CompletableFuture<Map<String, Integer>> fetchMappingNameToId(OkHttpClient client)
    {
        return fetchRaw(client, MAPPING_URL).thenApply(json ->
        {
            Map<String, Integer> map = new HashMap<>();
            try
            {
                JsonArray arr = GSON.fromJson(json, JsonArray.class);
                for (JsonElement elem : arr)
                {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    if (!obj.has("id") || !obj.has("name")) continue;
                    map.put(obj.get("name").getAsString().toLowerCase(), obj.get("id").getAsInt());
                }
                log.debug("Price mapping loaded: {} items", map.size());
            }
            catch (Exception e)
            {
                log.error("Failed to parse item mapping (name→id)", e);
            }
            return map;
        });
    }

    /**
     * Parses the /mapping response into an item-id → highalch map.
     */
    private static CompletableFuture<Map<Integer, Integer>> fetchMappingIdToAlch(OkHttpClient client)
    {
        return fetchRaw(client, MAPPING_URL).thenApply(json ->
        {
            Map<Integer, Integer> map = new HashMap<>();
            try
            {
                JsonArray arr = GSON.fromJson(json, JsonArray.class);
                for (JsonElement elem : arr)
                {
                    if (!elem.isJsonObject()) continue;
                    JsonObject obj = elem.getAsJsonObject();
                    if (!obj.has("id") || !obj.has("highalch")) continue;
                    if (obj.get("highalch").isJsonNull()) continue;
                    int highalch = obj.get("highalch").getAsInt();
                    if (highalch > 0) map.put(obj.get("id").getAsInt(), highalch);
                }
            }
            catch (Exception e)
            {
                log.error("Failed to parse item mapping (id→alch)", e);
            }
            return map;
        });
    }

    /**
     * Fetches ALL current prices from /latest and returns an item-id → high-price map.
     * The full snapshot is ~200 KB and is fetched only once per session.
     */
    private static CompletableFuture<Map<Integer, Integer>> fetchAllLatestPrices(OkHttpClient client)
    {
        return fetchRaw(client, PRICES_URL).thenApply(json ->
        {
            Map<Integer, Integer> map = new HashMap<>();
            try
            {
                JsonObject root = GSON.fromJson(json, JsonObject.class);
                if (root == null || !root.has("data")) return map;
                JsonObject data = root.getAsJsonObject("data");
                for (Map.Entry<String, JsonElement> entry : data.entrySet())
                {
                    try
                    {
                        int id = Integer.parseInt(entry.getKey());
                        if (!entry.getValue().isJsonObject()) continue;
                        JsonObject prices = entry.getValue().getAsJsonObject();
                        if (prices.has("high") && !prices.get("high").isJsonNull())
                        {
                            int high = prices.get("high").getAsInt();
                            if (high > 0) map.put(id, high);
                        }
                    }
                    catch (NumberFormatException ignored) {}
                }
                log.debug("Loaded {} item prices from /latest", map.size());
            }
            catch (Exception e)
            {
                log.error("Failed to parse latest prices", e);
            }
            return map;
        });
    }

    // -- HTTP ----------------------------------------------------------------

    private static CompletableFuture<String> fetchRaw(OkHttpClient client, String url)
    {
        CompletableFuture<String> future = new CompletableFuture<>();
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();
        client.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException ex)
            {
                log.error("Price API request failed for '{}': {}", url, ex.getMessage(), ex);
                future.completeExceptionally(ex);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (ResponseBody body = response.body())
                {
                    if (!response.isSuccessful() || body == null)
                    {
                        log.warn("Price API non-success {} for '{}'", response.code(), url);
                        future.complete("{}");
                        return;
                    }
                    future.complete(body.string());
                }
            }
        });
        return future;
    }
}
