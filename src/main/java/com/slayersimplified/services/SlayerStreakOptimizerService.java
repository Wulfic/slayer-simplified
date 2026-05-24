/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.StreakFillerMaster;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Recommends the optimal Slayer master to use each task in order to maximise
 * reward points over a streak (a.k.a. "Turael boosting" or "Mazchna boosting").
 *
 * <h2>Strategy</h2>
 * <ul>
 *   <li><strong>Filler tasks</strong> (not landing on a milestone): use the
 *       player-selected {@link StreakFillerMaster}.
 *       <ul>
 *         <li>{@code TURAEL} / {@code SPRIA} — 0 pts per task, shortest tasks
 *             (fastest streak progression).</li>
 *         <li>{@code MAZCHNA} — 6 pts per task, longer tasks (more points but
 *             slower per hour). Convenient in Morytania/Kourend.</li>
 *       </ul>
 *   </li>
 *   <li><strong>Milestone tasks</strong> (every 10th / 50th / 100th / 250th /
 *       1,000th completed assignment): use the <strong>highest-eligible
 *       master</strong> to collect the multiplied bonus.</li>
 * </ul>
 *
 * <h2>Milestone multipliers (per OSRS Wiki — Slayer reward point)</h2>
 * <pre>
 *   10th  →  5x   50th  → 15x   100th → 25x   250th → 35x   1,000th → 50x
 * </pre>
 *
 * <h2>Milestone master selection</h2>
 * Highest base-points master the player qualifies for, with Krystilia and the
 * filler masters excluded:
 * <ol>
 *   <li>Konar (18 pts) — combat &ge; 75</li>
 *   <li>Chaeldar (10 pts) — combat &ge; 70 and Lost City completed</li>
 *   <li>Vannaka (8 pts) — combat &ge; 40</li>
 *   <li>Mazchna (6 pts) — no requirements</li>
 * </ol>
 * Nieve (12 pts, cbt 85) and Duradel (15 pts, cbt 100) are both outclassed by
 * Konar (18 pts, cbt 75) — every player who qualifies for them already
 * qualifies for Konar.
 *
 * <p>Krystilia is intentionally excluded — Wilderness tasks maintain a
 * <em>separate</em> completion counter independent of the streak used for
 * milestone bonuses.</p>
 *
 * <p>{@link #refresh()} <strong>must</strong> be called on the client thread
 * before recommendations are needed. All other methods are thread-safe.</p>
 */
@Slf4j
@Singleton
public class SlayerStreakOptimizerService
{
    // Milestone boundaries in descending order (to match the highest first).
    private static final int[] MILESTONE_MULTIPLES   = {1000, 250, 100, 50, 10};
    private static final int[] MILESTONE_MULTIPLIERS = {  50,  35,  25, 15,  5};

    private final Client client;
    private final SlayerTaskTracker taskTracker;
    private final SlayerSimplifiedConfig config;

    // Player state — written on the client thread, read from any thread.
    private volatile int     combatLevel       = 0;
    private volatile boolean lostCityCompleted = false;

    @Inject
    public SlayerStreakOptimizerService(
            Client client,
            SlayerTaskTracker taskTracker,
            SlayerSimplifiedConfig config)
    {
        this.client      = client;
        this.taskTracker = taskTracker;
        this.config      = config;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Re-reads combat level and required quest states from the client.
     * <strong>Must be called on the client thread.</strong>
     */
    public void refresh()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }

        combatLevel = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getCombatLevel()
                : 0;

        try
        {
            lostCityCompleted = Quest.LOST_CITY.getState(client) == QuestState.FINISHED;
        }
        catch (Exception ex)
        {
            log.warn("Could not read Lost City quest state for streak optimizer", ex);
            lostCityCompleted = false;
        }

        log.debug("SlayerStreakOptimizerService refreshed: combat={} lostCity={}",
                combatLevel, lostCityCompleted);
    }

    /**
     * Returns the recommended Slayer master for the player's <em>next</em> task.
     *
     * <ul>
     *   <li>Milestone task → highest eligible master (max bonus points).</li>
     *   <li>Filler task → the configured {@link StreakFillerMaster}.</li>
     * </ul>
     */
    public SlayerMaster getRecommendedMaster()
    {
        int nextTask = getNextTaskNumber();
        return isMilestoneTask(nextTask)
                ? getHighestEligibleMilestoneMaster()
                : getFillerMaster();
    }

    /**
     * Returns a short human-readable explanation of the current recommendation,
     * suitable for a tooltip or sub-label in the plugin panel.
     */
    public String getRecommendationReason()
    {
        int nextTask           = getNextTaskNumber();
        SlayerMaster filler    = getFillerMaster();
        SlayerMaster milestone = getHighestEligibleMilestoneMaster();

        if (isMilestoneTask(nextTask))
        {
            int mult = getMilestoneMultiplier(nextTask);
            int pts  = milestone.getBasePoints() * mult;
            return "Task #" + nextTask + " — milestone! +" + pts
                    + " pts with " + milestone.getDisplayName();
        }

        int nextMilestone = getNextMilestone(nextTask);
        int fillersLeft   = nextMilestone - nextTask;
        String fillerName = filler.getDisplayName();
        // Mazchna fillers award points too — call that out so users understand
        // why they might pick the slower path.
        String fillerPts  = filler.getBasePoints() > 0
                ? " (+" + filler.getBasePoints() + " pts each)"
                : " (0 pts)";
        return "Task #" + nextTask + " — " + fillersLeft + " "
                + fillerName + " task" + (fillersLeft == 1 ? "" : "s") + fillerPts
                + " until milestone #" + nextMilestone + " (" + milestone.getDisplayName() + ")";
    }

    /**
     * Returns the 1-based task number the player will receive next
     * (completed-streak + 1). When the streak is unknown (e.g. RuneLite slayer
     * plugin has never written its config), defaults to {@code 1} — task #1
     * is always a filler, so the recommendation is still useful.
     */
    public int getNextTaskNumber()
    {
        int streak = taskTracker.getTaskStreak();
        return Math.max(1, streak + 1);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Returns the user-selected filler master, falling back to Turael if unset. */
    private SlayerMaster getFillerMaster()
    {
        StreakFillerMaster choice = config.streakFillerMaster();
        return choice != null ? choice.getMaster() : SlayerMaster.TURAEL;
    }

    /** @return {@code true} when {@code taskNumber} falls on any milestone boundary. */
    private boolean isMilestoneTask(int taskNumber)
    {
        return taskNumber > 0 && taskNumber % 10 == 0;
    }

    /**
     * Returns the bonus multiplier for {@code taskNumber}.
     * Returns {@code 1} for non-milestone task numbers.
     */
    private int getMilestoneMultiplier(int taskNumber)
    {
        for (int i = 0; i < MILESTONE_MULTIPLES.length; i++)
        {
            if (taskNumber % MILESTONE_MULTIPLES[i] == 0)
            {
                return MILESTONE_MULTIPLIERS[i];
            }
        }
        return 1;
    }

    /** Returns the next task number that is a multiple of 10, at or after {@code fromTask}. */
    private int getNextMilestone(int fromTask)
    {
        return ((fromTask + 9) / 10) * 10;
    }

    /**
     * Picks the highest-points master the player currently qualifies for,
     * excluding Krystilia and the zero-point filler masters.
     */
    private SlayerMaster getHighestEligibleMilestoneMaster()
    {
        if (combatLevel >= 75)
        {
            return SlayerMaster.KONAR;
        }
        if (combatLevel >= 70 && lostCityCompleted)
        {
            return SlayerMaster.CHAELDAR;
        }
        if (combatLevel >= 40)
        {
            return SlayerMaster.VANNAKA;
        }
        return SlayerMaster.MAZCHNA;
    }
}
