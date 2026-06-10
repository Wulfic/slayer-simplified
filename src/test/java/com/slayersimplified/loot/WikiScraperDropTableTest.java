/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.loot;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Regression tests for {@link WikiScraper#parseDropTables} covering the wiki page
 * structures that previously caused loot sections to render multiple times or to
 * silently overwrite one another after the jsoup → raw-wikitext refactor.
 */
public class WikiScraperDropTableTest
{
    /**
     * Pages that split drops into several {@code ==H2==} variants (by combat level, world
     * type, location, ...) repeat the same category names under each variant. Each variant
     * must become its own section so the renderer can label them; the categories themselves
     * must not be reported as duplicate top-level sections.
     */
    @Test
    public void multipleH2VariantsBecomeSeparateLabelledSections()
    {
        String wikitext = String.join("\n",
                "==Level 96 drops==",
                "===100%===",
                "{{DropsLine|name=Bones|quantity=1|rarity=Always}}",
                "===Weapons and armour===",
                "{{DropsLine|name=Rune sword|quantity=1|rarity=1/128}}",
                "==Level 146 drops==",
                "===100%===",
                "{{DropsLine|name=Bones|quantity=1|rarity=Always}}",
                "===Weapons and armour===",
                "{{DropsLine|name=Dragon sword|quantity=1|rarity=1/128}}",
                "==Gallery==",
                "irrelevant");

        DropTableSection[] sections = WikiScraper.parseDropTables(wikitext, "Araxyte");

        Assert.assertEquals(2, sections.length);
        Assert.assertEquals("Level 96 drops", sections[0].getHeader());
        Assert.assertEquals("Level 146 drops", sections[1].getHeader());
        assertItemNames(sections[0], "Weapons and armour", "Rune sword");
        assertItemNames(sections[1], "Weapons and armour", "Dragon sword");
        assertNoDuplicateRenderedHeaders(sections);
    }

    /**
     * Pages where a single {@code ==Drops==} contains {@code ===group===} headers, each
     * holding {@code ====category====} sub-tables (e.g. Abyssal demon, Dust devil). The H4
     * category names repeat across groups; without splitting on the group, the later group
     * would overwrite the earlier one in a single map. Each group must become its own section
     * and no items may be lost.
     */
    @Test
    public void h3GroupsWithH4CategoriesSplitWithoutDataLoss()
    {
        String wikitext = String.join("\n",
                "==Drops==",
                "===Standard===",
                "====100%====",
                "{{DropsLine|name=Bones|quantity=1|rarity=Always}}",
                "====Weapons and armour====",
                "{{DropsLine|name=Rune sword|quantity=1|rarity=1/128}}",
                "===Wilderness Slayer Cave===",
                "====100%====",
                "{{DropsLine|name=Bones|quantity=1|rarity=Always}}",
                "====Weapons and armour====",
                "{{DropsLine|name=Dragon sword|quantity=1|rarity=1/128}}");

        DropTableSection[] sections = WikiScraper.parseDropTables(wikitext, "Abyssal demon");

        Assert.assertEquals(2, sections.length);
        Assert.assertEquals("Standard", sections[0].getHeader());
        Assert.assertEquals("Wilderness Slayer Cave", sections[1].getHeader());
        // The shared "Weapons and armour" category must keep each group's distinct item.
        assertItemNames(sections[0], "Weapons and armour", "Rune sword");
        assertItemNames(sections[1], "Weapons and armour", "Dragon sword");
        assertNoDuplicateRenderedHeaders(sections);
    }

    /**
     * The common case: a single {@code ==Drops==} with {@code ===category===} sub-headers.
     * Behaviour must be unchanged — one section, categories in document order.
     */
    @Test
    public void singleDropSectionWithCategoriesIsUnchanged()
    {
        String wikitext = String.join("\n",
                "==Drops==",
                "===Weapons and armour===",
                "{{DropsLine|name=Rune sword|quantity=1|rarity=1/128}}",
                "===Runes===",
                "{{DropsLine|name=Fire rune|quantity=75|rarity=10/128}}",
                "==Changes==",
                "irrelevant");

        DropTableSection[] sections = WikiScraper.parseDropTables(wikitext, "Gargoyle");

        Assert.assertEquals(1, sections.length);
        Assert.assertArrayEquals(
                new String[]{"Weapons and armour", "Runes"},
                sections[0].getTable().keySet().toArray(new String[0]));
        assertNoDuplicateRenderedHeaders(sections);
    }

    /**
     * An H3 that has its own direct drops is a real category, even when an H4 appears later in
     * the same {@code ==Drops==} (e.g. Growler, where a skipped "Regular drops" wrapper is
     * followed by H4 sub-tables). Such an H3 must not be promoted to a variant section.
     */
    @Test
    public void h3WithOwnItemsIsNotPromotedToSection()
    {
        String wikitext = String.join("\n",
                "==Drops==",
                "===100%===",
                "{{DropsLine|name=Bones|quantity=1|rarity=Always}}",
                "===Godsword shard table===",
                "{{DropsLine|name=Godsword shard 1|quantity=1|rarity=1/64}}",
                "===Regular drops===",
                "====Food and ammunition====",
                "{{DropsLine|name=Shark|quantity=1|rarity=1/32}}",
                "====Other====",
                "{{DropsLine|name=Coins|quantity=100|rarity=1/16}}");

        DropTableSection[] sections = WikiScraper.parseDropTables(wikitext, "Growler");

        Assert.assertEquals(1, sections.length);
        Assert.assertArrayEquals(
                new String[]{"100%", "Godsword shard table", "Food and ammunition", "Other"},
                sections[0].getTable().keySet().toArray(new String[0]));
        assertNoDuplicateRenderedHeaders(sections);
    }

    @Test
    public void parseRedirectTargetHandlesCommonForms()
    {
        Assert.assertEquals("Acidic Bloodveld",
                WikiScraper.parseRedirectTarget("#REDIRECT [[Acidic Bloodveld]]"));
        Assert.assertEquals("Aberrant spectre",
                WikiScraper.parseRedirectTarget("#redirect [[Aberrant spectre#Drops]]"));
        Assert.assertEquals("Cave kraken",
                WikiScraper.parseRedirectTarget("#REDIRECT [[Cave kraken|krakens]]"));
        Assert.assertNull(WikiScraper.parseRedirectTarget("{{Infobox Monster|name=Goblin}}"));
        Assert.assertNull(WikiScraper.parseRedirectTarget(""));
        Assert.assertNull(WikiScraper.parseRedirectTarget(null));
    }

    // -- helpers -------------------------------------------------------------

    /** Asserts the given category in a section contains exactly the named items. */
    private static void assertItemNames(DropTableSection section, String category, String... expectedNames)
    {
        WikiItem[] items = section.getTable().get(category);
        Assert.assertNotNull("category '" + category + "' missing from section '"
                + section.getHeader() + "'", items);
        List<String> actual = new ArrayList<>();
        for (WikiItem item : items)
        {
            actual.add(item.getName());
        }
        Assert.assertEquals(List.of(expectedNames), actual);
    }

    /**
     * Reproduces the rendering rule in LootTab (variant header shown when there is more than
     * one section, then a category header per table entry) and asserts every rendered header
     * line — i.e. each (variant, category) pair — is unique. This is the property whose
     * violation was the original "sections displayed multiple times" bug.
     */
    private static void assertNoDuplicateRenderedHeaders(DropTableSection[] sections)
    {
        int renderable = 0;
        for (DropTableSection section : sections)
        {
            if (section.getTable() != null && !section.getTable().isEmpty())
            {
                renderable++;
            }
        }
        boolean showVariant = renderable > 1;

        Set<String> seen = new HashSet<>();
        for (DropTableSection section : sections)
        {
            if (section.getTable() == null || section.getTable().isEmpty())
            {
                continue;
            }
            String variant = showVariant ? section.getHeader() : "";
            for (String category : section.getTable().keySet())
            {
                String line = variant + " :: " + category;
                Assert.assertTrue("duplicate rendered header: " + line, seen.add(line));
            }
        }
    }
}
