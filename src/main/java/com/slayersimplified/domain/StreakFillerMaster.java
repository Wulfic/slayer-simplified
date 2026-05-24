/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The Slayer master a player wants to receive filler (non-milestone) tasks
 * from while the Streak Point Optimizer is enabled.
 *
 * <p>Three "filler paths" are supported. The two zero-point options
 * ({@link #TURAEL} and {@link #SPRIA}) implement classic
 * <em>Turael boosting</em>: their tasks are short and award 0 points, so
 * fillers race to the next 10/50/100/250/1,000 milestone where the bonus
 * master is used.</p>
 *
 * <p>{@link #MAZCHNA} implements the alternative <em>Mazchna boosting</em>
 * path. Mazchna gives 6 reward points per filler task (post-Aug-2025 buff)
 * which yields more points per completed task than Turael boosting at the
 * cost of longer task length. Players based in Morytania/Kourend often
 * prefer this path for its convenience.</p>
 *
 * <p>Krystilia is deliberately excluded — Wilderness tasks track a
 * <em>separate</em> streak counter that never contributes to the bonus
 * milestones used by the optimizer.</p>
 */
@Getter
@RequiredArgsConstructor
public enum StreakFillerMaster
{
    TURAEL("Turael",   SlayerMaster.TURAEL),
    SPRIA("Spria",     SlayerMaster.SPRIA),
    MAZCHNA("Mazchna", SlayerMaster.MAZCHNA);

    private final String displayName;
    private final SlayerMaster master;

    @Override
    public String toString()
    {
        return displayName;
    }
}
