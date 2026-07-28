/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.loot;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Regression tests for {@link WikiScraper#parseCombatStats} and the wiki title handling,
 * covering the infobox shapes that made the Wiki Combat Stats section report
 * "No data found." for roughly a quarter of all tasks (issue #3).
 */
public class WikiScraperCombatStatsTest
{
    /**
     * The Black dragon infobox from the issue report: every stat that differs between the
     * level 227 and 247 forms is numbered, and only the shared ones are unnumbered. Reading
     * the unnumbered names alone found nothing at all.
     */
    @Test
    public void numberedInfoboxVersionsBecomeOneBlockEach()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|version1 = Level 227",
                "|version2 = Level 247",
                "|name = Black dragon",
                "|combat1 = 227",
                "|combat2 = 247",
                "|hitpoints1 = 190",
                "|hitpoints2 = 250",
                "|max hit1 = 21 ([[Melee]]), 50 ([[Dragonfire]])",
                "|max hit2 = 22 ([[Melee]]), 50+ ([[Dragonfire]])",
                "|attack style = [[Slash]], [[Dragonfire]]",
                "|attributes = dragon,fiery",
                "}}");

        List<CombatStats.Variant> variants = WikiScraper.parseCombatStats(wikitext).getVariants();

        Assert.assertEquals(2, variants.size());

        CombatStats.Variant first = variants.get(0);
        Assert.assertEquals("Level 227", first.getName());
        Assert.assertEquals("227", first.getCombatLevel());
        Assert.assertEquals("190", first.getHitpoints());
        Assert.assertEquals("21 (Melee), 50 (Dragonfire)", first.getMaxHit());
        // Unnumbered parameters are shared by every version.
        Assert.assertEquals("Slash, Dragonfire", first.getAttackStyle());
        Assert.assertEquals("Dragon, Fiery", first.getAttribute());

        CombatStats.Variant second = variants.get(1);
        Assert.assertEquals("Level 247", second.getName());
        Assert.assertEquals("247", second.getCombatLevel());
        Assert.assertEquals("250", second.getHitpoints());
        Assert.assertEquals("Slash, Dragonfire", second.getAttackStyle());
    }

    /**
     * Most versioned pages version by NPC id or spawn location and repeat one set of stats
     * (Zombie has 13 such versions, Blue dragon 5). Those must collapse into a single
     * unlabelled block instead of being rendered once per version.
     */
    @Test
    public void versionsSharingStatsCollapseIntoOneUnlabelledBlock()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|version1 = 1",
                "|version2 = 2",
                "|version3 = 3",
                "|name = Blue dragon",
                "|combat = 111",
                "|hitpoints = 105",
                "|max hit = 10",
                "|attack style = [[Slash]]",
                "}}");

        List<CombatStats.Variant> variants = WikiScraper.parseCombatStats(wikitext).getVariants();

        Assert.assertEquals(1, variants.size());
        Assert.assertEquals("", variants.get(0).getName());
        Assert.assertEquals("111", variants.get(0).getCombatLevel());
    }

    /** Versions that share stats are labelled together when other versions differ. */
    @Test
    public void versionsSharingStatsAreLabelledTogether()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|version1 = Level 8 (1)",
                "|version2 = Level 8 (2)",
                "|version3 = Level 12",
                "|combat1 = 8",
                "|combat2 = 8",
                "|combat3 = 12",
                "|hitpoints = 16",
                "}}");

        List<CombatStats.Variant> variants = WikiScraper.parseCombatStats(wikitext).getVariants();

        Assert.assertEquals(2, variants.size());
        Assert.assertEquals("Level 8 (1), Level 8 (2)", variants.get(0).getName());
        Assert.assertEquals("8", variants.get(0).getCombatLevel());
        Assert.assertEquals("Level 12", variants.get(1).getName());
        Assert.assertEquals("12", variants.get(1).getCombatLevel());
    }

    /** A plain single-form infobox still yields exactly one unlabelled block. */
    @Test
    public void unversionedInfoboxYieldsOneUnlabelledBlock()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|name = Abyssal demon",
                "|combat = 124",
                "|hitpoints = 150",
                "|max hit = 8",
                "|attack style = [[Melee]]",
                "|elementalweaknesstype = water",
                "|elementalweaknesspercent = 40",
                "}}");

        List<CombatStats.Variant> variants = WikiScraper.parseCombatStats(wikitext).getVariants();

        Assert.assertEquals(1, variants.size());
        CombatStats.Variant variant = variants.get(0);
        Assert.assertEquals("", variant.getName());
        Assert.assertEquals("124", variant.getCombatLevel());
        Assert.assertEquals("Water", variant.getElementalWeakness());
        Assert.assertEquals("40", variant.getElementalWeaknessPercent());
    }

    /**
     * Poison and venom immunity are published as {@code poisonresistance}/
     * {@code venomresistance} percentages now; reading only the old {@code immunepoison}/
     * {@code immunevenom} flags left both rows blank on every current page.
     */
    @Test
    public void resistancePercentagesRenderAsImmunities()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|combat = 227",
                "|poisonresistance = 100",
                "|venomresistance = 0",
                "|immunecannon = No",
                "|immunethrall = Yes",
                "|immuneburn = Immune to weak burns",
                "}}");

        CombatStats.Variant variant = WikiScraper.parseCombatStats(wikitext).getVariants().get(0);

        Assert.assertEquals("Immune", variant.getImmunePoison());
        Assert.assertEquals("Not immune", variant.getImmuneVenom());
        Assert.assertEquals("Not immune", variant.getImmuneCannon());
        Assert.assertEquals("Immune", variant.getImmuneThrall());
        Assert.assertEquals("Immune to weak burns", variant.getImmuneBurn());
    }

    /** Venom resistance may name the downgraded damage instead of a percentage. */
    @Test
    public void venomDowngradedToPoisonIsReported()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|combat = 42",
                "|venomresistance = Poison",
                "}}");

        Assert.assertEquals("Poisoned instead",
                WikiScraper.parseCombatStats(wikitext).getVariants().get(0).getImmuneVenom());
    }

    /** Pages still carrying the old flags keep working. */
    @Test
    public void legacyImmunityFlagsStillParse()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|combat = 42",
                "|immunepoison = Yes",
                "|immunevenom = No",
                "}}");

        CombatStats.Variant variant = WikiScraper.parseCombatStats(wikitext).getVariants().get(0);

        Assert.assertEquals("Immune", variant.getImmunePoison());
        Assert.assertEquals("Not immune", variant.getImmuneVenom());
    }

    /** "None" is how the wiki says a monster has no elemental weakness — not a weakness. */
    @Test
    public void noElementalWeaknessIsNotRendered()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|combat = 42",
                "|elementalweaknesstype = None",
                "|elementalweaknesspercent = 0",
                "}}");

        CombatStats.Variant variant = WikiScraper.parseCombatStats(wikitext).getVariants().get(0);

        Assert.assertEquals("", variant.getElementalWeakness());
        Assert.assertEquals("", variant.getElementalWeaknessPercent());
    }

    /** Disambiguation and category pages have no monster infobox at all. */
    @Test
    public void pageWithoutMonsterInfoboxYieldsNoStats()
    {
        String wikitext = String.join("\n",
                "'''Archer''' may refer to:",
                "*[[Archer (Ardougne)]]",
                "{{Disambig}}");

        Assert.assertTrue(WikiScraper.parseCombatStats(wikitext).isEmpty());
    }

    /** An infobox that carries no stats at all is not worth an empty block. */
    @Test
    public void infoboxWithoutStatsYieldsNoStats()
    {
        String wikitext = String.join("\n",
                "{{Infobox Monster",
                "|name = Some NPC",
                "|examine = Nothing to fight here.",
                "}}");

        Assert.assertTrue(WikiScraper.parseCombatStats(wikitext).isEmpty());
    }

    /**
     * MediaWiki titles are case-sensitive past the first character, so a task name must
     * reach the wiki with its own capitalisation intact.
     */
    @Test
    public void sanitizeNameKeepsTitleCasing()
    {
        Assert.assertEquals("Elite_Dark_Ranger", WikiScraper.sanitizeName("Elite Dark Ranger"));
        Assert.assertEquals("Kebbit_(Eagles'_Peak)", WikiScraper.sanitizeName("Kebbit (Eagles' Peak)"));
        Assert.assertEquals("Black_dragon", WikiScraper.sanitizeName("Black dragon"));
        Assert.assertEquals("Aberrant_spectre", WikiScraper.sanitizeName("aberrant spectre"));
        Assert.assertEquals("Grotesque_Guardians", WikiScraper.sanitizeName("Dusk"));
    }

    /**
     * A name that is not a page title is retried without its qualifier and then with the
     * page's likelier casing, so mis-titled task and variant names still resolve.
     */
    @Test
    public void candidateTitlesCoverQualifierAndCasingDrift()
    {
        Assert.assertEquals(
                List.of("Blood_Blamish_Snail_(Round)", "Blood_Blamish_Snail", "Blood_blamish_snail_(round)"),
                WikiScraper.candidateTitles("Blood Blamish Snail (Round)"));
        Assert.assertEquals(
                List.of("Marble_Gargoyle", "Marble_gargoyle"),
                WikiScraper.candidateTitles("Marble Gargoyle"));
        // Nothing to vary: one request, as before.
        Assert.assertEquals(List.of("Black_dragon"), WikiScraper.candidateTitles("Black dragon"));
    }

    @Test
    public void stripQualifierRemovesTrailingParenthetical()
    {
        Assert.assertEquals("Paladin", WikiScraper.stripQualifier("Paladin (1)"));
        Assert.assertEquals("Ram", WikiScraper.stripQualifier("Ram (Sheared)"));
        Assert.assertEquals("Blood Blamish Snail",
                WikiScraper.stripQualifier("Blood Blamish Snail (Round)"));
        Assert.assertNull(WikiScraper.stripQualifier("Black dragon"));
        Assert.assertNull(WikiScraper.stripQualifier("(Round)"));
    }
}
