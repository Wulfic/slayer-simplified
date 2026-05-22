/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.slayersimplified.domain.SlayerMaster;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Recommends the optimal Slayer master to use each task in order to maximise
 * reward points over a streak ("Turael boosting" method).
 *
 * <p>Strategy:</p>
 * <ul>
 *   <li>Filler tasks (not landing on a milestone): use <strong>Turael</strong>
 *       — his tasks are trivially fast, contributing 0 points but building
 *       the streak counter toward the next milestone as quickly as possible.</li>
 *   <li>Milestone tasks (every 10th, 50th, 100th, 250th, or 1,000th completed
 *       assignment): use the <strong>highest-eligible master</strong> to collect
 *       the disproportionate bonus points.<br>
 *       Point values per task: Konar 18 (cbt 75) &gt; Chaeldar 10 (cbt 70 +
 *       Lost City) &gt; Vannaka 8 (cbt 40) &gt; Mazchna 6.</li>
 * </ul>
 *
 * <p>Konar beats both Duradel (15 pts, cbt 100) and Nieve (12 pts, cbt 85)
 * because she gives more points and has a lower combat requirement (75), so
 * any player who could use Nieve or Duradel already qualifies for Konar.</p>
 *
 * <p>Krystilia is intentionally excluded — Wilderness tasks maintain a
 * <em>separate</em> completion counter that is independent of the normal
 * streak used for milestone bonuses.</p>
 *
 * <p>{@link #refresh()} <strong>must</strong> be called on the client thread
 * before recommendations are needed. All other methods are thread-safe.</p>
 */
@Slf4j
@Singleton
public class SlayerStreakOptimizerService
{
    // Milestone boundaries in descending order (to match the highest first).
    private static final int[] MILESTONE_MULTIPLES  = {1000, 250, 100, 50, 10};
    private static final int[] MILESTONE_MULTIPLIERS = {  50,  35,  25, 15,  5};

    private final Client client;
    private final SlayerTaskTracker taskTracker;

    // Player state — written on the client thread, read from any thread.
    private volatile int     combatLevel        = 0;
    private volatile boolean lostCityCompleted  = false;

    @Inject
    public SlayerStreakOptimizerService(Client client, SlayerTaskTracker taskTracker)
    {
        this.client      = client;
        this.taskTracker = taskTracker;
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
     * Returns the recommended Slayer master for the player's <em>next</em> task,
     * based on their current streak and eligibility.
     *
     * <ul>
     *   <li>Milestone task → highest eligible master for maximum bonus points.</li>
     *   <li>Filler task → {@link SlayerMaster#TURAEL} for the fastest progression.</li>
     * </ul>
     *
     * Falls back to {@link SlayerMaster#TURAEL} when the streak is unknown
     * or the player is not logged in.
     */
    public SlayerMaster getRecommendedMaster()
    {
        int nextTask = getNextTaskNumber();
        if (nextTask <= 0)
        {
            return SlayerMaster.TURAEL;
        }
        return isMilestoneTask(nextTask)
                ? getHighestEligibleMilestonemaster()
                : SlayerMaster.TURAEL;
    }

    /**
     * Returns a short human-readable explanation of the current recommendation,
     * suitable for a tooltip or sub-label in the plugin panel.
     */
    public String getRecommendationReason()
    {
        int streak   = taskTracker.getTaskStreak();
        int nextTask = streak + 1;

        if (streak <= 0)
        {
            return "Streak unknown — go to Turael";
        }

        if (isMilestoneTask(nextTask))
        {
            int mult   = getMilestoneMultiplier(nextTask);
            SlayerMaster master = getHighestEligibleMilestonemaster();
            int pts    = master.getBasePoints() * mult;
            return "Task #" + nextTask + " — milestone! +" + pts + " pts with " + master.getDisplayName();
        }
        else
        {
            int nextMilestone = getNextMilestone(nextTask);
            int filler        = nextMilestone - nextTask;
            SlayerMaster milestoneMaster = getHighestEligibleMilestonemaster();
            return "Task #" + nextTask + " — " + filler + " Turael task(s) until #"
                    + nextMilestone + " (" + milestoneMaster.getDisplayName() + ")";
        }
    }

    /**
     * Returns the 1-based task number the player will receive next
     * (current streak + 1), or {@code 0} if the streak is unknown.
     */
    public int getNextTaskNumber()
    {
        int streak = taskTracker.getTaskStreak();
        return streak > 0 ? streak + 1 : 0;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

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
     * excluding Krystilia and Turael/Spria.
     *
     * <p>Priority (highest base points first):</p>
     * <ol>
     *   <li>Konar (18 pts) — combat ≥ 75</li>
     *   <li>Chaeldar (10 pts) — combat ≥ 70 and Lost City completed</li>
     *   <li>Vannaka (8 pts) — combat ≥ 40</li>
     *   <li>Mazchna (6 pts) — no requirements</li>
     * </ol>
     *
     * Note: Nieve (12 pts, cbt 85) and Duradel (15 pts, cbt 100) are both
     * outclassed by Konar (18 pts, cbt 75).  Any player eligible for Nieve
     * or Duradel is also eligible for Konar, which gives more points.
     */
    private SlayerMaster getHighestEligibleMilestonemaster()
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
