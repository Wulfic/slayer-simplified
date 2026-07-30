/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.LocationCoordinateService;
import com.slayersimplified.services.TaskServiceImpl;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates that every quest and skill name referenced in requirements.json maps
 * to a valid {@link Quest} / {@link Skill} enum constant.  Fails fast with a
 * descriptive message listing every bad name so they can all be fixed at once.
 */
public class RequirementsJsonValidationTest
{
    @Test
    public void allQuestNamesAreValid() throws Exception
    {
        InputStream is = getClass().getResourceAsStream("/data/requirements.json");
        Assert.assertNotNull("requirements.json must exist on the classpath", is);

        List<String> errors = new ArrayList<>();

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
        {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();

            // Validate both "locations" and "monsters" sections
            for (String section : new String[]{"locations", "monsters"})
            {
                if (!root.has(section)) continue;
                JsonObject sectionObj = root.getAsJsonObject(section);

                for (Map.Entry<String, JsonElement> entry : sectionObj.entrySet())
                {
                    String entryKey = entry.getKey();
                    JsonObject entryObj = entry.getValue().getAsJsonObject();

                    // Check "quests" (AND-list)
                    if (entryObj.has("quests"))
                    {
                        for (JsonElement q : entryObj.getAsJsonArray("quests"))
                        {
                            String name = q.getAsString();
                            try { Quest.valueOf(name); }
                            catch (IllegalArgumentException e)
                            {
                                errors.add("[" + section + "][" + entryKey + "].quests: unknown Quest '" + name + "'");
                            }
                        }
                    }

                    // Check "questsAny" (OR-list)
                    if (entryObj.has("questsAny"))
                    {
                        for (JsonElement q : entryObj.getAsJsonArray("questsAny"))
                        {
                            String name = q.getAsString();
                            try { Quest.valueOf(name); }
                            catch (IllegalArgumentException e)
                            {
                                errors.add("[" + section + "][" + entryKey + "].questsAny: unknown Quest '" + name + "'");
                            }
                        }
                    }

                    // Check "skills"
                    if (entryObj.has("skills"))
                    {
                        for (Map.Entry<String, JsonElement> sk : entryObj.getAsJsonObject("skills").entrySet())
                        {
                            String skillName = sk.getKey();
                            try { Skill.valueOf(skillName); }
                            catch (IllegalArgumentException e)
                            {
                                errors.add("[" + section + "][" + entryKey + "].skills: unknown Skill '" + skillName + "'");
                            }
                        }
                    }
                }
            }
        }

        if (!errors.isEmpty())
        {
            Assert.fail("requirements.json contains " + errors.size() + " invalid enum name(s):\n  "
                    + String.join("\n  ", errors));
        }
    }

    /**
     * Every location key in requirements.json must resolve to a location that
     * actually exists in location_coordinates.json.
     *
     * LocationRequirementService stores requirements under
     * {@code resolveCanonical(key)}, and tasks look them up by the canonical name
     * of the location they reference. A key that resolves to nothing therefore
     * parks its requirements under a name no lookup ever reaches, and the
     * requirement is silently dropped instead of failing loudly — which is how a
     * rename in location_coordinates.json orphaned the Mourner Tunnels quest
     * requirement. Renaming a location means adding the old name as an alias.
     */
    @Test
    public void everyRequirementLocationResolvesToAKnownLocation() throws Exception
    {
        LocationCoordinateService locationService =
                new LocationCoordinateService(new Gson(), "/data/location_coordinates.json");

        InputStream is = getClass().getResourceAsStream("/data/requirements.json");
        Assert.assertNotNull("requirements.json must exist on the classpath", is);

        List<String> orphans = new ArrayList<>();

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
        {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (!root.has("locations"))
            {
                return;
            }

            for (String key : root.getAsJsonObject("locations").keySet())
            {
                String canonical = locationService.resolveCanonical(key);
                if (!locationService.getAllCanonicalNames().contains(canonical))
                {
                    orphans.add(key + "' resolved to '" + canonical);
                }
            }
        }

        Assert.assertEquals(
                "requirements.json references location(s) missing from location_coordinates.json;"
                        + " add the old name as an alias if it was renamed: " + orphans,
                0,
                orphans.size());
    }

    /**
     * Every key in the {@code monsters} section must name a real task or a real
     * task variant.
     *
     * <p><strong>The {@code monsters} section is currently inert.</strong>
     * {@code LocationRequirementService} deserializes only the {@code locations}
     * section — its {@code RequirementsFile} model has no {@code monsters} field —
     * and no other production class reads requirements.json at all. Nothing has
     * ever consumed it: no commit in this repository's history contains a reader
     * for it. So these entries describe real in-game requirements (Bloodveld needs
     * Priest in Peril, Venator needs The Blood Moon Rises and 74 Slayer) that the
     * panel never surfaces.</p>
     *
     * <p>The keys are validated regardless, because the day someone does wire the
     * section up, a key naming no task would fail the same silent way a bad
     * location key does — parked under a name no lookup reaches. Keeping them
     * honest now makes that a one-line change instead of a debugging session.</p>
     *
     * <p>Variant names are accepted because {@code "Shadow hound"} is a variant of
     * the {@code Dog} task rather than a task of its own, and its Sins of the
     * Father requirement belongs to that variant alone.</p>
     */
    @Test
    public void monsterRequirementKeysNameARealTaskOrVariant() throws Exception
    {
        TaskServiceImpl taskService = new TaskServiceImpl(
                new Gson(),
                "/data/tasks",
                "/data/non_slayer_tasks",
                "/data/boss_tasks",
                "/data/animal_tasks",
                "https://oldschool.runescape.wiki/w/",
                "/images/monsters/");

        Set<String> knownMonsters = new HashSet<>();
        for (Task task : taskService.getAll())
        {
            knownMonsters.add(task.name.toLowerCase());
            if (task.variants == null)
            {
                continue;
            }
            for (String variant : task.variants)
            {
                if (variant != null)
                {
                    // Variants carry combat levels as "--lvl N" suffixes; strip them.
                    knownMonsters.add(variant.split(" --lvl")[0].trim().toLowerCase());
                }
            }
        }

        InputStream is = getClass().getResourceAsStream("/data/requirements.json");
        Assert.assertNotNull("requirements.json must exist on the classpath", is);

        List<String> unknown = new ArrayList<>();
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
        {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            if (!root.has("monsters"))
            {
                return;
            }

            for (String key : root.getAsJsonObject("monsters").keySet())
            {
                if (!knownMonsters.contains(key.toLowerCase()))
                {
                    unknown.add(key);
                }
            }
        }

        Assert.assertEquals(
                "requirements.json [monsters] key(s) name no bundled task or task variant: "
                        + unknown,
                0,
                unknown.size());
    }
}
