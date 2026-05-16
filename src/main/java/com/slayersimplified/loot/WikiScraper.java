/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Scrapes drop-table data and combat stats from the OSRS Wiki.
 * Ported and adapted from the loot-lookup-plugin by donth77.
 */
package com.slayersimplified.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scrapes drop-table data from the OSRS Wiki for a given monster.
 * Ported and adapted from the loot-lookup-plugin by donth77.
 */
@Slf4j
public class WikiScraper
{
    private static final String BASE_URL = "https://oldschool.runescape.wiki";
    private static final String BASE_WIKI_URL = BASE_URL + "/w/";
    private static final String USER_AGENT = RuneLite.USER_AGENT + " (slayer-simplified)";

    /** In-memory cache for combat stats keyed by monster name. */
    private static final Map<String, CombatStats> combatStatsCache = new ConcurrentHashMap<>();

    public static CompletableFuture<DropTableSection[]> getDropsByMonster(
            OkHttpClient okHttpClient, String monsterName)
    {
        CompletableFuture<DropTableSection[]> future = new CompletableFuture<>();
        String url = getWikiUrl(monsterName);

        log.debug("Looking up drops for monster: '{}' at URL: {}", monsterName, url);

        requestAsync(okHttpClient, url).whenCompleteAsync((responseHTML, ex) ->
        {
            if (ex != null)
            {
                log.error("HTTP request failed for monster '{}': {}", monsterName, ex.getMessage(), ex);
                future.complete(new DropTableSection[0]);
                return;
            }

            try
            {
                DropTableSection[] result = parseDropTables(responseHTML, monsterName);
                if (result.length == 0)
                {
                    log.warn("No drop tables found for monster '{}'", monsterName);
                }
                else
                {
                    log.debug("Found {} drop table section(s) for monster '{}'", result.length, monsterName);
                }
                future.complete(result);
            }
            catch (Exception e)
            {
                log.error("Error parsing drop tables for '{}'", monsterName, e);
                future.complete(new DropTableSection[0]);
            }
        });

        return future;
    }

    /**
     * Scrape combat stats from the OSRS Wiki infobox for the given monster.
     * Returns a CompletableFuture that resolves to a CombatStats object (never null).
     */
    public static CompletableFuture<CombatStats> getCombatStats(
            OkHttpClient okHttpClient, String monsterName)
    {
        // Return cached result immediately if available
        CombatStats cached = combatStatsCache.get(monsterName);
        if (cached != null)
        {
            log.debug("Combat stats cache hit for '{}'", monsterName);
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<CombatStats> future = new CompletableFuture<>();
        String url = getWikiUrl(monsterName);

        log.debug("Looking up combat stats for monster: '{}' at URL: {}", monsterName, url);

        requestAsync(okHttpClient, url).whenCompleteAsync((responseHTML, ex) ->
        {
            if (ex != null)
            {
                log.error("HTTP request failed for combat stats '{}': {}", monsterName, ex.getMessage(), ex);
                future.complete(emptyCombatStats());
                return;
            }

            try
            {
                CombatStats stats = parseCombatStats(responseHTML);
                log.debug("Parsed combat stats for '{}': CB={} HP={} MaxHit={} Style={}",
                        monsterName, stats.getCombatLevel(), stats.getHitpoints(),
                        stats.getMaxHit(), stats.getAttackStyle());
                combatStatsCache.put(monsterName, stats);
                future.complete(stats);
            }
            catch (Exception e)
            {
                log.error("Error parsing combat stats for '{}'", monsterName, e);
                future.complete(emptyCombatStats());
            }
        });

        return future;
    }

    static CombatStats parseCombatStats(String html)
    {
        Document doc = Jsoup.parse(html);
        Element infobox = doc.selectFirst(".infobox-monster");
        if (infobox == null)
        {
            log.warn("No infobox-monster found in HTML");
            return emptyCombatStats();
        }

        // Strategy 1: try data-attr-param (present on many monster pages)
        String combatLevel = attrText(infobox, "combat");
        if (!combatLevel.isEmpty())
        {
            return parseCombatStatsFromAttr(infobox);
        }

        // Strategy 2: fall back to th/td text matching
        log.debug("data-attr-param not found, falling back to text matching");
        return parseCombatStatsFromTable(infobox);
    }

    /**
     * Parse combat stats using data-attr-param attributes (newer wiki format).
     */
    private static CombatStats parseCombatStatsFromAttr(Element infobox)
    {
        String combatLevel = attrText(infobox, "combat");
        String hitpoints = attrText(infobox, "hitpoints");
        String maxHit = attrText(infobox, "max_hit_fmt");
        String attackStyle = attrText(infobox, "attack style");
        String attribute = attrText(infobox, "attributes");

        // Elemental weakness — type is an img alt like "Water elemental weakness"
        String weaknessType = "";
        Element weaknessTypeEl = infobox.selectFirst("[data-attr-param=elementalweaknesstype] img");
        if (weaknessTypeEl != null)
        {
            String alt = weaknessTypeEl.attr("alt");
            if (alt != null && !alt.isEmpty())
            {
                weaknessType = alt.replace(" elemental weakness", "").trim();
            }
        }
        String weaknessPercent = attrText(infobox, "elementalweaknesspercent");

        String immunePoison = attrText(infobox, "immunepoison");
        String immuneVenom = attrText(infobox, "immunevenom");
        String immuneCannon = attrText(infobox, "immunecannon");
        String immuneThrall = attrText(infobox, "immunethrall");
        String immuneBurn = attrText(infobox, "immuneburn");

        return new CombatStats(
                combatLevel, hitpoints, maxHit, attackStyle, attribute,
                weaknessType, weaknessPercent,
                immunePoison, immuneVenom, immuneCannon, immuneThrall, immuneBurn);
    }

    /**
     * Parse combat stats by matching th/td text content (older wiki format
     * without data-attr-param).
     */
    private static CombatStats parseCombatStatsFromTable(Element infobox)
    {
        String combatLevel = thTdText(infobox, "Combat level");
        String maxHit = thTdText(infobox, "Max hit");
        String attackStyle = thTdText(infobox, "Attack style");
        String attribute = thTdText(infobox, "Attribute");

        // Hitpoints are in the combat stats grid: icon row then value row
        String hitpoints = getHitpointsFromGrid(infobox);

        // Elemental weakness — look for th containing "Elemental weakness" with img
        String weaknessType = "";
        String weaknessPercent = "";
        Element weaknessRow = findThContaining(infobox, "Elemental weakness");
        if (weaknessRow != null)
        {
            Element img = weaknessRow.selectFirst("img");
            if (img != null)
            {
                String alt = img.attr("alt");
                if (alt != null && !alt.isEmpty())
                {
                    weaknessType = alt.replace(" elemental weakness", "").trim();
                }
            }
            Element td = weaknessRow.parent() != null ? weaknessRow.parent().selectFirst("td") : null;
            if (td != null)
            {
                weaknessPercent = td.text().trim();
            }
        }

        // Immunities — in the "Immunities" section, th text = Poison/Venom/Cannons/Thralls
        String immunePoison = getImmunityText(infobox, "Poison");
        String immuneVenom = getImmunityText(infobox, "Venom");
        String immuneCannon = getImmunityText(infobox, "Cannons");
        String immuneThrall = getImmunityText(infobox, "Thralls");
        // Some pages have "Burn" or it may be absent
        String immuneBurn = getImmunityText(infobox, "Burn");

        return new CombatStats(
                combatLevel, hitpoints, maxHit, attackStyle, attribute,
                weaknessType, weaknessPercent,
                immunePoison, immuneVenom, immuneCannon, immuneThrall, immuneBurn);
    }

    /**
     * Find a th element whose text contains the given label, then return the
     * td sibling text from the same tr.
     */
    private static String thTdText(Element infobox, String headerText)
    {
        for (Element tr : infobox.select("tr"))
        {
            Element th = tr.selectFirst("th");
            Element td = tr.selectFirst("td");
            if (th != null && td != null && th.text().contains(headerText))
            {
                return td.text().trim();
            }
        }
        return "";
    }

    /**
     * Find a th element whose text or title attributes contain the label.
     */
    private static Element findThContaining(Element infobox, String label)
    {
        for (Element th : infobox.select("th"))
        {
            if (th.text().contains(label))
            {
                return th;
            }
            // Also check link titles
            Element a = th.selectFirst("a[title*=" + label + "]");
            if (a != null)
            {
                return th;
            }
        }
        return null;
    }

    /**
     * Extract Hitpoints from the combat stats grid. The grid has an icon header row
     * (HP, Attack, Strength, Defence, Magic, Ranged) followed by a value row.
     * HP is always the first column.
     */
    private static String getHitpointsFromGrid(Element infobox)
    {
        Elements rows = infobox.select("tr");
        for (int i = 0; i < rows.size(); i++)
        {
            Element row = rows.get(i);
            // Look for the row containing the Hitpoints icon
            Element hpLink = row.selectFirst("th a[href*=Hitpoints]");
            if (hpLink != null)
            {
                // Value row is the next tr
                if (i + 1 < rows.size())
                {
                    Element valueRow = rows.get(i + 1);
                    Element firstTd = valueRow.selectFirst("td");
                    if (firstTd != null)
                    {
                        return firstTd.text().trim();
                    }
                }
            }
        }
        return "";
    }

    /**
     * Get immunity text for a given label from the Immunities section.
     * The Immunities section has rows like: th=Poison, td=Not immune.
     * We only match rows AFTER the "Immunities" subheader to avoid
     * matching the "Poisonous" row in combat info.
     */
    private static String getImmunityText(Element infobox, String label)
    {
        boolean inImmunities = false;
        for (Element tr : infobox.select("tr"))
        {
            Element subheader = tr.selectFirst("th.infobox-subheader");
            if (subheader != null && subheader.text().contains("Immunities"))
            {
                inImmunities = true;
                continue;
            }
            if (subheader != null && inImmunities)
            {
                // Entered a new section after Immunities
                break;
            }
            if (inImmunities)
            {
                Element th = tr.selectFirst("th");
                Element td = tr.selectFirst("td");
                if (th != null && td != null && th.text().trim().equalsIgnoreCase(label))
                {
                    return td.text().trim();
                }
            }
        }
        return "";
    }

    private static String attrText(Element infobox, String param)
    {
        Element el = infobox.selectFirst("[data-attr-param=\"" + param + "\"]");
        if (el == null)
        {
            return "";
        }
        return el.text().trim();
    }

    private static CombatStats emptyCombatStats()
    {
        return new CombatStats("", "", "", "", "", "", "", "", "", "", "", "");
    }

    private static DropTableSection[] parseDropTables(String html, String monsterName)
    {
        Document doc = Jsoup.parse(html);
        List<DropTableSection> dropTableSections = new ArrayList<>();

        Elements tableHeaders = doc.select("h2, h3, h4");

        boolean parseDropTableSection = false;
        DropTableSection currDropTableSection = new DropTableSection();
        Map<String, WikiItem[]> currDropTable = new LinkedHashMap<>();

        @SuppressWarnings("unused")
        boolean incrementH3Index = false;

        for (Element tableHeader : tableHeaders)
        {
            String tableHeaderText = tableHeader.text();
            String monsterNameLC = monsterName.toLowerCase();

            // Edge cases
            if (monsterNameLC.equals("hespori") && tableHeaderText.equals("Main table")) continue;
            if (monsterNameLC.equals("chaos elemental") && tableHeaderText.equals("Major drops")) continue;
            if (monsterNameLC.equals("cyclops") && tableHeaderText.equals("Drops")) continue;
            if (monsterNameLC.equals("gorak") && tableHeaderText.equals("Drops")) continue;
            if (monsterNameLC.equals("undead druid") && tableHeaderText.equals("Seeds"))
            {
                incrementH3Index = true;
                continue;
            }

            String tableHeaderTextLower = tableHeaderText.toLowerCase();
            boolean isDropsTableHeader = tableHeaderTextLower.contains("drop")
                    || tableHeaderTextLower.contains("levels")
                    || tableHeaderTextLower.contains("reward")
                    || isDropsHeaderForEdgeCases(monsterName, tableHeaderText);
            boolean isPickpocketLootHeader = tableHeaderTextLower.contains("loot");
            boolean parseH3Primary = isPickpocketLootHeader || parseH3PrimaryForEdgeCases(monsterName);

            String tagName = tableHeader.tagName().toLowerCase();
            boolean isParentH2 = tagName.equals("h2");
            boolean isParentH3 = tagName.equals("h3");
            boolean isParentH4 = tagName.equals("h4");

            if (isParentH3 && tableHeaderText.equals("Regular drops"))
            {
                incrementH3Index = true;
                continue;
            }

            if (isParentH2 || (parseH3Primary && isParentH3))
            {
                if (!currDropTable.isEmpty())
                {
                    currDropTableSection.setTable(currDropTable);
                    dropTableSections.add(currDropTableSection);
                    currDropTable = new LinkedHashMap<>();
                    currDropTableSection = new DropTableSection();
                }

                if (isDropsTableHeader || isPickpocketLootHeader)
                {
                    parseDropTableSection = true;
                    currDropTableSection.setHeader(tableHeaderText);
                }
                else
                {
                    parseDropTableSection = false;
                }
            }
            else if (parseDropTableSection && (isParentH3 || isParentH4))
            {
                Element searchStart = tableHeader;
                Element parent = tableHeader.parent();
                if (parent != null && parent.hasClass("mw-heading"))
                {
                    searchStart = parent;
                }

                Element searchElement = searchStart.nextElementSibling();
                Element foundTable = null;
                int searchDepth = 0;

                while (searchElement != null && searchDepth < 5)
                {
                    if (searchElement.tagName().equals("table") && searchElement.hasClass("item-drops"))
                    {
                        foundTable = searchElement;
                        break;
                    }

                    Elements tablesInside = searchElement.select("table.item-drops");
                    if (!tablesInside.isEmpty())
                    {
                        foundTable = tablesInside.first();
                        break;
                    }

                    searchElement = searchElement.nextElementSibling();
                    searchDepth++;
                }

                WikiItem[] tableRows = new WikiItem[0];
                if (foundTable != null)
                {
                    tableRows = getTableItemsFromElement(foundTable);
                }

                if (tableRows.length > 0 && !currDropTable.containsKey(tableHeaderText))
                {
                    currDropTable.put(tableHeaderText, tableRows);
                }
            }
        }

        if (!currDropTable.isEmpty())
        {
            currDropTableSection.setTable(currDropTable);
            dropTableSections.add(currDropTableSection);
        }

        // Fallback: try old wiki format
        if (dropTableSections.isEmpty())
        {
            Elements fallbackHeaders = doc.select("h2 span.mw-headline");
            if (!fallbackHeaders.isEmpty())
            {
                Elements dropTables = doc.select("h2 ~ table.item-drops");
                if (!dropTables.isEmpty())
                {
                    WikiItem[] tableRows = getTableItemsFromElement(dropTables.first());
                    if (tableRows.length > 0)
                    {
                        Map<String, WikiItem[]> table = new LinkedHashMap<>();
                        table.put("Drops", tableRows);
                        dropTableSections.add(new DropTableSection("Drops", table));
                    }
                }
            }
        }

        return dropTableSections.toArray(new DropTableSection[0]);
    }

    private static WikiItem[] getTableItemsFromElement(Element table)
    {
        List<WikiItem> wikiItems = new ArrayList<>();
        Elements dropTableRows = table.select("tbody tr");

        for (Element dropTableRow : dropTableRows)
        {
            String[] lootRow = new String[6];
            Elements dropTableCells = dropTableRow.select("td");
            int index = 1;

            for (Element dropTableCell : dropTableCells)
            {
                String cellContent = dropTableCell.text();
                Elements images = dropTableCell.select("img");

                if (!images.isEmpty())
                {
                    String imageSource = images.first().attr("src");
                    if (!imageSource.isEmpty())
                    {
                        lootRow[0] = BASE_URL + imageSource;
                    }
                }

                if (cellContent != null && !cellContent.isEmpty() && index < 6)
                {
                    cellContent = cellContent.replaceAll("\\[.*\\]", "");
                    lootRow[index] = cellContent;
                    index++;
                }
            }

            if (lootRow[0] != null)
            {
                WikiItem wikiItem = parseRow(lootRow);
                wikiItems.add(wikiItem);
            }
        }

        return wikiItems.toArray(new WikiItem[0]);
    }

    static WikiItem parseRow(String[] row)
    {
        String imageUrl = "";
        String name = "";
        double rarity = -1;
        String rarityStr = "";
        int quantity = 0;
        String quantityStr = "";
        int exchangePrice = -1;
        int alchemyPrice = -1;

        if (row.length > 4)
        {
            imageUrl = row[0];
            name = row[1];
            if (name.endsWith("(m)"))
            {
                name = name.substring(0, name.length() - 3).trim();
            }

            NumberFormat nf = NumberFormat.getNumberInstance();

            quantityStr = row[2] != null ? row[2].replaceAll("–", "-").trim() : "";
            try
            {
                String[] quantityStrs = quantityStr.replaceAll("\\s+", "").split("-");
                String firstQuantityStr = quantityStrs.length > 0 ? quantityStrs[0] : null;
                if (firstQuantityStr != null)
                {
                    quantity = nf.parse(firstQuantityStr).intValue();
                }
            }
            catch (ParseException ignored) {}

            rarityStr = row[3] != null ? row[3] : "";
            if (rarityStr.startsWith("~"))
            {
                rarityStr = rarityStr.substring(1);
            }
            else if (rarityStr.startsWith("2 × ") || rarityStr.startsWith("3 × "))
            {
                rarityStr = rarityStr.substring(4);
            }

            try
            {
                String[] rarityStrs = rarityStr.replaceAll("\\s+", "").split(";");
                String firstRarityStr = rarityStrs.length > 0 ? rarityStrs[0] : null;

                if (firstRarityStr != null)
                {
                    if (firstRarityStr.equals("Always"))
                    {
                        rarity = 1.0;
                    }
                    else
                    {
                        String[] fraction = firstRarityStr.split("/");
                        if (fraction.length > 1)
                        {
                            double numer = nf.parse(fraction[0]).doubleValue();
                            double denom = nf.parse(fraction[1]).doubleValue();
                            rarity = numer / denom;
                        }
                    }
                }
            }
            catch (ParseException ignored) {}

            try
            {
                if (row[4] != null) exchangePrice = nf.parse(row[4]).intValue();
            }
            catch (ParseException ignored) {}

            try
            {
                if (row[5] != null) alchemyPrice = nf.parse(row[5]).intValue();
            }
            catch (ParseException ignored) {}
        }

        return new WikiItem(imageUrl, name, quantity, quantityStr, rarityStr, rarity, exchangePrice, alchemyPrice);
    }

    public static String getWikiUrl(String monsterName)
    {
        String sanitized = sanitizeName(monsterName);
        return BASE_WIKI_URL + sanitized;
    }

    static String sanitizeName(String name)
    {
        if (name.equalsIgnoreCase("tzhaar-mej"))
        {
            name = "tzhaar-mej (monster)";
        }
        if (name.equalsIgnoreCase("dusk") || name.equalsIgnoreCase("dawn"))
        {
            name = "grotesque guardians";
        }
        name = name.trim().toLowerCase().replaceAll("\\s+", "_");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private static boolean isDropsHeaderForEdgeCases(String monsterName, String tableHeaderText)
    {
        String monsterNameLC = monsterName.toLowerCase();
        String tableHeaderTextLower = tableHeaderText.toLowerCase();
        return (monsterNameLC.equals("cyclops") && (tableHeaderTextLower.contains("warriors' guild")
                || tableHeaderText.equals("Ardougne Zoo")))
                || (monsterNameLC.equals("vampyre juvinate")
                        && tableHeaderTextLower.equals("returning a juvinate to human"));
    }

    private static boolean parseH3PrimaryForEdgeCases(String monsterName)
    {
        return monsterName.toLowerCase().equals("cyclops");
    }

    private static CompletableFuture<String> requestAsync(OkHttpClient okHttpClient, String url)
    {
        CompletableFuture<String> future = new CompletableFuture<>();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException ex)
            {
                log.error("HTTP call failed for URL '{}': {}", url, ex.getMessage(), ex);
                future.completeExceptionally(ex);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                try (ResponseBody responseBody = response.body())
                {
                    if (!response.isSuccessful() || responseBody == null)
                    {
                        log.warn("HTTP request unsuccessful. Status code: {} for URL: {}", response.code(), url);
                        future.complete("");
                        return;
                    }
                    future.complete(responseBody.string());
                }
            }
        });

        return future;
    }
}
