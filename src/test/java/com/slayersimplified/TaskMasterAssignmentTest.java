/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.TaskServiceImpl;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

/**
 * Pins the {@code masters} list of tasks whose assignment is easy to get wrong,
 * against the {@code assignedby} field of the corresponding wiki infobox.
 *
 * <p>{@link SlayerMasterNameResolutionTest} only proves a master name is spelled
 * well enough to resolve; it cannot tell that a master who never assigns the task
 * was listed. The visible symptom is subtle in the wrong direction — the monster
 * simply shows up under a master who will never hand it out, which reads as
 * plausible until a player takes a block slot on it.</p>
 */
public class TaskMasterAssignmentTest
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

    /**
     * Mithril dragons are {@code assignedby = nieve,duradel}. Konar is the trap:
     * she is the only master who assigns the separate "Metal dragons" grouping
     * task, and mithril dragons are not part of it, so listing her here both
     * invents an assignment and blurs the line between the two entries.
     */
    @Test
    public void mithrilDragonsAreAssignedByNieveAndDuradelOnly()
    {
        Task mithril = newService().get("Mithril dragon");

        Assert.assertNotNull("Mithril dragon entry is missing", mithril);
        Assert.assertEquals(
                "wiki assignedby for Mithril dragon is nieve,duradel",
                Arrays.asList("Nieve", "Duradel"),
                Arrays.asList(mithril.masters));
    }

    /** The Metal dragons grouping is Konar-only, and excludes mithril dragons. */
    @Test
    public void metalDragonsIsKonarOnlyAndExcludesMithril()
    {
        Task metalDragons = newService().get("Metal dragons");

        Assert.assertNotNull("Metal dragons entry is missing", metalDragons);
        Assert.assertEquals(
                Arrays.asList("Konar quo Maten"),
                Arrays.asList(metalDragons.masters));

        for (String variant : metalDragons.variants)
        {
            Assert.assertFalse(
                    "Mithril dragon is its own task, not part of the Metal dragons grouping",
                    variant.toLowerCase().startsWith("mithril"));
        }
    }
}
