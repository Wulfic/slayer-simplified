/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

/**
 * Enum of all Slayer masters with their overworld WorldPoint coordinates.
 * Used for "navigate to master" when the player has no active task.
 *
 * <p>{@code basePoints} is the number of Slayer reward points awarded per
 * normal (non-milestone) completed task for each master.  Milestone tasks
 * (10th, 50th, 100th, 250th, 1,000th) award a multiplied value:
 * 5x / 15x / 25x / 35x / 50x respectively.</p>
 */
@Getter
@RequiredArgsConstructor
public enum SlayerMaster
{
    TURAEL("Turael",           new WorldPoint(2931, 3536, 0),  0),
    MAZCHNA("Mazchna",         new WorldPoint(3510, 3507, 0),  6),
    VANNAKA("Vannaka",         new WorldPoint(3145, 9914, 0),  8),
    CHAELDAR("Chaeldar",       new WorldPoint(2445, 4431, 0), 10),
    KONAR("Konar quo Maten",   new WorldPoint(1308, 3786, 0), 18),
    NIEVE("Nieve / Steve",     new WorldPoint(2432, 3424, 0), 12),
    DURADEL("Duradel",         new WorldPoint(2869, 2982, 0), 15),
    KRYSTILIA("Krystilia",     new WorldPoint(3109, 3514, 0), 25);

    private final String displayName;
    private final WorldPoint worldPoint;
    /** Slayer reward points awarded per completed task (non-milestone). */
    private final int basePoints;

    @Override
    public String toString()
    {
        return displayName;
    }

    /**
     * Finds the SlayerMaster whose display name starts with the given task master name string.
     * Handles mismatches like "Nieve" in task data vs "Nieve / Steve" as display name.
     *
     * @param name the master name string as it appears in tasks.json
     * @return the matching SlayerMaster, or null if not found
     */
    public static SlayerMaster fromTaskMasterName(String name)
    {
        for (SlayerMaster m : values())
        {
            if (m.displayName.startsWith(name))
            {
                return m;
            }
        }
        return null;
    }
}
