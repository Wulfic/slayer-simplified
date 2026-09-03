/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.google.gson.Gson;
import com.slayersimplified.domain.Task;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Guards the Slayer-task-name → data-entry mapping.
 *
 * The game (and RuneLite's Slayer plugin) hands us the task <em>category</em>
 * name, which does not always match the monster name we key our data on:
 * "Mutated zygomites" is the Zygomite entry, "Jellies" is the Jelly entry, and
 * so on. When the lookup misses, the panel shows no monster image and quick
 * navigation silently refuses to route — the bug Fodziix reported in issue #6.
 *
 * Every mismatch is resolved by an {@code aliases} array in the task JSON, so
 * these tests assert the alias index actually resolves them.
 */
public class TaskAliasResolutionTest
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
     * Slayer task name as assigned in game → the task entry it must resolve to.
     * Both the plural the game uses and the singular our chat-message
     * normalisation produces have to land on the same entry.
     */
    private static final Map<String, String> TASK_NAME_TO_ENTRY = new HashMap<>();
    static
    {
        TASK_NAME_TO_ENTRY.put("Mutated zygomites", "Zygomite");
        TASK_NAME_TO_ENTRY.put("Mutated zygomite", "Zygomite");
        TASK_NAME_TO_ENTRY.put("Jellies", "Jelly");
        TASK_NAME_TO_ENTRY.put("Jellie", "Jelly");
        TASK_NAME_TO_ENTRY.put("Wolves", "Wolf");
        TASK_NAME_TO_ENTRY.put("Wolve", "Wolf");
        TASK_NAME_TO_ENTRY.put("Dwarves", "Dwarf");
        TASK_NAME_TO_ENTRY.put("Dwarve", "Dwarf");
        TASK_NAME_TO_ENTRY.put("Lizardmen", "Lizardman");
        TASK_NAME_TO_ENTRY.put("Fleshcrawlers", "Flesh Crawler");
        TASK_NAME_TO_ENTRY.put("Fleshcrawler", "Flesh Crawler");
        // Names ending in a non-plural 's' that the singularisation over-strips.
        TASK_NAME_TO_ENTRY.put("Sarachni", "Sarachnis");
        TASK_NAME_TO_ENTRY.put("Vardorvi", "Vardorvis");
        TASK_NAME_TO_ENTRY.put("Venenati", "Venenatis");
        // Boss tasks the game prefixes with "The".
        TASK_NAME_TO_ENTRY.put("The Chaos Elemental", "Chaos Elemental");
        TASK_NAME_TO_ENTRY.put("The Chaos Fanatic", "Chaos Fanatic");
        TASK_NAME_TO_ENTRY.put("The Giant Mole", "Giant Mole");
        TASK_NAME_TO_ENTRY.put("The Kalphite Queen", "Kalphite Queen");
        TASK_NAME_TO_ENTRY.put("The King Black Dragon", "King Black Dragon");
        TASK_NAME_TO_ENTRY.put("The Phantom Muspah", "Phantom Muspah");
        TASK_NAME_TO_ENTRY.put("The Leviathan", "Leviathan");
        TASK_NAME_TO_ENTRY.put("The Whisperer", "Whisperer");
    }

    /**
     * Boss Slayer task names → the entry they must resolve to.
     *
     * <p>Unlike {@link #TASK_NAME_TO_ENTRY} these keys include the canonical names
     * themselves, because the point of this map is the round trip the plugin actually
     * performs: RuneLite hands us the assigned name, {@code SlayerTaskTracker}
     * normalises it, and the normalised string has to find the entry. That
     * normalisation strips a trailing "s" (except after "ss"/"us"), so every plural
     * boss task arrives singularised — "Dagannoth Kings" reaches us as
     * "Dagannoth King" — and only an alias can catch it.</p>
     */
    private static final Map<String, String> BOSS_TASK_NAME_TO_ENTRY = new HashMap<>();
    static
    {
        BOSS_TASK_NAME_TO_ENTRY.put("Abyssal Sire", "Abyssal Sire");
        BOSS_TASK_NAME_TO_ENTRY.put("Alchemical Hydra", "Alchemical Hydra");
        BOSS_TASK_NAME_TO_ENTRY.put("Araxxor", "Araxxor");
        BOSS_TASK_NAME_TO_ENTRY.put("Cerberus", "Cerberus");
        BOSS_TASK_NAME_TO_ENTRY.put("Deranged Archaeologist", "Deranged Archaeologist");
        BOSS_TASK_NAME_TO_ENTRY.put("Kraken", "Kraken");
        BOSS_TASK_NAME_TO_ENTRY.put("Maggot King", "Maggot King");
        BOSS_TASK_NAME_TO_ENTRY.put("Shellbane Gryphon", "Shellbane Gryphon");
        BOSS_TASK_NAME_TO_ENTRY.put("Thermonuclear Smoke Devil", "Thermonuclear Smoke Devil");
        BOSS_TASK_NAME_TO_ENTRY.put("TzTok-Jad", "TzTok-Jad");
        BOSS_TASK_NAME_TO_ENTRY.put("TzKal-Zuk", "TzKal-Zuk");
        // Plural task names, in both the form the game uses and the form the
        // tracker's singularisation produces.
        BOSS_TASK_NAME_TO_ENTRY.put("Barrows Brothers", "Barrows Brothers");
        BOSS_TASK_NAME_TO_ENTRY.put("Barrows Brother", "Barrows Brothers");
        BOSS_TASK_NAME_TO_ENTRY.put("Dagannoth Kings", "Dagannoth Kings");
        BOSS_TASK_NAME_TO_ENTRY.put("Dagannoth King", "Dagannoth Kings");
        BOSS_TASK_NAME_TO_ENTRY.put("Grotesque Guardians", "Grotesque Guardians");
        BOSS_TASK_NAME_TO_ENTRY.put("Grotesque Guardian", "Grotesque Guardians");
        BOSS_TASK_NAME_TO_ENTRY.put("Metal dragons", "Metal dragons");
        BOSS_TASK_NAME_TO_ENTRY.put("Metal dragon", "Metal dragons");
    }

    /** Every boss task name the game can assign must land on a bundled entry. */
    @Test
    public void everyBossTaskNameResolvesToItsEntry()
    {
        TaskServiceImpl service = newService();

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> e : BOSS_TASK_NAME_TO_ENTRY.entrySet())
        {
            Task task = service.get(e.getKey());
            if (task == null)
            {
                failures.add(e.getKey() + " -> (not found)");
            }
            else if (!task.name.equalsIgnoreCase(e.getValue()))
            {
                failures.add(e.getKey() + " -> " + task.name + " (expected " + e.getValue() + ")");
            }
        }

        Assert.assertTrue("boss task names that do not resolve: " + failures, failures.isEmpty());
    }

    /**
     * A boss entry with no bundled PNG falls back to the shared "?" placeholder, which
     * is exactly the "no data" symptom these entries were added to remove. Every boss
     * task must carry its own image and at least one location to route to.
     */
    @Test
    public void everyBossTaskHasItsOwnImageAndALocation()
    {
        TaskServiceImpl service = newService();
        Task placeholderHolder = service.get("A DEBUG TASK");
        Assert.assertNotNull("the debug fixture task should exist", placeholderHolder);

        List<String> failures = new ArrayList<>();
        for (String name : new HashSet<>(BOSS_TASK_NAME_TO_ENTRY.values()))
        {
            Task task = service.get(name);
            Assert.assertNotNull(name + " must resolve", task);

            if (task.image == null || task.image == placeholderHolder.image)
            {
                failures.add(name + " (no monster image)");
            }
            if (task.variantLocations == null || task.variantLocations.isEmpty())
            {
                failures.add(name + " (no locations)");
            }
        }

        Assert.assertTrue("boss tasks missing data: " + failures, failures.isEmpty());
    }

    @Test
    public void everyKnownSlayerTaskNameResolvesToItsEntry()
    {
        TaskServiceImpl service = newService();

        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> e : TASK_NAME_TO_ENTRY.entrySet())
        {
            Task task = service.get(e.getKey());
            if (task == null)
            {
                failures.add(e.getKey() + " -> (not found)");
            }
            else if (!task.name.equalsIgnoreCase(e.getValue()))
            {
                failures.add(e.getKey() + " -> " + task.name + " (expected " + e.getValue() + ")");
            }
        }

        Assert.assertTrue("Slayer task names that do not resolve: " + failures, failures.isEmpty());
    }

    /** Issue #6: the assigned name must reach the Zygomite entry, image and locations intact. */
    @Test
    public void mutatedZygomitesResolvesToZygomiteWithImageAndLocations()
    {
        TaskServiceImpl service = newService();

        Task task = service.get("Mutated zygomites");
        Assert.assertNotNull("'Mutated zygomites' must resolve to a task", task);
        Assert.assertEquals("Zygomite", task.name);
        // "A DEBUG TASK" ships no PNG, so its image is the shared placeholder
        // instance — a real image must not be that same object.
        Task placeholderHolder = service.get("A DEBUG TASK");
        Assert.assertNotNull("the debug fixture task should exist", placeholderHolder);
        Assert.assertNotNull("Zygomite must carry a monster image", task.image);
        Assert.assertNotSame("Zygomite must load its own image, not the '?' placeholder",
                placeholderHolder.image, task.image);
        Assert.assertNotNull("zygomite.png must be bundled",
                getClass().getResource("/images/monsters/zygomite.png"));
        Assert.assertNotNull("Zygomite must carry per-variant locations", task.variantLocations);
        Assert.assertFalse("Zygomite must have at least one location to route to",
                task.variantLocations.isEmpty());
    }

    /** Aliases are a lookup path only — they must not show up as extra list entries. */
    @Test
    public void aliasesDoNotDuplicateTasksInTheList()
    {
        TaskServiceImpl service = newService();
        Task[] all = service.getAll();

        long zygomites = Arrays.stream(all).filter(t -> "Zygomite".equals(t.name)).count();
        Assert.assertEquals("Zygomite must appear exactly once in the task list", 1, zygomites);

        long aliasNamed = Arrays.stream(all)
                .filter(t -> TASK_NAME_TO_ENTRY.containsKey(t.name))
                .count();
        Assert.assertEquals("no alias may be listed as a task in its own right", 0, aliasNamed);
    }

    /** An alias that shadows a real task key would make that task unreachable. */
    @Test
    public void noAliasCollidesWithACanonicalTaskName()
    {
        TaskServiceImpl service = newService();
        Task[] all = service.getAll();

        Map<String, String> canonical = new HashMap<>();
        for (Task t : all)
        {
            canonical.put(t.name.toLowerCase(), t.name);
        }

        Map<String, String> seenAliases = new HashMap<>();
        List<String> failures = new ArrayList<>();
        for (Task t : all)
        {
            if (t.aliases == null)
            {
                continue;
            }
            for (String alias : t.aliases)
            {
                Assert.assertNotNull("null alias on task " + t.name, alias);
                String key = alias.trim().toLowerCase();
                Assert.assertFalse("blank alias on task " + t.name, key.isEmpty());

                if (canonical.containsKey(key))
                {
                    failures.add("alias '" + alias + "' on " + t.name
                            + " shadows task '" + canonical.get(key) + "'");
                }
                String owner = seenAliases.put(key, t.name);
                if (owner != null)
                {
                    failures.add("alias '" + alias + "' claimed by both " + owner + " and " + t.name);
                }
            }
        }

        Assert.assertTrue("alias conflicts: " + failures, failures.isEmpty());
    }

    /** Searching by the in-game task name should surface the monster it maps to. */
    @Test
    public void searchFindsTasksByAlias()
    {
        TaskServiceImpl service = newService();

        Task[] matches = service.searchPartialName("mutated zygomite");
        Assert.assertTrue("searching the task name should find Zygomite",
                Arrays.stream(matches).anyMatch(t -> "Zygomite".equals(t.name)));
    }
}
