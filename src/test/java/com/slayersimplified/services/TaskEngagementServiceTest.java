/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Exercises the overlay engagement gate state machine: arm shows the overlays
 * for a fixed window, combat locks them on, the window expires without combat,
 * and combat re-engages from idle.
 */
public class TaskEngagementServiceTest
{
    private TaskEngagementService engagement;
    private AtomicLong now;

    @Before
    public void setUp()
    {
        engagement = new TaskEngagementService();
        now = new AtomicLong(0L);
        engagement.setClock(now::get);
    }

    @Test
    public void hiddenByDefault()
    {
        Assert.assertFalse(engagement.shouldShowOverlays());
    }

    @Test
    public void armShowsOverlaysImmediately()
    {
        engagement.arm();
        Assert.assertTrue(engagement.shouldShowOverlays());
    }

    @Test
    public void armWindowExpiresWithoutCombat()
    {
        engagement.arm();

        // Just before the window closes, still visible.
        now.set(TaskEngagementService.ARM_WINDOW_MS - 1);
        engagement.tick();
        Assert.assertTrue(engagement.shouldShowOverlays());

        // At/after the window, hidden.
        now.set(TaskEngagementService.ARM_WINDOW_MS);
        engagement.tick();
        Assert.assertFalse(engagement.shouldShowOverlays());
    }

    @Test
    public void combatDuringWindowLocksOverlaysOnPastExpiry()
    {
        engagement.arm();
        engagement.onCombat();

        // Window elapses, but combat already engaged — stays visible.
        now.set(TaskEngagementService.ARM_WINDOW_MS * 10);
        engagement.tick();
        Assert.assertTrue(engagement.shouldShowOverlays());
    }

    @Test
    public void combatReEngagesFromIdle()
    {
        engagement.arm();
        now.set(TaskEngagementService.ARM_WINDOW_MS);
        engagement.tick();
        Assert.assertFalse("precondition: window expired", engagement.shouldShowOverlays());

        engagement.onCombat();
        Assert.assertTrue(engagement.shouldShowOverlays());
    }

    @Test
    public void resetHidesOverlays()
    {
        engagement.arm();
        engagement.onCombat();
        Assert.assertTrue(engagement.shouldShowOverlays());

        engagement.reset();
        Assert.assertFalse(engagement.shouldShowOverlays());
    }
}
