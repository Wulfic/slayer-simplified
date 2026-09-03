/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.google.gson.Gson;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.Task;
import com.slayersimplified.domain.TaskSearchResult;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Guards {@link TaskSearchResult#dedupeByDisplayName}.
 *
 * <p>The same monster legitimately appears in several task entries: a family
 * entry lists it as a variant, a group entry lists it as a member, and it may
 * own a dedicated boss entry too. Searching "Dagannoth Prime" therefore returned
 * three rows, all rendered as the identical string with the identical icon, and
 * the only difference — which page each one opened — was invisible until
 * clicked. 38 display names across the bundled corpus collide this way.</p>
 *
 * <p>The winner has to be picked by a total order, because {@code TaskServiceImpl}
 * holds tasks in a {@link java.util.HashMap}: any tie left unbroken would resolve
 * differently between JVM runs and make the panel behaviour unreproducible.</p>
 */
public class TaskSearchDedupeTest
{
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

    private static List<String> displayNames(TaskSearchResult[] results)
    {
        List<String> names = new ArrayList<>();
        for (TaskSearchResult r : results)
        {
            names.add(r.displayName);
        }
        return names;
    }

    /** The reported case: three indistinguishable rows collapse to one. */
    @Test
    public void dagannothPrimeYieldsASingleRow()
    {
        TaskServiceImpl service = newService();

        TaskSearchResult[] raw = service.searchWithVariants("Dagannoth Prime");
        Assert.assertEquals(
                "expected the family, group and boss entries to all match: " + displayNames(raw),
                3, raw.length);

        TaskSearchResult[] deduped = TaskSearchResult.dedupeByDisplayName(raw);
        Assert.assertEquals("one row per monster: " + displayNames(deduped), 1, deduped.length);
        Assert.assertEquals("Dagannoth Prime", deduped[0].displayName);
        Assert.assertEquals(
                "the dedicated boss page wins over the Dagannoth family and Kings group",
                "Dagannoth Prime", deduped[0].parentTask.name);
    }

    /**
     * No display name may survive twice, for any search term, across the whole
     * corpus. Searching each known display name individually is what the panel
     * actually does, so this exercises every colliding name rather than a sample.
     */
    @Test
    public void noSearchTermProducesADuplicateRow()
    {
        TaskServiceImpl service = newService();

        List<String> offenders = new ArrayList<>();
        for (String term : everyDisplayName(service))
        {
            TaskSearchResult[] deduped =
                    TaskSearchResult.dedupeByDisplayName(service.searchWithVariants(term));

            Map<String, Integer> counts = new LinkedHashMap<>();
            for (TaskSearchResult r : deduped)
            {
                counts.merge(r.displayName.toLowerCase(), 1, Integer::sum);
            }
            counts.forEach((name, count) ->
            {
                if (count > 1)
                {
                    offenders.add("\"" + term + "\" -> " + name + " x" + count);
                }
            });
        }

        Assert.assertEquals("duplicate rows survived dedup: " + offenders, 0, offenders.size());
    }

    /** Location search shares the collision, so it must share the fix. */
    @Test
    public void locationSearchAlsoCollapsesDuplicates()
    {
        TaskServiceImpl service = newService();

        TaskSearchResult[] raw = service.searchByLocation("Waterbirth Island Dungeon");
        long rawPrimes = displayNames(raw).stream().filter("Dagannoth Prime"::equals).count();
        Assert.assertEquals("the location is claimed by all three Dagannoth entries", 3, rawPrimes);

        TaskSearchResult[] deduped = TaskSearchResult.dedupeByDisplayName(raw);
        long dedupedPrimes = displayNames(deduped).stream().filter("Dagannoth Prime"::equals).count();
        Assert.assertEquals(1, dedupedPrimes);
    }

    /**
     * With no dedicated entry to prefer, the most specific parent wins: a
     * blood-starved venator belongs to the two-variant Venator entry, not to the
     * sprawling Vampyre family that also happens to list it.
     */
    @Test
    public void withoutADedicatedEntryTheMostSpecificParentWins()
    {
        TaskServiceImpl service = newService();

        TaskSearchResult[] deduped = TaskSearchResult.dedupeByDisplayName(
                service.searchWithVariants("Blood-starved venator"));

        Assert.assertEquals(1, deduped.length);
        Assert.assertEquals("Venator", deduped[0].parentTask.name);
    }

    /**
     * A tie broken only by parent name must not drift between loads. Two
     * independently-built services hash their tasks into different iteration
     * orders, so any winner that depended on that order shows up here.
     */
    @Test
    public void theWinnerIsStableAcrossServiceInstances()
    {
        TaskServiceImpl a = newService();
        TaskServiceImpl b = newService();

        List<String> fromA = new ArrayList<>();
        List<String> fromB = new ArrayList<>();

        for (String term : everyDisplayName(a))
        {
            collectRows(fromA, term, TaskSearchResult.dedupeByDisplayName(a.searchWithVariants(term)));
            collectRows(fromB, term, TaskSearchResult.dedupeByDisplayName(b.searchWithVariants(term)));
        }

        Assert.assertEquals("dedup picked a different parent on a second load", fromA, fromB);
    }

    private static void collectRows(List<String> sink, String term, TaskSearchResult[] results)
    {
        for (TaskSearchResult r : results)
        {
            sink.add(term + "|" + r.displayName + "|" + r.parentTask.name);
        }
    }

    /**
     * The panel hides bosses and animals unless the user opts in, so it filters
     * before deduping. Reversing that order would elect the boss entry and then
     * delete it, and monsters reachable only through a family entry — every
     * boss-task monster, with the toggle off — would vanish from search entirely.
     */
    @Test
    public void filteringBeforeDedupKeepsHiddenMonstersReachable()
    {
        TaskServiceImpl service = newService();

        TaskSearchResult[] visible = Arrays.stream(service.searchWithVariants("Dagannoth Prime"))
                .filter(r -> !isNonSlayerTask(r.parentTask))
                .toArray(TaskSearchResult[]::new);

        TaskSearchResult[] deduped = TaskSearchResult.dedupeByDisplayName(visible);
        Assert.assertEquals(
                "Dagannoth Prime must still be findable through the Dagannoth family entry",
                1, deduped.length);
        Assert.assertEquals("Dagannoth", deduped[0].parentTask.name);
    }

    /** Mirrors {@code MainPanel.isNonSlayerTask}. */
    private static boolean isNonSlayerTask(Task t)
    {
        if (t.masters == null)
        {
            return false;
        }
        for (String m : t.masters)
        {
            if (SlayerMaster.NON_SLAYER_ENEMIES.getDisplayName().equals(m)
                    || SlayerMaster.ANIMALS.getDisplayName().equals(m)
                    || SlayerMaster.BOSSES.getDisplayName().equals(m))
            {
                return true;
            }
        }
        return false;
    }

    /** Every name the search list can render: task names plus stripped variant names. */
    private static List<String> everyDisplayName(TaskServiceImpl service)
    {
        List<String> names = new ArrayList<>();
        for (Task task : service.getAll())
        {
            names.add(task.name);
            if (task.variants == null)
            {
                continue;
            }
            for (String variant : task.variants)
            {
                int flagIdx = variant.indexOf("--lvl ");
                names.add(flagIdx >= 0 ? variant.substring(0, flagIdx).trim() : variant);
            }
        }
        return names;
    }
}
