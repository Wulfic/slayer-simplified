/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.Task;
import com.slayersimplified.domain.TileNoteEntry;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves tile and object markers for the current slayer task so the
 * {@link com.slayersimplified.presentation.TileNoteOverlay} can render them
 * in the game scene.
 *
 * <p>Two sources are merged for the active task:
 * <ol>
 *   <li><b>Location-derived tiles</b> — coarse training-spot coordinates read
 *       from {@code location_coordinates.json} via {@link LocationCoordinateService}.</li>
 *   <li><b>Explicit tile/object entries</b> — fine-grained markers loaded from
 *       {@code tile_notes.json} (safespots, specific objects, etc.).</li>
 * </ol>
 *
 * <p>Call {@link #updateForTask(String)} whenever the active task changes.
 */
@Slf4j
@Singleton
public class TileNoteService
{
    private final TaskService taskService;
    private final LocationCoordinateService locationCoordinateService;

    /**
     * All explicit tile/object entries keyed by lower-case task name.
     * Loaded once at construction from {@code tile_notes.json}.
     */
    private final Map<String, List<TileNoteEntry>> explicitEntries;

    /** Location label → WorldPoint for the current task's markers. */
    private volatile Map<String, WorldPoint> currentTaskTiles = Collections.emptyMap();

    /** Explicit entries for the current task (including object markers). */
    private volatile List<TileNoteEntry> currentExplicitEntries = Collections.emptyList();

    /**
     * True when the current task belongs to the Non-Slayer Enemies / Animals / Bosses
     * pseudo-masters. Used by {@link com.slayersimplified.presentation.TileNoteOverlay}
     * to honour the {@code showNonSlayerEnemies} config toggle.
     */
    private volatile boolean currentTaskIsNonSlayer = false;

    @Inject
    public TileNoteService(
            TaskService taskService,
            LocationCoordinateService locationCoordinateService,
            Gson gson,
            @Named("tileNotesDataPath") String tileNotesDataPath)
    {
        this.taskService = taskService;
        this.locationCoordinateService = locationCoordinateService;
        this.explicitEntries = loadExplicitEntries(gson, tileNotesDataPath);
    }

    // -------------------------------------------------------------------------

    /**
     * Rebuilds the tile/object sets for the given task name by merging both
     * location-derived coordinates and explicit {@code tile_notes.json} entries.
     * Safe to call from any thread.
     */
    public void updateForTask(String taskName)
    {
        if (taskName == null || taskName.isEmpty())
        {
            currentTaskTiles = Collections.emptyMap();
            currentExplicitEntries = Collections.emptyList();
            return;
        }

        // --- location-derived tiles ---
        Map<String, WorldPoint> tiles = new LinkedHashMap<>();
        Task task = taskService.get(taskName);
        if (task != null && task.variantLocations != null)
        {
            for (String[] locations : task.variantLocations.values())
            {
                if (locations == null)
                {
                    continue;
                }
                for (String locationName : locations)
                {
                    if (locationName == null || tiles.containsKey(locationName))
                    {
                        continue;
                    }
                    WorldPoint wp = locationCoordinateService.getCoordinates(locationName);
                    if (wp != null)
                    {
                        tiles.put(locationName, wp);
                    }
                }
            }
        }

        // --- explicit tile entries from tile_notes.json ---
        List<TileNoteEntry> explicit = explicitEntries.getOrDefault(
                taskName.toLowerCase(), Collections.emptyList());
        for (TileNoteEntry entry : explicit)
        {
            if ("tile".equalsIgnoreCase(entry.type) && entry.label != null)
            {
                WorldPoint wp = new WorldPoint(entry.x, entry.y, entry.plane);
                tiles.put(entry.label, wp);
            }
        }

        currentTaskTiles = Collections.unmodifiableMap(tiles);
        currentExplicitEntries = Collections.unmodifiableList(explicit);
        currentTaskIsNonSlayer = isNonSlayerTask(task);
        log.debug("TileNoteService: {} tiles, {} explicit entries for task '{}' (nonSlayer={})",
                tiles.size(), explicit.size(), taskName, currentTaskIsNonSlayer);
    }

    /**
     * Returns the current map of location label → WorldPoint to mark.
     * Includes both location-derived spots and explicit tile entries.
     */
    public Map<String, WorldPoint> getCurrentTaskTiles()
    {
        return currentTaskTiles;
    }

    /**
     * Returns the raw explicit entries for the current task (includes object
     * markers and any additional metadata such as {@code note}).
     */
    public List<TileNoteEntry> getCurrentExplicitEntries()
    {
        return currentExplicitEntries;
    }

    /**
     * Returns {@code true} if the current task belongs to the Non-Slayer Enemies,
     * Animals, or Bosses pseudo-master groups.
     */
    public boolean isCurrentTaskNonSlayer()
    {
        return currentTaskIsNonSlayer;
    }

    /** Clears all tracked tiles (e.g. on plugin shutdown or task cleared). */
    public void clear()
    {
        currentTaskTiles = Collections.emptyMap();
        currentExplicitEntries = Collections.emptyList();
        currentTaskIsNonSlayer = false;
    }

    private static boolean isNonSlayerTask(Task task)
    {
        if (task == null || task.masters == null)
        {
            return false;
        }
        for (String master : task.masters)
        {
            if (SlayerMaster.NON_SLAYER_ENEMIES.getDisplayName().equals(master)
                    || SlayerMaster.ANIMALS.getDisplayName().equals(master)
                    || SlayerMaster.BOSSES.getDisplayName().equals(master))
            {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------

    private Map<String, List<TileNoteEntry>> loadExplicitEntries(Gson gson, String path)
    {
        InputStream is = getClass().getResourceAsStream(path);
        if (is == null)
        {
            log.warn("tile_notes.json not found at {}", path);
            return Collections.emptyMap();
        }

        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
        {
            Type type = new TypeToken<Map<String, List<TileNoteEntry>>>() {}.getType();
            Map<String, List<TileNoteEntry>> raw = gson.fromJson(reader, type);
            if (raw == null)
            {
                return Collections.emptyMap();
            }
            // Normalise keys to lower-case for case-insensitive lookup
            Map<String, List<TileNoteEntry>> normalised = new LinkedHashMap<>();
            raw.forEach((k, v) ->
            {
                if (k != null && !k.startsWith("_"))
                {
                    normalised.put(k.toLowerCase(), v);
                }
            });
            log.debug("Loaded tile_notes.json: {} task entries", normalised.size());
            return Collections.unmodifiableMap(normalised);
        }
        catch (JsonSyntaxException | IOException e)
        {
            log.error("Failed to load tile_notes.json from {}", path, e);
            return Collections.emptyMap();
        }
    }
}
