/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import lombok.extern.slf4j.Slf4j;

import javax.inject.Singleton;
import java.util.function.LongSupplier;

/**
 * Gates the task overlays (target highlight, tile notes, task reminder) behind
 * an "engagement" state machine so they only show when the player is actually
 * working the task — not the instant a task is assigned.
 *
 * <p>Lifecycle:</p>
 * <ul>
 *   <li>{@link #arm()} — called when the player types {@code !task} or receives a
 *       new assignment. Overlays show immediately for a {@value #ARM_WINDOW_MS} ms
 *       window (the {@code ARMED} state).</li>
 *   <li>{@link #onCombat()} — called when a hit is exchanged with the correct
 *       task monster. Locks the overlays on ({@code ENGAGED}), cancelling the
 *       arm window. Re-engages the overlays even from {@code IDLE}.</li>
 *   <li>{@link #tick()} — called every game tick. If the arm window elapses with
 *       no combat, the overlays hide ({@code IDLE}).</li>
 *   <li>{@link #reset()} — called on task completion / no task. Hides overlays.</li>
 * </ul>
 *
 * <p>All mutation happens on the client thread (event-bus handlers and overlay
 * rendering). The state is {@code volatile} purely as defensive belt-and-braces.</p>
 */
@Slf4j
@Singleton
public class TaskEngagementService
{
    /** How long after {@link #arm()} the overlays stay visible without combat. */
    static final long ARM_WINDOW_MS = 120_000L;

    private enum State
    {
        /** Overlays hidden — no recent !task and no combat with the task monster. */
        IDLE,
        /** Overlays shown; waiting for combat before the arm window expires. */
        ARMED,
        /** Overlays locked on — combat with the correct monster was detected. */
        ENGAGED
    }

    private volatile State state = State.IDLE;

    /** Absolute time (epoch millis) at which an {@code ARMED} window expires. */
    private volatile long armedUntilMillis = 0L;

    /** Time source. Overridable in tests so the arm window can be expired deterministically. */
    private volatile LongSupplier clock = System::currentTimeMillis;

    /** Package-private test seam: swap the time source. */
    void setClock(LongSupplier clock)
    {
        this.clock = clock;
    }

    /**
     * Starts (or restarts) the arm window. Called when the player types
     * {@code !task} or is assigned a new task. Overlays become visible
     * immediately and stay visible until combat engages them or the window
     * expires.
     */
    public void arm()
    {
        state = State.ARMED;
        armedUntilMillis = clock.getAsLong() + ARM_WINDOW_MS;
        log.debug("Overlay gate armed for {} ms", ARM_WINDOW_MS);
    }

    /**
     * Signals that a hit was exchanged with the correct task monster. Locks the
     * overlays on and cancels the arm window. Safe to call every hit — it is a
     * no-op once already engaged.
     */
    public void onCombat()
    {
        if (state != State.ENGAGED)
        {
            log.debug("Overlay gate engaged (combat with task monster)");
        }
        state = State.ENGAGED;
    }

    /**
     * Expires the arm window if it has elapsed without combat. Called once per
     * game tick from the plugin.
     */
    public void tick()
    {
        if (state == State.ARMED && clock.getAsLong() >= armedUntilMillis)
        {
            state = State.IDLE;
            log.debug("Overlay gate arm window expired with no combat — hiding overlays");
        }
    }

    /**
     * Hides the overlays and clears any engagement. Called when the task is
     * completed or the player has no task.
     */
    public void reset()
    {
        if (state != State.IDLE)
        {
            log.debug("Overlay gate reset");
        }
        state = State.IDLE;
    }

    /** Returns {@code true} while the task overlays should be visible. */
    public boolean shouldShowOverlays()
    {
        return state != State.IDLE;
    }
}
