/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

/**
 * A single tile or game-object marker loaded from {@code tile_notes.json}.
 *
 * <ul>
 *   <li>When {@code type} is {@code "tile"}, the fields {@code x}, {@code y},
 *       and {@code plane} identify the specific world tile to mark.</li>
 *   <li>When {@code type} is {@code "object"}, {@code objectId} is the
 *       RuneScape game-object ID to highlight whenever it is in the scene.</li>
 * </ul>
 *
 * {@code label} is rendered above the tile/object in the game scene.
 * {@code note} is a longer description available for tooltips or info panels.
 */
public class TileNoteEntry
{
    /** {@code "tile"} or {@code "object"}. */
    public String type;

    // ---- tile fields ----
    public int x;
    public int y;
    public int plane;

    // ---- object fields ----
    public Integer objectId;

    // ---- shared ----
    public String label;
    public String note;
}
