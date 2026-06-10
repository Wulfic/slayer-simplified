/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Fetches drop-table data and combat stats from the OSRS Wiki raw wikitext.
 */
package com.slayersimplified.loot;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.*;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class WikiScraper
{
    private static final String BASE_WIKI_URL = "https://oldschool.runescape.wiki/w/";
    private static final String USER_AGENT = RuneLite.USER_AGENT + " (slayer-simplified)";

    private static final Map<String, CombatStats> combatStatsCache = new ConcurrentHashMap<>();

    public static CompletableFuture<DropTableSection[]> getDropsByMonster(
            OkHttpClient okHttpClient, String monsterName)
    {
        CompletableFuture<DropTableSection[]> future = new CompletableFuture<>();

        log.debug("Looking up drops for monster: '{}'", monsterName);

        requestWikitext(okHttpClient, monsterName).whenCompleteAsync((rawWikitext, ex) ->
        {
            if (ex != null)
            {
                log.error("HTTP request failed for monster '{}': {}", monsterName, ex.getMessage(), ex);
                future.complete(new DropTableSection[0]);
                return;
            }

            try
            {
                DropTableSection[] result = parseDropTables(rawWikitext, monsterName);
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

    public static CompletableFuture<CombatStats> getCombatStats(
            OkHttpClient okHttpClient, String monsterName)
    {
        CombatStats cached = combatStatsCache.get(monsterName);
        if (cached != null)
        {
            log.debug("Combat stats cache hit for '{}'", monsterName);
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<CombatStats> future = new CompletableFuture<>();

        log.debug("Looking up combat stats for monster: '{}'", monsterName);

        requestWikitext(okHttpClient, monsterName).whenCompleteAsync((rawWikitext, ex) ->
        {
            if (ex != null)
            {
                log.error("HTTP request failed for combat stats '{}': {}", monsterName, ex.getMessage(), ex);
                future.complete(emptyCombatStats());
                return;
            }

            try
            {
                CombatStats stats = parseCombatStats(rawWikitext);
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

    static CombatStats parseCombatStats(String rawWikitext)
    {
        Map<String, String> params = parseInfoboxMonsterParams(rawWikitext);
        if (params.isEmpty())
        {
            log.warn("No Infobox Monster template found in wikitext");
            return emptyCombatStats();
        }

        String combatLevel = params.getOrDefault("combat", "");
        String hitpoints = params.getOrDefault("hitpoints", "");
        String maxHit = params.getOrDefault("max hit", "");
        String attackStyle = params.getOrDefault("attack style", "");
        String attribute = params.getOrDefault("attributes", params.getOrDefault("attribute", ""));

        String weaknessType = params.getOrDefault("elementalweaknesstype", "");
        if (weaknessType.toLowerCase().endsWith(" elemental weakness"))
        {
            weaknessType = weaknessType.substring(0, weaknessType.length() - " elemental weakness".length()).trim();
        }
        String weaknessPercent = params.getOrDefault("elementalweaknesspercent", "");

        String immunePoison = params.getOrDefault("immunepoison", "");
        String immuneVenom = params.getOrDefault("immunevenom", "");
        String immuneCannon = params.getOrDefault("immunecannon", "");
        String immuneThrall = params.getOrDefault("immunethrall", "");
        String immuneBurn = params.getOrDefault("immuneburn", "");

        return new CombatStats(
                combatLevel, hitpoints, maxHit, attackStyle, attribute,
                weaknessType, weaknessPercent,
                immunePoison, immuneVenom, immuneCannon, immuneThrall, immuneBurn);
    }

    static DropTableSection[] parseDropTables(String rawWikitext, String monsterName)
    {
        String monsterLC = monsterName.toLowerCase();
        List<DropTableSection> sections = new ArrayList<>();

        boolean inDropSection = false;
        boolean h3IsPrimary = false;
        String sectionHeader = "";
        String subsectionHeader = "";
        Map<String, WikiItem[]> currentTable = new LinkedHashMap<>();
        List<WikiItem> pendingItems = new ArrayList<>();
        // The most recent non-primary H3. If an H4 follows it, that H3 was a *group* of
        // category sub-tables (not a category itself) and is promoted to a section header,
        // so repeated H4 names across groups don't collide in one table. See parseDropTables.
        String pendingH3Group = null;

        for (String rawLine : rawWikitext.split("\n"))
        {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            int level = getHeaderLevel(line);
            if (level == 2)
            {
                String header = extractHeaderText(line, 2);
                if (isH2SkipEdgeCase(monsterLC, header)) continue;

                flushPending(pendingItems, subsectionHeader.isEmpty() ? sectionHeader : subsectionHeader, currentTable);
                pendingItems = new ArrayList<>();
                if (!currentTable.isEmpty())
                {
                    sections.add(new DropTableSection(sectionHeader, currentTable));
                    currentTable = new LinkedHashMap<>();
                }

                sectionHeader = header;
                subsectionHeader = "";
                pendingH3Group = null;
                String lc = header.toLowerCase();
                inDropSection = lc.contains("drop") || lc.contains("reward") || lc.contains("loot")
                        || isDropsHeaderForEdgeCases(monsterName, header);
                h3IsPrimary = lc.contains("loot") || parseH3PrimaryForEdgeCases(monsterName);
            }
            else if (level == 3)
            {
                String header = extractHeaderText(line, 3);
                if (isH3SkipEdgeCase(monsterLC, header)) continue;
                if (header.equalsIgnoreCase("Regular drops")) continue;

                if (inDropSection)
                {
                    flushPending(pendingItems, subsectionHeader.isEmpty() ? sectionHeader : subsectionHeader, currentTable);
                    pendingItems = new ArrayList<>();

                    if (h3IsPrimary)
                    {
                        if (!currentTable.isEmpty())
                        {
                            sections.add(new DropTableSection(sectionHeader, currentTable));
                            currentTable = new LinkedHashMap<>();
                        }
                        sectionHeader = header;
                        subsectionHeader = "";
                        pendingH3Group = null;
                        String lc = header.toLowerCase();
                        inDropSection = lc.contains("drop") || lc.contains("reward") || lc.contains("loot")
                                || isDropsHeaderForEdgeCases(monsterName, header);
                    }
                    else
                    {
                        subsectionHeader = header;
                        pendingH3Group = header;
                    }
                }
            }
            else if (level == 4)
            {
                String header = extractHeaderText(line, 4);
                if (inDropSection)
                {
                    flushPending(pendingItems, subsectionHeader.isEmpty() ? sectionHeader : subsectionHeader, currentTable);
                    pendingItems = new ArrayList<>();

                    // The first H4 under an H3 reveals that the H3 was a group of category
                    // sub-tables, not a category. Promote it to its own section so the H4
                    // category names (which repeat across groups) stay in separate tables.
                    // Only promote a *pure* grouping H3: if items were already flushed under
                    // the H3's own name it is a real category (e.g. an H3 with both direct
                    // drops and H4 sub-tables) and must stay put.
                    if (pendingH3Group != null)
                    {
                        if (!currentTable.containsKey(pendingH3Group))
                        {
                            if (!currentTable.isEmpty())
                            {
                                sections.add(new DropTableSection(sectionHeader, currentTable));
                                currentTable = new LinkedHashMap<>();
                            }
                            sectionHeader = pendingH3Group;
                        }
                        pendingH3Group = null;
                    }

                    subsectionHeader = header;
                }
            }
            else if (inDropSection && line.startsWith("{{DropsLine"))
            {
                WikiItem item = parseDropsLine(line);
                if (item != null)
                {
                    pendingItems.add(item);
                }
            }
        }

        flushPending(pendingItems, subsectionHeader.isEmpty() ? sectionHeader : subsectionHeader, currentTable);
        if (!currentTable.isEmpty())
        {
            sections.add(new DropTableSection(sectionHeader, currentTable));
        }

        return sections.toArray(new DropTableSection[0]);
    }

    private static void flushPending(List<WikiItem> items, String key, Map<String, WikiItem[]> table)
    {
        if (!items.isEmpty() && key != null && !key.isEmpty())
        {
            table.put(key, items.toArray(new WikiItem[0]));
        }
    }

    private static WikiItem parseDropsLine(String line)
    {
        int start = line.indexOf("{{DropsLine");
        int end = line.lastIndexOf("}}");
        if (start < 0 || end <= start) return null;

        String content = line.substring(start + 2, end);
        int firstPipe = content.indexOf('|');
        if (firstPipe < 0) return null;
        content = content.substring(firstPipe + 1);

        Map<String, String> params = parseTemplateParams(content);

        String name = params.getOrDefault("name", "").trim();
        if (name.isEmpty()) return null;
        if (name.endsWith("(m)")) name = name.substring(0, name.length() - 3).trim();

        String quantityStr = params.getOrDefault("quantity", "").replaceAll("–", "-").trim();
        int quantity = 0;
        NumberFormat nf = NumberFormat.getNumberInstance();
        try
        {
            String[] parts = quantityStr.replaceAll("\\s+", "").split("-");
            if (parts.length > 0 && !parts[0].isEmpty())
            {
                quantity = nf.parse(parts[0]).intValue();
            }
        }
        catch (ParseException ignored) {}

        String rarityStr = params.getOrDefault("rarity", "");
        if (rarityStr.startsWith("~"))
        {
            rarityStr = rarityStr.substring(1);
        }
        else if (rarityStr.startsWith("2 × ") || rarityStr.startsWith("3 × "))
        {
            rarityStr = rarityStr.substring(4);
        }

        double rarity = -1;
        try
        {
            String[] rarityParts = rarityStr.replaceAll("\\s+", "").split(";");
            String first = rarityParts.length > 0 ? rarityParts[0] : null;
            if (first != null)
            {
                if (first.equalsIgnoreCase("Always"))
                {
                    rarity = 1.0;
                }
                else
                {
                    String[] fraction = first.split("/");
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

        return new WikiItem("", name, quantity, quantityStr, rarityStr, rarity, -1, -1);
    }

    /**
     * Finds and parses the {{Infobox Monster|...}} template parameters from raw wikitext.
     */
    private static Map<String, String> parseInfoboxMonsterParams(String rawWikitext)
    {
        int start = rawWikitext.indexOf("{{Infobox Monster");
        if (start == -1) return Collections.emptyMap();

        int depth = 0;
        int end = -1;
        for (int i = start; i < rawWikitext.length() - 1; i++)
        {
            if (rawWikitext.charAt(i) == '{' && rawWikitext.charAt(i + 1) == '{')
            {
                depth++;
                i++;
            }
            else if (rawWikitext.charAt(i) == '}' && rawWikitext.charAt(i + 1) == '}')
            {
                depth--;
                if (depth == 0)
                {
                    end = i;
                    break;
                }
                i++;
            }
        }
        if (end == -1) return Collections.emptyMap();

        Map<String, String> params = new LinkedHashMap<>();
        for (String line : rawWikitext.substring(start + 2, end).split("\n"))
        {
            line = line.trim();
            if (!line.startsWith("|")) continue;
            line = line.substring(1);
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim().toLowerCase();
            String value = stripWikiLinks(line.substring(eq + 1).trim());
            if (!key.isEmpty())
            {
                params.put(key, value);
            }
        }

        return params;
    }

    private static Map<String, String> parseTemplateParams(String content)
    {
        Map<String, String> params = new LinkedHashMap<>();
        for (String part : splitOnPipe(content))
        {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq).trim().toLowerCase();
            String value = part.substring(eq + 1).trim();
            if (!key.isEmpty())
            {
                params.put(key, value);
            }
        }
        return params;
    }

    /**
     * Splits a template parameter string on {@code |} while respecting nested {{ }} depth.
     */
    private static List<String> splitOnPipe(String content)
    {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < content.length(); i++)
        {
            char c = content.charAt(i);
            if (c == '{' && i + 1 < content.length() && content.charAt(i + 1) == '{')
            {
                depth++;
                current.append(c);
                current.append(content.charAt(i + 1));
                i++;
            }
            else if (c == '}' && i + 1 < content.length() && content.charAt(i + 1) == '}')
            {
                if (depth > 0) depth--;
                current.append(c);
                current.append(content.charAt(i + 1));
                i++;
            }
            else if (c == '|' && depth == 0)
            {
                parts.add(current.toString());
                current.setLength(0);
            }
            else
            {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    /**
     * Strips wikilinks and simple templates from a value string.
     * {@code [[A|B]]} → B, {@code [[A]]} → A.
     */
    private static String stripWikiLinks(String text)
    {
        text = text.replaceAll("\\[\\[[^\\[\\]|]+\\|([^\\[\\]]+)\\]\\]", "$1");
        text = text.replaceAll("\\[\\[([^\\[\\]]+)\\]\\]", "$1");
        text = text.replaceAll("<[^>]+>", "");
        text = text.replaceAll("\\{\\{[^}]+\\}\\}", "");
        return text.trim();
    }

    /**
     * Returns the MediaWiki heading level (2–6) for a line like {@code == Header ==},
     * or 0 if the line is not a heading.
     */
    private static int getHeaderLevel(String line)
    {
        if (!line.startsWith("==")) return 0;
        int level = 0;
        while (level < line.length() && line.charAt(level) == '=') level++;
        if (level < 2 || level > 6) return 0;
        int trailing = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '='; i--) trailing++;
        return trailing == level ? level : 0;
    }

    private static String extractHeaderText(String line, int level)
    {
        return line.substring(level, line.length() - level).trim();
    }

    private static boolean isH2SkipEdgeCase(String monsterLC, String header)
    {
        return (monsterLC.equals("hespori") && header.equals("Main table"))
                || (monsterLC.equals("chaos elemental") && header.equals("Major drops"))
                || ((monsterLC.equals("cyclops") || monsterLC.equals("gorak")) && header.equals("Drops"));
    }

    private static boolean isH3SkipEdgeCase(String monsterLC, String header)
    {
        return monsterLC.equals("undead druid") && header.equals("Seeds");
    }

    private static boolean isDropsHeaderForEdgeCases(String monsterName, String header)
    {
        String monsterLC = monsterName.toLowerCase();
        String headerLC = header.toLowerCase();
        return (monsterLC.equals("cyclops") && (headerLC.contains("warriors' guild") || header.equals("Ardougne Zoo")))
                || (monsterLC.equals("vampyre juvinate") && headerLC.equals("returning a juvinate to human"));
    }

    private static boolean parseH3PrimaryForEdgeCases(String monsterName)
    {
        return monsterName.toLowerCase().equals("cyclops");
    }

    private static CombatStats emptyCombatStats()
    {
        return new CombatStats("", "", "", "", "", "", "", "", "", "", "", "");
    }

    public static String getWikiUrl(String monsterName)
    {
        return BASE_WIKI_URL + sanitizeName(monsterName);
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

    /**
     * Fetches a monster page's raw wikitext, transparently following a single MediaWiki
     * redirect. {@code ?action=raw} returns the {@code #REDIRECT [[Target]]} stub rather than
     * following it, so monsters whose canonical wiki title differs in casing from the task
     * name (e.g. "Acidic Bloodveld") would otherwise yield no data.
     */
    private static CompletableFuture<String> requestWikitext(OkHttpClient okHttpClient, String monsterName)
    {
        String url = getWikiUrl(monsterName) + "?action=raw";
        return requestAsync(okHttpClient, url).thenCompose(rawWikitext ->
        {
            String target = parseRedirectTarget(rawWikitext);
            if (target == null)
            {
                return CompletableFuture.completedFuture(rawWikitext);
            }
            log.debug("Following wiki redirect: '{}' -> '{}'", monsterName, target);
            String redirectUrl = BASE_WIKI_URL + wikiTitleToPath(target) + "?action=raw";
            return requestAsync(okHttpClient, redirectUrl);
        });
    }

    /**
     * Returns the target title of a {@code #REDIRECT [[Target]]} page, or {@code null} if the
     * wikitext is not a redirect. Handles section anchors ({@code [[A#b]]}) and piped links.
     */
    static String parseRedirectTarget(String wikitext)
    {
        if (wikitext == null) return null;
        String trimmed = wikitext.trim();
        if (!trimmed.toLowerCase().startsWith("#redirect")) return null;

        int open = trimmed.indexOf("[[");
        int close = open < 0 ? -1 : trimmed.indexOf("]]", open);
        if (open < 0 || close < 0) return null;

        String target = trimmed.substring(open + 2, close).trim();
        int hash = target.indexOf('#');
        if (hash >= 0) target = target.substring(0, hash).trim();
        int pipe = target.indexOf('|');
        if (pipe >= 0) target = target.substring(0, pipe).trim();
        return target.isEmpty() ? null : target;
    }

    /**
     * Converts a canonical wiki title (correct casing already) to a URL path segment.
     * Unlike {@link #sanitizeName} this preserves case; OkHttp percent-encodes the rest.
     */
    private static String wikiTitleToPath(String title)
    {
        return title.trim().replace(' ', '_');
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
