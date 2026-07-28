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

    /**
     * Raw wikitext per monster name, shared by the Loot and Info tabs so opening a task
     * costs one wiki request instead of one per tab. Failed and empty responses are
     * evicted (see {@link #requestWikitext}) so a rate-limited or offline lookup retries
     * the next time the task is opened instead of showing an empty panel all session.
     */
    private static final Map<String, CompletableFuture<String>> wikitextCache = new ConcurrentHashMap<>();

    /** Above this many cached pages the cache is dropped wholesale; keeps memory bounded. */
    private static final int MAX_CACHED_PAGES = 128;

    /** Upper bound on variant pages fetched for a task whose own page carries no data. */
    private static final int MAX_FALLBACK_VARIANTS = 8;

    /** Infobox parameters that may carry a {@code 1..N} version suffix and that we render. */
    private static final String[] VERSIONED_KEYS = {
            "combat", "hitpoints", "max hit", "attack style", "attributes", "attribute",
            "elementalweaknesstype", "elementalweaknesspercent",
            "immunepoison", "immunevenom", "immunecannon", "immunethrall", "immuneburn",
            "poisonresistance", "venomresistance",
    };

    public static CompletableFuture<DropTableSection[]> getDropsByMonster(
            OkHttpClient okHttpClient, String monsterName)
    {
        return getDropsByMonster(okHttpClient, monsterName, Collections.emptyList());
    }

    /**
     * Fetches the drop tables for {@code monsterName}.
     *
     * @param fallbackNames monsters to fall back to when the task's own wiki page has no drop
     *                      tables — task pages such as "Kalphite" or "Troll" are category
     *                      pages whose drops live on the individual monsters' pages instead.
     */
    public static CompletableFuture<DropTableSection[]> getDropsByMonster(
            OkHttpClient okHttpClient, String monsterName, List<String> fallbackNames)
    {
        log.debug("Looking up drops for monster: '{}'", monsterName);

        return requestWikitext(okHttpClient, monsterName)
                .thenApply(rawWikitext -> parseDropTablesSafely(rawWikitext, monsterName))
                .thenCompose(sections ->
                {
                    if (sections.length > 0 || fallbackNames.isEmpty())
                    {
                        return CompletableFuture.completedFuture(sections);
                    }
                    log.debug("No drop tables on '{}' page; falling back to its variants", monsterName);
                    return dropsForVariants(okHttpClient, fallbackNames);
                })
                .exceptionally(ex ->
                {
                    log.error("Drop table lookup failed for '{}': {}", monsterName, ex.getMessage(), ex);
                    return new DropTableSection[0];
                });
    }

    public static CompletableFuture<CombatStats> getCombatStats(
            OkHttpClient okHttpClient, String monsterName)
    {
        return getCombatStats(okHttpClient, monsterName, Collections.emptyList());
    }

    /**
     * Fetches the infobox combat stats for {@code monsterName}.
     *
     * @param fallbackNames monsters to fall back to when the task's own wiki page carries no
     *                      monster infobox (category and disambiguation pages)
     */
    public static CompletableFuture<CombatStats> getCombatStats(
            OkHttpClient okHttpClient, String monsterName, List<String> fallbackNames)
    {
        log.debug("Looking up combat stats for monster: '{}'", monsterName);

        return requestWikitext(okHttpClient, monsterName)
                .thenApply(rawWikitext -> parseCombatStatsSafely(rawWikitext, monsterName))
                .thenCompose(stats ->
                {
                    if (!stats.isEmpty() || fallbackNames.isEmpty())
                    {
                        return CompletableFuture.completedFuture(stats);
                    }
                    log.debug("No infobox stats on '{}' page; falling back to its variants", monsterName);
                    return combatStatsForVariants(okHttpClient, fallbackNames);
                })
                .exceptionally(ex ->
                {
                    log.error("Combat stat lookup failed for '{}': {}", monsterName, ex.getMessage(), ex);
                    return CombatStats.empty();
                });
    }

    /** Drops the shared wikitext cache; called when the plugin shuts down. */
    public static void clearCache()
    {
        wikitextCache.clear();
    }

    // -- Variant fallback ----------------------------------------------------

    private static CompletableFuture<DropTableSection[]> dropsForVariants(
            OkHttpClient okHttpClient, List<String> variantNames)
    {
        List<String> names = limitVariants(variantNames);
        List<CompletableFuture<DropTableSection[]>> futures = new ArrayList<>();

        for (String name : names)
        {
            futures.add(requestWikitext(okHttpClient, name)
                    .thenApply(text -> relabelSections(parseDropTablesSafely(text, name), name))
                    .exceptionally(ex ->
                    {
                        log.warn("Variant drop lookup failed for '{}': {}", name, ex.getMessage());
                        return new DropTableSection[0];
                    }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored ->
                {
                    List<DropTableSection> all = new ArrayList<>();
                    for (CompletableFuture<DropTableSection[]> future : futures)
                    {
                        all.addAll(Arrays.asList(future.join()));
                    }
                    return all.toArray(new DropTableSection[0]);
                });
    }

    private static CompletableFuture<CombatStats> combatStatsForVariants(
            OkHttpClient okHttpClient, List<String> variantNames)
    {
        List<String> names = limitVariants(variantNames);
        List<CompletableFuture<CombatStats>> futures = new ArrayList<>();

        for (String name : names)
        {
            futures.add(requestWikitext(okHttpClient, name)
                    .thenApply(text -> nameVariantsAfter(parseCombatStatsSafely(text, name), name))
                    .exceptionally(ex ->
                    {
                        log.warn("Variant combat stat lookup failed for '{}': {}", name, ex.getMessage());
                        return CombatStats.empty();
                    }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored ->
                {
                    List<CombatStats.Variant> all = new ArrayList<>();
                    for (CompletableFuture<CombatStats> future : futures)
                    {
                        all.addAll(future.join().getVariants());
                    }
                    return new CombatStats(all);
                });
    }

    private static List<String> limitVariants(List<String> variantNames)
    {
        return variantNames.size() > MAX_FALLBACK_VARIANTS
                ? variantNames.subList(0, MAX_FALLBACK_VARIANTS)
                : variantNames;
    }

    /**
     * Re-labels a variant monster's sections with that monster's name, so the aggregated
     * table shows which monster each block of drops belongs to.
     */
    private static DropTableSection[] relabelSections(DropTableSection[] sections, String monsterName)
    {
        for (DropTableSection section : sections)
        {
            String header = section.getHeader();
            section.setHeader(sections.length > 1 && header != null && !header.isEmpty()
                    ? monsterName + " — " + header
                    : monsterName);
        }
        return sections;
    }

    /** Prefixes each stat block with the monster it came from (variant fallback only). */
    private static CombatStats nameVariantsAfter(CombatStats stats, String monsterName)
    {
        List<CombatStats.Variant> named = new ArrayList<>();
        for (CombatStats.Variant variant : stats.getVariants())
        {
            named.add(variant.withName(variant.getName().isEmpty()
                    ? monsterName
                    : monsterName + " (" + variant.getName() + ")"));
        }
        return new CombatStats(named);
    }

    // -- Parsing -------------------------------------------------------------

    private static DropTableSection[] parseDropTablesSafely(String rawWikitext, String monsterName)
    {
        try
        {
            DropTableSection[] result = parseDropTables(rawWikitext, monsterName);
            if (result.length == 0)
            {
                log.debug("No drop tables found for monster '{}'", monsterName);
            }
            else
            {
                log.debug("Found {} drop table section(s) for monster '{}'", result.length, monsterName);
            }
            return result;
        }
        catch (Exception e)
        {
            log.error("Error parsing drop tables for '{}'", monsterName, e);
            return new DropTableSection[0];
        }
    }

    private static CombatStats parseCombatStatsSafely(String rawWikitext, String monsterName)
    {
        try
        {
            CombatStats stats = parseCombatStats(rawWikitext);
            log.debug("Parsed {} combat stat block(s) for '{}'", stats.getVariants().size(), monsterName);
            return stats;
        }
        catch (Exception e)
        {
            log.error("Error parsing combat stats for '{}'", monsterName, e);
            return CombatStats.empty();
        }
    }

    /**
     * Parses the {@code {{Infobox Monster}}} stat blocks out of raw wikitext.
     * <p>
     * Infoboxes for monsters that exist in several forms carry one numbered set of
     * parameters per form ({@code combat1}/{@code combat2}, ...) with the shared values left
     * unnumbered; reading only the unnumbered names — as this did before — returned nothing
     * at all for those pages (see issue #3). Every numbered form is read here, falling back
     * to the unnumbered value per parameter, and forms whose stats are identical are
     * collapsed so pages that version by NPC id or location render as one block.
     */
    static CombatStats parseCombatStats(String rawWikitext)
    {
        Map<String, String> params = parseInfoboxMonsterParams(rawWikitext);
        if (params.isEmpty())
        {
            log.debug("No Infobox Monster template found in wikitext");
            return CombatStats.empty();
        }

        List<Integer> versions = versionIndexes(params);
        if (versions.isEmpty())
        {
            CombatStats.Variant variant = buildVariant("", params, -1);
            return variant.hasNoStats()
                    ? CombatStats.empty()
                    : new CombatStats(Collections.singletonList(variant));
        }

        Map<String, CombatStats.Variant> byStats = new LinkedHashMap<>();
        Map<String, List<String>> labelsByStats = new LinkedHashMap<>();

        for (int index : versions)
        {
            String label = params.getOrDefault("version" + index, "").trim();
            CombatStats.Variant variant = buildVariant(
                    label.isEmpty() ? "Version " + index : label, params, index);
            if (variant.hasNoStats())
            {
                continue;
            }
            String key = variant.statsKey();
            byStats.putIfAbsent(key, variant);
            labelsByStats.computeIfAbsent(key, k -> new ArrayList<>()).add(variant.getName());
        }

        boolean singleBlock = byStats.size() <= 1;
        List<CombatStats.Variant> variants = new ArrayList<>();
        for (Map.Entry<String, CombatStats.Variant> entry : byStats.entrySet())
        {
            // One surviving block means every version shares its stats — the label adds nothing.
            variants.add(entry.getValue().withName(
                    singleBlock ? "" : joinLabels(labelsByStats.get(entry.getKey()))));
        }

        return new CombatStats(variants);
    }

    /**
     * Version numbers present on any rendered infobox parameter, in ascending order.
     * Derived from the parameters themselves rather than from {@code versionN} alone so a
     * page that numbers its stats without naming its versions is still picked up.
     */
    private static List<Integer> versionIndexes(Map<String, String> params)
    {
        Set<Integer> indexes = new TreeSet<>();
        for (String key : params.keySet())
        {
            int split = key.length();
            while (split > 0 && Character.isDigit(key.charAt(split - 1))) split--;
            if (split == key.length() || split == 0) continue;

            String base = key.substring(0, split);
            if (!base.equals("version") && !isVersionedKey(base)) continue;

            try
            {
                indexes.add(Integer.parseInt(key.substring(split)));
            }
            catch (NumberFormatException ignored)
            {
                // Suffix too long for an int — not a version number.
            }
        }
        return new ArrayList<>(indexes);
    }

    private static boolean isVersionedKey(String base)
    {
        for (String key : VERSIONED_KEYS)
        {
            if (key.equals(base)) return true;
        }
        return false;
    }

    /**
     * Builds one stat block. {@code index} selects the numbered parameter set, or -1 for a
     * page whose infobox has a single unnumbered set.
     */
    private static CombatStats.Variant buildVariant(String name, Map<String, String> params, int index)
    {
        String attribute = param(params, "attributes", index);
        if (attribute.isEmpty())
        {
            attribute = param(params, "attribute", index);
        }

        String weaknessType = param(params, "elementalweaknesstype", index);
        if (weaknessType.toLowerCase().endsWith(" elemental weakness"))
        {
            weaknessType = weaknessType.substring(0, weaknessType.length() - " elemental weakness".length()).trim();
        }
        if (weaknessType.equalsIgnoreCase("none"))
        {
            weaknessType = "";
        }

        return new CombatStats.Variant(
                name,
                param(params, "combat", index),
                param(params, "hitpoints", index),
                param(params, "max hit", index),
                param(params, "attack style", index),
                formatAttributes(attribute),
                capitalize(weaknessType),
                weaknessType.isEmpty() ? "" : param(params, "elementalweaknesspercent", index),
                immunity(params, "immunepoison", "poisonresistance", index),
                immunity(params, "immunevenom", "venomresistance", index),
                normalizeImmunity(param(params, "immunecannon", index)),
                normalizeImmunity(param(params, "immunethrall", index)),
                normalizeImmunity(param(params, "immuneburn", index)));
    }

    /** Numbered parameter for this version, falling back to the shared unnumbered one. */
    private static String param(Map<String, String> params, String key, int index)
    {
        if (index >= 0)
        {
            String versioned = params.get(key + index);
            if (versioned != null)
            {
                return versioned.trim();
            }
        }
        return params.getOrDefault(key, "").trim();
    }

    /**
     * Poison and venom immunity moved from the {@code immunepoison}/{@code immunevenom}
     * flags to numeric {@code poisonresistance}/{@code venomresistance} percentages, which
     * left both rows permanently blank. Read the flag when a page still has one, else
     * render the percentage.
     */
    private static String immunity(Map<String, String> params, String flagKey, String resistanceKey, int index)
    {
        String flag = param(params, flagKey, index);
        if (!flag.isEmpty())
        {
            return normalizeImmunity(flag);
        }

        String resistance = param(params, resistanceKey, index);
        if (resistance.isEmpty())
        {
            return "";
        }
        // Venom resistance may name the damage it is downgraded to rather than a percentage.
        if (resistance.equalsIgnoreCase("poison"))
        {
            return "Poisoned instead";
        }
        try
        {
            int percent = Integer.parseInt(resistance);
            if (percent <= 0) return "Not immune";
            if (percent >= 100) return "Immune";
            return percent + "% resistant";
        }
        catch (NumberFormatException e)
        {
            return capitalize(resistance);
        }
    }

    private static String normalizeImmunity(String value)
    {
        if (value.equalsIgnoreCase("yes")) return "Immune";
        if (value.equalsIgnoreCase("no")) return "Not immune";
        return capitalize(value);
    }

    /** {@code "dragon,fiery"} → {@code "Dragon, Fiery"}. */
    private static String formatAttributes(String value)
    {
        if (value.isEmpty())
        {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (String part : value.split(","))
        {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(capitalize(trimmed));
        }
        return out.toString();
    }

    private static String capitalize(String value)
    {
        return value.isEmpty()
                ? value
                : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    /** Joins the version labels sharing one stat block, keeping the header short. */
    private static String joinLabels(List<String> labels)
    {
        String joined = String.join(", ", labels);
        return joined.length() <= 34 ? joined : labels.get(0) + " +" + (labels.size() - 1) + " more";
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
                h3IsPrimary = lc.contains("loot");
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
            else if (line.startsWith("{{DropsTableHead"))
            {
                // The wiki's own marker that a drop table starts here. Sections that hold one
                // under a heading the name test misses ("Pickpocketing", "Hunter info") would
                // otherwise be dropped silently.
                inDropSection = true;
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
                || (monsterLC.equals("gorak") && header.equals("Drops"));
    }

    private static boolean isH3SkipEdgeCase(String monsterLC, String header)
    {
        return monsterLC.equals("undead druid") && header.equals("Seeds");
    }

    private static boolean isDropsHeaderForEdgeCases(String monsterName, String header)
    {
        // The Cyclops page has since been restructured into the ordinary
        // ==Drops== / ===location=== / ====category==== shape the general parser handles;
        // the special cases it used to need left it with no drop table at all.
        return monsterName.toLowerCase().equals("vampyre juvinate")
                && header.toLowerCase().equals("returning a juvinate to human");
    }

    public static String getWikiUrl(String monsterName)
    {
        return BASE_WIKI_URL + sanitizeName(monsterName);
    }

    /**
     * Converts a task/monster name into a wiki page title.
     * <p>
     * MediaWiki titles are case-sensitive after the first character, so the name's own
     * capitalisation must be kept: lower-casing it turned real titles into missing pages
     * ("Elite Dark Ranger" → "Elite dark ranger"), which showed up as empty Loot and Info
     * tabs for every such task (issue #3).
     */
    static String sanitizeName(String name)
    {
        if (name.equalsIgnoreCase("tzhaar-mej"))
        {
            name = "TzHaar-Mej (monster)";
        }
        if (name.equalsIgnoreCase("dusk") || name.equalsIgnoreCase("dawn"))
        {
            // Both halves of the fight share the Grotesque Guardians page, which is also
            // the only one of the three carrying the drop table.
            name = "Grotesque Guardians";
        }
        name = name.trim().replaceAll("\\s+", "_");
        return name.isEmpty()
                ? name
                : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    /**
     * Drops a trailing {@code (qualifier)} from a name, or returns {@code null} when there is
     * none. Some tasks are named with a qualifier the wiki does not use ("Paladin (1)",
     * "Ram (Sheared)", "Blood Blamish Snail (Round)"); retrying without it finds the page.
     */
    static String stripQualifier(String name)
    {
        String trimmed = name.trim();
        if (!trimmed.endsWith(")"))
        {
            return null;
        }
        int open = trimmed.lastIndexOf('(');
        if (open <= 0)
        {
            return null;
        }
        String stripped = trimmed.substring(0, open).trim();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * Raw wikitext for a monster, served from the shared session cache.
     * <p>
     * Both the Loot and Info tabs ask for the same page when a task is opened, and the wiki
     * rate-limits; caching the in-flight request collapses that into a single fetch. Failed
     * and empty lookups are evicted again so they are retried rather than leaving the tabs
     * blank for the rest of the session.
     */
    private static CompletableFuture<String> requestWikitext(OkHttpClient okHttpClient, String monsterName)
    {
        String key = monsterName.trim().toLowerCase(Locale.ROOT);

        if (wikitextCache.size() > MAX_CACHED_PAGES)
        {
            wikitextCache.clear();
        }

        CompletableFuture<String> request =
                wikitextCache.computeIfAbsent(key, k -> fetchWikitext(okHttpClient, monsterName));

        return request.whenComplete((wikitext, ex) ->
        {
            if (ex != null || wikitext == null || wikitext.isEmpty())
            {
                wikitextCache.remove(key, request);
            }
        });
    }

    /**
     * Fetches a monster page's raw wikitext, transparently following a single MediaWiki
     * redirect. {@code ?action=raw} returns the {@code #REDIRECT [[Target]]} stub rather than
     * following it, so monsters whose canonical wiki title differs from the task name (e.g.
     * "Acidic Bloodveld") would otherwise yield no data.
     */
    private static CompletableFuture<String> fetchWikitext(OkHttpClient okHttpClient, String monsterName)
    {
        List<String> titles = candidateTitles(monsterName);
        if (titles.isEmpty() || titles.get(0).isEmpty())
        {
            return CompletableFuture.completedFuture("");
        }
        return requestFirstExisting(okHttpClient, monsterName, titles, 0);
    }

    /**
     * Titles to try, in order, for a task name. The first is the name as written; the rest
     * cover the ways task names drift from wiki titles — a qualifier the wiki does not use
     * ("Paladin (1)"), or a name capitalised differently from the page ("Marble Gargoyle" vs
     * "Marble gargoyle"). Only a missing page costs the extra request.
     */
    static List<String> candidateTitles(String monsterName)
    {
        List<String> titles = new ArrayList<>();
        titles.add(sanitizeName(monsterName));

        String stripped = stripQualifier(monsterName);
        if (stripped != null)
        {
            addIfNew(titles, sanitizeName(stripped));
        }
        addIfNew(titles, lowerCaseTitle(monsterName));
        return titles;
    }

    private static void addIfNew(List<String> titles, String title)
    {
        if (!title.isEmpty() && !titles.contains(title))
        {
            titles.add(title);
        }
    }

    /** Title with everything but the leading character lower-cased. */
    private static String lowerCaseTitle(String monsterName)
    {
        String title = sanitizeName(monsterName);
        return title.isEmpty()
                ? title
                : title.substring(0, 1) + title.substring(1).toLowerCase(Locale.ROOT);
    }

    private static CompletableFuture<String> requestFirstExisting(
            OkHttpClient okHttpClient, String monsterName, List<String> titles, int index)
    {
        return requestTitle(okHttpClient, titles.get(index)).thenCompose(wikitext ->
        {
            if (!wikitext.isEmpty())
            {
                return followRedirect(okHttpClient, monsterName, wikitext);
            }
            if (index + 1 >= titles.size())
            {
                log.debug("No wiki page found for '{}' (tried {})", monsterName, titles);
                return CompletableFuture.completedFuture("");
            }
            return requestFirstExisting(okHttpClient, monsterName, titles, index + 1);
        });
    }

    private static CompletableFuture<String> followRedirect(
            OkHttpClient okHttpClient, String monsterName, String wikitext)
    {
        String target = parseRedirectTarget(wikitext);
        if (target == null)
        {
            return CompletableFuture.completedFuture(wikitext);
        }
        log.debug("Following wiki redirect: '{}' -> '{}'", monsterName, target);
        return requestTitle(okHttpClient, wikiTitleToPath(target));
    }

    private static CompletableFuture<String> requestTitle(OkHttpClient okHttpClient, String title)
    {
        return requestAsync(okHttpClient, BASE_WIKI_URL + title + "?action=raw");
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
                        // A 404 is an ordinary outcome here — the caller retries the lookup
                        // under a different title — so it is not worth a warning.
                        if (response.code() == 404)
                        {
                            log.debug("No wiki page at URL: {}", url);
                        }
                        else
                        {
                            log.warn("HTTP request unsuccessful. Status code: {} for URL: {}", response.code(), url);
                        }
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
