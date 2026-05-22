/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * This service loads a mapping of location names to WorldPoint coordinates
 * from a bundled JSON resource. Used to resolve location strings from
 * tasks.json into navigable coordinates.
 */
package com.slayersimplified.services;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.*;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads and provides a lookup from location name strings (e.g. "Slayer Tower")
 * to their corresponding WorldPoint coordinates for pathfinding navigation.
 *
 * Also exposes quest/skill requirements and suggested-item hints that are
 * stored directly in the JSON alongside coordinates, enabling fully data-driven
 * location gating without any hardcoded Java.
 *
 * Each entry in the JSON can carry optional fields:
 * <ul>
 *   <li>{@code aliases}      – alternative name strings that resolve to this entry</li>
 *   <li>{@code quests}       – {@link net.runelite.api.Quest} enum names that must be FINISHED</li>
 *   <li>{@code skills}       – map of {@link net.runelite.api.Skill} enum name → minimum level</li>
 *   <li>{@code suggestedItems} – free-text hints about items to bring</li>
 * </ul>
 *
 * Alias resolution is transparent: any method that accepts a location name will
 * first check whether that name is a registered alias and, if so, operate on the
 * canonical entry instead.
 *
 * Coordinates are loaded from /data/location_coordinates.json at startup.
 */
@Slf4j
@Singleton
public class LocationCoordinateService
{
    /** All names (canonical + aliases) → WorldPoint. */
    private final Map<String, WorldPoint> coordinates;

    /** Canonical lower-case name → full entry (for requirements / suggestions). */
    private final Map<String, CoordEntry> definitions;

    /** Alias lower-case name → canonical lower-case name. */
    private final Map<String, String> aliasToCanonical;

    @Inject
    public LocationCoordinateService(
            Gson gson,
            @Named("locationDataPath") String locationDataPath)
    {
        Map<String, WorldPoint> coords = new HashMap<>();
        Map<String, CoordEntry> defs = new HashMap<>();
        Map<String, String> aliasMap = new HashMap<>();

        InputStream inputStream = this.getClass().getResourceAsStream(locationDataPath);

        if (inputStream == null)
        {
            log.error("Could not find location coordinates JSON at path {}", locationDataPath);
            this.coordinates = Collections.emptyMap();
            this.definitions = Collections.emptyMap();
            this.aliasToCanonical = Collections.emptyMap();
            return;
        }

        try (Reader reader = new InputStreamReader(inputStream))
        {
            Type type = new TypeToken<Map<String, CoordEntry>>() {}.getType();
            Map<String, CoordEntry> data = gson.fromJson(reader, type);

            data.forEach((locationName, entry) ->
            {
                String canonicalKey = locationName.toLowerCase();
                WorldPoint wp = new WorldPoint(entry.x, entry.y, entry.plane);

                // Register the canonical name in both maps
                coords.put(canonicalKey, wp);
                defs.put(canonicalKey, entry);

                // Register each alias → same WorldPoint, and record alias → canonical mapping
                if (entry.aliases != null)
                {
                    for (String alias : entry.aliases)
                    {
                        String aliasKey = alias.toLowerCase();
                        coords.put(aliasKey, wp);
                        aliasMap.put(aliasKey, canonicalKey);
                    }
                }
            });

            log.info("Loaded {} location definitions ({} total name lookups) for Slayer Simplified",
                    defs.size(), coords.size());
        }
        catch (JsonSyntaxException e)
        {
            log.error("JSON syntax error in location coordinates file {}", locationDataPath, e);
        }
        catch (IOException e)
        {
            log.error("Could not read location coordinates from {}", locationDataPath, e);
        }

        this.coordinates = Collections.unmodifiableMap(coords);
        this.definitions = Collections.unmodifiableMap(defs);
        this.aliasToCanonical = Collections.unmodifiableMap(aliasMap);
    }

    // -------------------------------------------------------------------------
    // Coordinate lookups
    // -------------------------------------------------------------------------

    /**
     * Look up the WorldPoint for a location name. Case-insensitive.
     * Alias names resolve transparently to their canonical entry's coordinates.
     *
     * @param locationName the location string (e.g. "Slayer Tower")
     * @return the WorldPoint, or null if no coordinates are mapped
     */
    public WorldPoint getCoordinates(String locationName)
    {
        if (locationName == null)
        {
            return null;
        }
        return coordinates.get(locationName.toLowerCase());
    }

    /**
     * Returns an unmodifiable view of all name-to-coordinate mappings,
     * including both canonical names and registered aliases.
     */
    public Map<String, WorldPoint> getAll()
    {
        return coordinates;
    }

    // -------------------------------------------------------------------------
    // Alias resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves a location name to its canonical lower-case key. If the name is
     * a registered alias the canonical key is returned; otherwise the input is
     * returned as lower-case. Returns {@code null} when {@code locationName} is
     * {@code null}.
     */
    public String resolveCanonical(String locationName)
    {
        if (locationName == null)
        {
            return null;
        }
        String key = locationName.toLowerCase();
        String canonical = aliasToCanonical.get(key);
        return canonical != null ? canonical : key;
    }

    /**
     * Returns an unmodifiable set of all canonical location names (lower-case).
     * Does not include alias names. Useful for iterating all defined locations.
     */
    public Set<String> getAllCanonicalNames()
    {
        return definitions.keySet();
    }

    // -------------------------------------------------------------------------
    // Requirement and suggestion accessors (alias-transparent)
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link net.runelite.api.Quest} enum names that must all be
     * {@code FINISHED} to access this location. Resolves aliases transparently.
     * Returns an empty array when no quest is required or the location is unknown.
     */
    public String[] getQuestNames(String locationName)
    {
        CoordEntry entry = getDefinitionEntry(locationName);
        if (entry == null || entry.quests == null)
        {
            return new String[0];
        }
        return entry.quests;
    }

    /**
     * Returns the minimum real skill levels required to access this location,
     * keyed by {@link net.runelite.api.Skill} enum name. Resolves aliases
     * transparently. Returns an empty map when no skill gate is defined.
     */
    public Map<String, Integer> getSkillRequirements(String locationName)
    {
        CoordEntry entry = getDefinitionEntry(locationName);
        if (entry == null || entry.skills == null)
        {
            return Collections.emptyMap();
        }
        return entry.skills;
    }

    /**
     * Returns free-text hints about items to bring when visiting this location
     * (e.g. "Light source (e.g. Bullseye lantern)"). Resolves aliases
     * transparently. Returns an empty array when none are recorded.
     */
    public String[] getSuggestedItemNames(String locationName)
    {
        CoordEntry entry = getDefinitionEntry(locationName);
        if (entry == null || entry.suggestedItems == null)
        {
            return new String[0];
        }
        return entry.suggestedItems;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private CoordEntry getDefinitionEntry(String locationName)
    {
        if (locationName == null)
        {
            return null;
        }
        String canonical = resolveCanonical(locationName);
        return definitions.get(canonical);
    }

    /**
     * JSON deserialization model for a single location entry. All fields
     * beyond {@code x}/{@code y}/{@code plane} are optional; Gson leaves them
     * {@code null} when absent, which is treated as "no data" by the accessors.
     */
    private static class CoordEntry
    {
        int x;
        int y;
        int plane;

        /** Alternative names that resolve to this entry. */
        String[] aliases;

        /** {@link net.runelite.api.Quest} enum names that must all be FINISHED. */
        String[] quests;

        /** {@link net.runelite.api.Skill} enum name → minimum real level. */
        Map<String, Integer> skills;

        /** Free-text item suggestions for accessing this location. */
        String[] suggestedItems;
    }
}
