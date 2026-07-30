/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.StreakFillerMaster;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.TaskServiceImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pins Mortimer's assignment list to the 29 tasks he actually gives out
 * (Wyrmscraig, 29 July 2026).
 *
 * <p>Tagging those 29 files was a mechanical edit across the task corpus, and a
 * mistake in it is <em>silent</em>: {@link SlayerMaster#fromTaskMasterName} returns
 * {@code null} for an unrecognised string and
 * {@code GroupedTaskList} simply drops the task from the panel. A missing tag is
 * equally invisible — the task just never appears under Mortimer. So the count and
 * the exact membership are asserted here rather than eyeballed.</p>
 *
 * <p>If Jagex changes Mortimer's list, update {@link #EXPECTED_MORTIMER_TASKS} —
 * do not relax the assertion.</p>
 */
public class MortimerTaskCoverageTest
{
    /**
     * The 29 task keys Mortimer assigns, per the OSRS Wiki "Mortimer" assignment
     * table. Keys are the {@code _index.json} keys, matched case-insensitively.
     */
    private static final Set<String> EXPECTED_MORTIMER_TASKS = new HashSet<>(Arrays.asList(
            "Crawling Hand",
            "Cave crawler",
            "Banshee",
            "Rockslug",
            "Cockatrice",
            "Pyrefiend",
            "Infernal Mage",
            "Bloodveld",
            "Gryphon",
            "Jelly",
            "Custodian stalker",
            "Turoth",
            "Warped creature",
            "Cave horror",
            "Aberrant spectre",
            "Basilisk",
            "Wyrm",
            "Dust devil",
            "Kurask",
            "Venator",
            "Gargoyle",
            "Aquanite",
            "Nechryael",
            "Drake",
            "Abyssal demon",
            "Dark beast",
            "Araxyte",
            "Smoke devil",
            "Hydra"));

    private static TaskServiceImpl newService()
    {
        return new TaskServiceImpl(
                new Gson(),
                "/data/tasks",
                "/data/non_slayer_tasks",
                "/data/boss_tasks",
                "/data/animal_tasks",
                "https://oldschool.runescape.wiki/w/",
                "/images/monsters/");
    }

    /** Sanity check on the expectation itself, so a bad edit here can't pass vacuously. */
    @Test
    public void expectationListHolds29Tasks()
    {
        Assert.assertEquals("Mortimer assigns 29 tasks", 29, EXPECTED_MORTIMER_TASKS.size());
    }

    @Test
    public void exactlyTheExpectedTasksAreTaggedMortimer()
    {
        Set<String> expectedLower = new TreeSet<>();
        for (String key : EXPECTED_MORTIMER_TASKS)
        {
            expectedLower.add(key.toLowerCase());
        }

        Set<String> actualLower = new TreeSet<>();
        for (Task task : newService().getAll())
        {
            if (task.masters == null)
            {
                continue;
            }
            if (Arrays.asList(task.masters).contains(SlayerMaster.MORTIMER.getDisplayName()))
            {
                actualLower.add(task.name.toLowerCase());
            }
        }

        Set<String> missing = new TreeSet<>(expectedLower);
        missing.removeAll(actualLower);

        Set<String> unexpected = new TreeSet<>(actualLower);
        unexpected.removeAll(expectedLower);

        Assert.assertTrue(
                "Task(s) expected to be assignable by Mortimer but not tagged \"Mortimer\": " + missing,
                missing.isEmpty());

        Assert.assertTrue(
                "Task(s) tagged \"Mortimer\" that he does not assign: " + unexpected,
                unexpected.isEmpty());

        Assert.assertEquals(
                "Mortimer must be tagged on exactly 29 tasks",
                29,
                actualLower.size());
    }

    /**
     * Every task Mortimer assigns must be loadable by its index key — a tag on a
     * file that never made it into {@code _index.json} would be dead data.
     */
    @Test
    public void everyMortimerTaskIsLoadable()
    {
        TaskServiceImpl service = newService();
        Set<String> unloadable = new TreeSet<>();

        for (String key : EXPECTED_MORTIMER_TASKS)
        {
            if (service.get(key) == null)
            {
                unloadable.add(key);
            }
        }

        Assert.assertTrue(
                "Mortimer task(s) missing from _index.json or failing to parse: " + unloadable,
                unloadable.isEmpty());
    }

    /**
     * Mortimer awards points through a randomly rolled "Mortifier" (5&ndash;40 by
     * task), not a fixed per-task value, so his {@code basePoints} must stay 0.
     * A non-zero constant would make
     * {@code SlayerStreakOptimizerService.getRecommendationReason()} quote a
     * points figure that does not exist in game.
     */
    @Test
    public void mortimerAwardsNoFixedBasePoints()
    {
        Assert.assertEquals(
                "Mortimer's basePoints must stay 0 - he rolls a Mortifier instead",
                0,
                SlayerMaster.MORTIMER.getBasePoints());
    }

    /** The task data spells him exactly the way the enum does. */
    @Test
    public void mortimerNameResolvesToTheEnum()
    {
        Assert.assertSame(
                SlayerMaster.MORTIMER,
                SlayerMaster.fromTaskMasterName("Mortimer"));
    }

    /**
     * Mortimer's tasks advance a <em>separate</em> completion counter, exactly like
     * Krystilia's, so neither can ever be a streak filler: fillers exist to push the
     * main streak toward its next 10/50/100/250/1,000 milestone, and a task that
     * does not count toward it is worse than useless — it silently stalls the plan
     * the optimizer is presenting.
     *
     * <p>Mortimer would also make {@code SlayerStreakOptimizerService.getRecommendationReason()}
     * print "(0 pts)" for a master who does award points, just not a fixed number
     * of them.</p>
     */
    @Test
    public void noStreakFillerMasterUsesASeparateStreakCounter()
    {
        for (StreakFillerMaster filler : StreakFillerMaster.values())
        {
            Assert.assertNotSame(
                    filler.name() + " must not map to Mortimer - his tasks track a separate"
                            + " streak counter and cannot advance a milestone",
                    SlayerMaster.MORTIMER,
                    filler.getMaster());

            Assert.assertNotSame(
                    filler.name() + " must not map to Krystilia - Wilderness tasks track a"
                            + " separate streak counter and cannot advance a milestone",
                    SlayerMaster.KRYSTILIA,
                    filler.getMaster());
        }
    }
}
