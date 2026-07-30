/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.TaskServiceImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Pins the task-data side of the Wyrmscraig / Mortimer update (29 July 2026) and
 * of the venator, which shipped with <em>The Blood Moon Rises</em> on 30 June 2026
 * and was missed at the time.
 *
 * <p>Every assertion here corresponds to something a player sees in the panel and
 * that nothing else in the suite would catch: a dropped spawn just makes the
 * Locations tab one row shorter, and a missing master just removes the task from
 * one group. Both are invisible without a fixed expectation.</p>
 */
public class WyrmscraigUpdateDataTest
{
    private final TaskServiceImpl service = new TaskServiceImpl(
            new Gson(),
            "/data/tasks",
            "/data/non_slayer_tasks",
            "/data/boss_tasks",
            "/data/animal_tasks",
            "https://oldschool.runescape.wiki/w/",
            "/images/monsters/");

    private static final List<String> VAMPYRIUM_HUNTING_GROUNDS =
            Arrays.asList("Apsul Hunting Ground", "Virer Hunting Ground");

    private static final String VENATOR = "Venator --lvl 246";
    private static final String BLOOD_STARVED_VENATOR = "Blood-starved venator --lvl 246";

    private Task task(String key)
    {
        Task task = service.get(key);
        Assert.assertNotNull("Task '" + key + "' must load from _index.json", task);
        return task;
    }

    private void assertLocations(Task task, String variant, String... expected)
    {
        Assert.assertNotNull(task.name + " must have variantLocations", task.variantLocations);
        String[] actual = task.variantLocations.get(variant);
        Assert.assertNotNull(
                task.name + " has no variantLocations entry for '" + variant + "'", actual);
        Assert.assertEquals(
                task.name + " / " + variant + " locations",
                Arrays.asList(expected),
                Arrays.asList(actual));
    }

    // -------------------------------------------------------------------------
    // Venator
    // -------------------------------------------------------------------------

    /** Both venator forms share the two Vampyrium hunting grounds. */
    @Test
    public void venatorSpawnsInBothVampyriumHuntingGrounds()
    {
        Task venator = task("Venator");

        Assert.assertEquals("Venator requires 74 Slayer", 74, venator.levelRequired);
        Assert.assertEquals(
                "Venator variants",
                Arrays.asList(VENATOR, BLOOD_STARVED_VENATOR),
                Arrays.asList(venator.variants));

        for (String variant : new String[]{VENATOR, BLOOD_STARVED_VENATOR})
        {
            assertLocations(venator, variant,
                    VAMPYRIUM_HUNTING_GROUNDS.get(0), VAMPYRIUM_HUNTING_GROUNDS.get(1));
        }
    }

    /**
     * The venators appear twice in the corpus: as the standalone "Venator" task
     * and as two variants of the "Vampyre" category task. Nothing in the loader
     * links the two, so an edit to one silently drifts from the other and the
     * same monster ends up with two different location lists depending on which
     * task the player opened.
     */
    @Test
    public void venatorVariantsAgreeBetweenTheVenatorAndVampyreTasks()
    {
        Task venator = task("Venator");
        Task vampyre = task("Vampyre");

        for (String variant : new String[]{VENATOR, BLOOD_STARVED_VENATOR})
        {
            Assert.assertTrue(
                    "Vampyre must still list '" + variant + "' as a variant",
                    Arrays.asList(vampyre.variants).contains(variant));

            Assert.assertEquals(
                    "'" + variant + "' locations must match between the Venator and Vampyre tasks",
                    Arrays.asList(venator.variantLocations.get(variant)),
                    Arrays.asList(vampyre.variantLocations.get(variant)));
        }
    }

    /**
     * Vampyres are assignable by every master from Mazchna up, not just the two
     * the data used to claim — Chaeldar, Konar, Nieve and Duradel were all
     * missing, so a Vampyres task from any of them fell out of the panel.
     *
     * <p>Mortimer is deliberately absent: he assigns Venator directly rather
     * than the Vampyres category, which is why "Venator" is its own task.</p>
     */
    @Test
    public void vampyreTaskListsEveryMasterThatAssignsIt()
    {
        Task vampyre = task("Vampyre");

        Assert.assertEquals(
                "Vampyre masters",
                Arrays.asList("Mazchna", "Vannaka", "Chaeldar", "Konar quo Maten", "Nieve", "Duradel"),
                Arrays.asList(vampyre.masters));

        Assert.assertFalse(
                "Mortimer assigns Venator directly, not the Vampyres category",
                Arrays.asList(vampyre.masters).contains(SlayerMaster.MORTIMER.getDisplayName()));
    }

    // -------------------------------------------------------------------------
    // Wyrmscraig spawns
    // -------------------------------------------------------------------------

    /**
     * Wyrmscraig Cavern is an additional wyrm location, not a replacement for
     * Karuulm — a player on a Wyrm task needs to see both, and the two
     * strykewyrm forms only gained the cavern for their lower-level variant.
     */
    @Test
    public void wyrmTaskListsWyrmscraigAlongsideItsExistingLocations()
    {
        Task wyrm = task("Wyrm");

        assertLocations(wyrm, "Wyrm --lvl 97", "Karuulm Slayer Dungeon", "Wyrmscraig Cavern");
        assertLocations(wyrm, "Wyrmling --lvl 55", "Neypotzli", "Wyrmscraig");
        assertLocations(wyrm, "Lava Strykewyrm --lvl 116", "Charred Dungeon", "Wyrmscraig Cavern");

        // Unchanged by the update — asserted so a future bulk edit cannot quietly
        // spread Wyrmscraig onto forms that do not spawn there.
        assertLocations(wyrm, "Shadow Wyrm --lvl 267", "Karuulm Slayer Dungeon");
        assertLocations(wyrm, "Magma Strykewyrm --lvl 249", "Charred Dungeon");
    }

    /**
     * The non-Slayer Wyrmscraig spawns added alongside the Slayer ones. Bats and
     * mountain trolls share the cavern; yaks and bunnies are on the surface.
     */
    @Test
    public void nonSlayerWyrmscraigSpawnsArePresent()
    {
        assertLocations(task("Bat"), "Bat --lvl 6",
                "Slayer Tower", "Wyrmscraig Cavern");
        assertLocations(task("Troll"), "Mountain troll --lvl 69 --lvl 71",
                "Death Plateau", "Trollheim - South", "Wyrmscraig Cavern");
        assertLocations(task("Yak"), "Yak --lvl 22", "Neitiznot", "Wyrmscraig");
        assertLocations(task("Bunny"), "Bunny --lvl 2", "Rellekka", "Wyrmscraig");
    }
}
