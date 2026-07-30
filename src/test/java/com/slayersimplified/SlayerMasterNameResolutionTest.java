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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every {@code masters} entry in every bundled task must resolve to a real
 * {@link SlayerMaster}.
 *
 * <p>This is the corpus-wide net for misspelled master names.
 * {@link SlayerMaster#fromTaskMasterName} returns {@code null} on an unknown
 * string and {@code GroupedTaskList} then quietly drops the task, so a one-character
 * typo removes a monster from a master's list with no error, no warning and no
 * visible symptom other than an absence. Four such typos ("Neive", "Spira" x2,
 * "Spiria" x2) shipped undetected before this test existed.</p>
 *
 * <p>Note that {@code fromTaskMasterName} matches on
 * {@code displayName.startsWith(name)}, which is what lets task data say
 * {@code "Nieve"} for the {@code "Nieve / Steve"} display name and {@code "Konar"}
 * for {@code "Konar quo Maten"}. That prefix match makes the check permissive, so
 * anything it still rejects is unambiguously wrong.</p>
 */
public class SlayerMasterNameResolutionTest
{
    /**
     * Debug-only fixture. It is filtered out of the panel by name in
     * {@code MainPanel} and deliberately carries junk data — including the RS3
     * master "Kuradal" and placeholder location strings — so it is exempt.
     */
    private static final String DEBUG_TASK_NAME = "A DEBUG TASK";

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

    @Test
    public void everyTaskMasterNameResolvesToAKnownMaster()
    {
        List<String> unresolved = new ArrayList<>();

        for (Task task : newService().getAll())
        {
            if (DEBUG_TASK_NAME.equals(task.name) || task.masters == null)
            {
                continue;
            }

            for (String master : task.masters)
            {
                if (SlayerMaster.fromTaskMasterName(master) == null)
                {
                    unresolved.add(task.name + " -> \"" + master + "\"");
                }
            }
        }

        Assert.assertEquals(
                "Task(s) reference a master name that resolves to no SlayerMaster; these tasks are"
                        + " silently dropped from the master-grouped list: " + unresolved,
                0,
                unresolved.size());
    }

    /** No task may ship an empty or blank master string. */
    @Test
    public void noTaskHasABlankMasterEntry()
    {
        List<String> blanks = new ArrayList<>();

        for (Task task : newService().getAll())
        {
            if (task.masters == null)
            {
                continue;
            }

            for (String master : task.masters)
            {
                if (master == null || master.trim().isEmpty())
                {
                    blanks.add(task.name);
                }
            }
        }

        Assert.assertEquals("Task(s) with a blank master entry: " + blanks, 0, blanks.size());
    }

    /**
     * Guards the prefix-match contract itself. An empty string matches every
     * display name, so {@code fromTaskMasterName("")} returning the first enum
     * constant is a trap worth documenting rather than discovering.
     */
    @Test
    public void masterNamesUsedByTaskDataResolveAsDocumented()
    {
        Assert.assertSame(SlayerMaster.NIEVE, SlayerMaster.fromTaskMasterName("Nieve"));
        Assert.assertSame(SlayerMaster.KONAR, SlayerMaster.fromTaskMasterName("Konar"));
        Assert.assertSame(SlayerMaster.KONAR, SlayerMaster.fromTaskMasterName("Konar quo Maten"));
        Assert.assertSame(SlayerMaster.SPRIA, SlayerMaster.fromTaskMasterName("Spria"));
        Assert.assertSame(SlayerMaster.MORTIMER, SlayerMaster.fromTaskMasterName("Mortimer"));

        Assert.assertNull("A misspelled master must not resolve",
                SlayerMaster.fromTaskMasterName("Neive"));
        Assert.assertNull("A misspelled master must not resolve",
                SlayerMaster.fromTaskMasterName("Spira"));
    }

    /**
     * The five tasks whose master names were misspelled must now actually group
     * under the master that was intended.
     * {@link #everyTaskMasterNameResolvesToAKnownMaster()} only proves the strings
     * resolve to <em>something</em>; a "fix" that turned {@code "Neive"} into
     * {@code "Nieve"} on the wrong task would still pass it.
     */
    @Test
    public void previouslyMisspelledTasksGroupUnderTheirIntendedMaster()
    {
        assertAssignedBy("Kurask", SlayerMaster.NIEVE);
        assertAssignedBy("Banshee", SlayerMaster.SPRIA);
        assertAssignedBy("Lizard", SlayerMaster.SPRIA);
        assertAssignedBy("Bear", SlayerMaster.SPRIA);
        assertAssignedBy("Cave slime", SlayerMaster.SPRIA);
    }

    private static void assertAssignedBy(String taskKey, SlayerMaster expected)
    {
        Task task = newService().get(taskKey);
        Assert.assertNotNull("Task '" + taskKey + "' must load from _index.json", task);
        Assert.assertNotNull(taskKey + " must list masters", task.masters);

        for (String master : task.masters)
        {
            if (SlayerMaster.fromTaskMasterName(master) == expected)
            {
                return;
            }
        }

        Assert.fail(taskKey + " must be assignable by " + expected.getDisplayName()
                + " but its masters resolve to none of it: " + Arrays.toString(task.masters));
    }
}
