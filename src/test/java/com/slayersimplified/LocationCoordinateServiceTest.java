/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.LocationCoordinateService;
import com.slayersimplified.services.TaskServiceImpl;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Unit tests for LocationCoordinateService — verifies that location_coordinates.json
 * is parsed correctly and specific locations can be looked up.
 */
public class LocationCoordinateServiceTest
{
    private final LocationCoordinateService service =
            new LocationCoordinateService(new Gson(), "/data/location_coordinates.json");

    @Test
    public void testZanarisHasCoordinates()
    {
        WorldPoint coords = service.getCoordinates("Zanaris");
        Assert.assertNotNull("Zanaris should have coordinates mapped", coords);
    }

    @Test
    public void testSlayerTowerHasCoordinates()
    {
        WorldPoint coords = service.getCoordinates("Slayer Tower");
        Assert.assertNotNull("Slayer Tower should have coordinates mapped", coords);
    }

    @Test
    public void testLumbridgeCowFieldHasCoordinates()
    {
        WorldPoint coords = service.getCoordinates("Lumbridge Cow Field");
        Assert.assertNotNull("Lumbridge Cow Field should have coordinates mapped", coords);
    }

    @Test
    public void testCaseSensitivity()
    {
        WorldPoint lower = service.getCoordinates("zanaris");
        WorldPoint mixed = service.getCoordinates("Zanaris");
        WorldPoint upper = service.getCoordinates("ZANARIS");
        Assert.assertNotNull("Lowercase lookup should work", lower);
        Assert.assertNotNull("Mixed-case lookup should work", mixed);
        Assert.assertNotNull("Uppercase lookup should work", upper);
    }

    @Test
    public void testAllLocationCountIsReasonable()
    {
        // Should have at least 150 locations loaded (canonical + aliases included)
        Assert.assertTrue(
                "Should have loaded at least 150 locations, got: " + service.getAll().size(),
                service.getAll().size() >= 150
        );
    }

    @Test
    public void testAliasResolvesToSameCoordinates()
    {
        // "Mourner Tunnels" is canonical; the older, longer name is an alias for it.
        WorldPoint canonical = service.getCoordinates("Mourner Tunnels");
        WorldPoint alias = service.getCoordinates("Path to Temple of Light (Mourner tunnels)");
        Assert.assertNotNull("Canonical name should resolve", canonical);
        Assert.assertNotNull("Alias should resolve to same WorldPoint", alias);
        Assert.assertEquals("Alias should give identical coordinates to canonical entry", canonical, alias);
    }

    @Test
    public void testResolveCanonical()
    {
        // "Stronhold of Security" (typo) is an alias for "stronghold of security"
        String canonical = service.resolveCanonical("Stronhold of Security");
        Assert.assertEquals("stronghold of security", canonical);

        // A canonical name should return itself lower-cased
        String self = service.resolveCanonical("Zanaris");
        Assert.assertEquals("zanaris", self);
    }

    // -------------------------------------------------------------------------
    // Corpus-wide: task variantLocations must resolve to real coordinates
    // -------------------------------------------------------------------------

    /**
     * Debug-only fixture, filtered out of the panel by name in {@code MainPanel}.
     * It deliberately carries placeholder location strings (one is literally
     * "Wilderness Slayer Cave - Find real Cords"), so it is exempt.
     */
    private static final String DEBUG_TASK_NAME = "A DEBUG TASK";

    /**
     * Location strings referenced by task data that have no entry in
     * location_coordinates.json. These are <strong>pre-existing</strong> debt that
     * predates this test: a location the coordinate service cannot resolve yields no
     * WorldPoint, so "navigate to location" silently does nothing for that variant.
     *
     * <p>This set is a <strong>ratchet, not a permission slip</strong>. It exists so the
     * check can be enforced today without bundling ~20 unrelated data fixes into the
     * commit that introduced it. Two rules keep it honest:</p>
     * <ul>
     *   <li>{@link #noNewUnresolvableTaskLocationsAreIntroduced()} fails if any
     *       <em>new</em> orphan appears — so this list can never grow silently.</li>
     *   <li>{@link #knownOrphanListHasNoStaleEntries()} fails if an entry here has since
     *       been fixed — so the list must shrink as the debt is paid off, and cannot rot
     *       into a permanent excuse.</li>
     * </ul>
     *
     * <p>Entries are lower-case (the service lower-cases every key). Several are plainly
     * typos of each other — "Wilderness Slayer Cave" / "Wildnerness Slayer Cave" /
     * "Wilderness Slayer Dungeon" — and should collapse to one canonical entry with
     * aliases when someone triages them.</p>
     */
    private static final Set<String> KNOWN_UNRESOLVABLE_TASK_LOCATIONS = new HashSet<>(Arrays.asList(
            "calvar'ion's den",
            "enchanted valley",
            "meiyerditch mine",
            "north of venenatis",
            "spindel's den",
            "stronghold of security (level 2)",
            "wilderness slayer cave",
            "wilderness slayer dungeon",
            "wildnerness slayer cave"));

    private static TaskServiceImpl newTaskService()
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

    /** Maps each unresolvable location string (lower-case) to the tasks referencing it. */
    private Map<String, Set<String>> findUnresolvableTaskLocations()
    {
        Map<String, Set<String>> orphans = new TreeMap<>();

        for (Task task : newTaskService().getAll())
        {
            if (DEBUG_TASK_NAME.equals(task.name) || task.variantLocations == null)
            {
                continue;
            }

            for (String[] locations : task.variantLocations.values())
            {
                if (locations == null)
                {
                    continue;
                }

                for (String location : locations)
                {
                    if (location == null || service.getCoordinates(location) != null)
                    {
                        continue;
                    }
                    orphans.computeIfAbsent(location.toLowerCase(), k -> new TreeSet<>())
                            .add(task.name);
                }
            }
        }

        return orphans;
    }

    /**
     * No task may reference a location that location_coordinates.json does not know.
     * A missing coordinate entry is invisible at runtime — the Locations tab renders
     * the name but navigation has nowhere to send the player.
     */
    @Test
    public void noNewUnresolvableTaskLocationsAreIntroduced()
    {
        Map<String, Set<String>> orphans = findUnresolvableTaskLocations();

        Map<String, Set<String>> newOrphans = new TreeMap<>(orphans);
        newOrphans.keySet().removeAll(KNOWN_UNRESOLVABLE_TASK_LOCATIONS);

        Assert.assertEquals(
                "Task(s) reference location(s) with no entry in location_coordinates.json."
                        + " Add the coordinates (or an alias on the existing entry) - do not add"
                        + " them to KNOWN_UNRESOLVABLE_TASK_LOCATIONS: " + newOrphans,
                0,
                newOrphans.size());
    }

    /**
     * Keeps the ratchet tightening: once an orphan is fixed its entry must be deleted
     * from {@link #KNOWN_UNRESOLVABLE_TASK_LOCATIONS}, so the list cannot silently
     * outlive the debt it documents.
     */
    @Test
    public void knownOrphanListHasNoStaleEntries()
    {
        Set<String> stillOrphaned = findUnresolvableTaskLocations().keySet();

        Set<String> fixed = new TreeSet<>(KNOWN_UNRESOLVABLE_TASK_LOCATIONS);
        fixed.removeAll(stillOrphaned);

        Assert.assertEquals(
                "These location(s) now resolve and must be removed from"
                        + " KNOWN_UNRESOLVABLE_TASK_LOCATIONS: " + fixed,
                0,
                fixed.size());
    }

    /** The Wyrmscraig / Vampyrium locations added for the Mortimer update must resolve. */
    @Test
    public void wyrmscraigAndVampyriumLocationsResolve()
    {
        Assert.assertEquals(new WorldPoint(2571, 2267, 0), service.getCoordinates("Wyrmscraig"));
        Assert.assertEquals(new WorldPoint(2590, 8615, 0), service.getCoordinates("Wyrmscraig Cavern"));
        Assert.assertEquals(new WorldPoint(2583, 7773, 0), service.getCoordinates("Apsul Hunting Ground"));
        Assert.assertEquals(new WorldPoint(2525, 7762, 0), service.getCoordinates("Virer Hunting Ground"));
    }

    // -------------------------------------------------------------------------
    // Alias / canonical-name collisions
    // -------------------------------------------------------------------------

    /**
     * Locations that have their own coordinate entry <em>and</em> appear as an alias
     * on a different entry, so their own coordinates are unreachable.
     *
     * <p>The service loads canonical names and aliases into one flat map, in JSON
     * document order, with plain {@code put}. When name {@code X} is both a
     * top-level entry and an alias of {@code Y}, whichever appears later wins the
     * coordinate lookup, and {@code resolveCanonical("X")} returns {@code "y"}
     * regardless. Two of the three below currently return the <em>other</em>
     * location's coordinates, so "navigate to location" sends the player to the
     * wrong place:</p>
     * <ul>
     *   <li>{@code Artio's Den} — own entry (3039, 10266, 0), but aliased on
     *       Hunter's End, so lookups return (3115, 3677, 0). Affects the
     *       <em>Artio</em> boss task.</li>
     *   <li>{@code Grimstone Dungeon} — own entry (2913, 4067, 0), but aliased on
     *       Taverley Dungeon, so lookups return (2884, 3397, 0). Affects the
     *       <em>Frost dragon</em> task.</li>
     *   <li>{@code South of Slayer Tower} — own entry (3428, 3517, 0) wins the
     *       coordinate lookup by document order, but {@code resolveCanonical}
     *       still redirects to {@code slayer tower}, so it would inherit the
     *       tower's access requirements rather than its own. Neither has any
     *       today, so nothing is visibly broken yet.</li>
     * </ul>
     *
     * <p>Deciding which coordinate is correct needs someone who knows the place,
     * so these are <strong>listed rather than silently repointed</strong>. Same
     * ratchet rules as {@link #KNOWN_UNRESOLVABLE_TASK_LOCATIONS}: nothing new may
     * be added, and a fixed entry must be deleted from the list. The fix is to
     * remove the alias from the other entry (keeping the dedicated one) or delete
     * the dedicated entry (keeping the alias) — never both.</p>
     */
    private static final Set<String> KNOWN_SHADOWED_LOCATION_NAMES = new HashSet<>(Arrays.asList(
            "artio's den",
            "grimstone dungeon",
            "south of slayer tower"));

    /** Canonical names whose own entry is shadowed by an alias on another entry. */
    private Set<String> findShadowedCanonicalNames()
    {
        Set<String> shadowed = new TreeSet<>();

        for (String canonical : service.getAllCanonicalNames())
        {
            if (!canonical.equals(service.resolveCanonical(canonical)))
            {
                shadowed.add(canonical);
            }
        }

        return shadowed;
    }

    @Test
    public void noNewLocationNameIsShadowedByAnAlias()
    {
        Set<String> newlyShadowed = new TreeSet<>(findShadowedCanonicalNames());
        newlyShadowed.removeAll(KNOWN_SHADOWED_LOCATION_NAMES);

        Assert.assertEquals(
                "Location(s) have their own coordinate entry but are also listed as an alias"
                        + " on another entry, so their own coordinates are unreachable. Remove"
                        + " the duplicate alias - do not add these to"
                        + " KNOWN_SHADOWED_LOCATION_NAMES: " + newlyShadowed,
                0,
                newlyShadowed.size());
    }

    @Test
    public void knownShadowedListHasNoStaleEntries()
    {
        Set<String> fixed = new TreeSet<>(KNOWN_SHADOWED_LOCATION_NAMES);
        fixed.removeAll(findShadowedCanonicalNames());

        Assert.assertEquals(
                "These location name(s) are no longer shadowed and must be removed from"
                        + " KNOWN_SHADOWED_LOCATION_NAMES: " + fixed,
                0,
                fixed.size());
    }
}
