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
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Loads and provides a lookup from location name strings (e.g. "Slayer Tower")
 * to their corresponding WorldPoint coordinates for pathfinding navigation.
 *
 * <p>Each entry in the JSON carries:
 * <ul>
 *   <li>{@code x}, {@code y}, {@code plane} – tile coordinates</li>
 *   <li>{@code aliases} – alternative name strings that resolve to this entry (optional)</li>
 * </ul>
 *
 * <p>Quest/skill access requirements live in {@code requirements.json} and are
 * handled by {@link LocationRequirementService}. Suggested items live in
 * the individual task JSON files.
 *
 * <p>Coordinates are loaded from /data/location_coordinates.json at startup.
 */
@Slf4j
@Singleton
public class LocationCoordinateService
{
    /** All names (canonical + aliases) → WorldPoint. */
    private final Map<String, WorldPoint> coordinates;

    /** Alias lower-case name → canonical lower-case name. */
    private final Map<String, String> aliasToCanonical;

    /** All canonical lower-case names (for external iteration). */
    private final Set<String> canonicalNames;

    @Inject
    public LocationCoordinateService(
            Gson gson,
            @Named("locationDataPath") String locationDataPath)
    {
        Map<String, WorldPoint> coords = new HashMap<>();
        Map<String, String> aliasMap = new HashMap<>();
        java.util.LinkedHashSet<String> canonicals = new java.util.LinkedHashSet<>();

        InputStream inputStream = this.getClass().getResourceAsStream(locationDataPath);

        if (inputStream == null)
        {
            log.error("Could not find location coordinates JSON at path {}", locationDataPath);
            this.coordinates = Collections.emptyMap();
            this.aliasToCanonical = Collections.emptyMap();
            this.canonicalNames = Collections.emptySet();
            return;
        }

        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8))
        {
            Type type = new TypeToken<Map<String, CoordEntry>>() {}.getType();
            Map<String, CoordEntry> data = gson.fromJson(reader, type);

            data.forEach((locationName, entry) ->
            {
                String canonicalKey = locationName.toLowerCase();
                WorldPoint wp = new WorldPoint(entry.x, entry.y, entry.plane);

                // Register the canonical name
                coords.put(canonicalKey, wp);
                canonicals.add(canonicalKey);

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
                    canonicals.size(), coords.size());
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
        this.aliasToCanonical = Collections.unmodifiableMap(aliasMap);
        this.canonicalNames = Collections.unmodifiableSet(canonicals);
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
     * Does not include alias names.
     */
    public Set<String> getAllCanonicalNames()
    {
        return canonicalNames;
    }

    /**
     * JSON deserialization model for a single location entry.
     * Only {@code x}/{@code y}/{@code plane} are required; {@code aliases} is optional.
     * Any other fields in the JSON (quests, skills, suggestedItems) are ignored.
     */
    private static class CoordEntry
    {
        int x;
        int y;
        int plane;

        /** Alternative names that resolve to this entry. */
        String[] aliases;
    }
}
