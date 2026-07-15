/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.Gson;
import com.slayersimplified.domain.Task;
import net.runelite.client.util.ImageUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the image-loading fallback in {@link TaskServiceImpl}.
 *
 * Several bundled tasks have no monster PNG (newer content and level variants).
 * Loading those through {@link ImageUtil#loadImageResource} makes it log at WARN
 * before throwing, which polluted user logs on every startup.  The service now
 * checks the classpath first and quietly substitutes the placeholder, so these
 * expected misses must produce no warnings.
 */
public class TaskServiceImageTest
{
    /** Collects log events so the test can assert on what was emitted. */
    private static final class CapturingAppender extends AppenderBase<ILoggingEvent>
    {
        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event)
        {
            events.add(event);
        }
    }

    private static TaskServiceImpl newService()
    {
        return new TaskServiceImpl(
                new Gson(),
                "/data/tasks",
                "/data/non_slayer_tasks",
                "/data/boss_tasks",
                "/data/animal_tasks",
                "https://oldschool.runescape.wiki/w/",
                "/images/monsters/");
    }

    @Test
    public void loadingTasksLogsNoImageWarnings()
    {
        Logger imageUtilLogger = (Logger) LoggerFactory.getLogger(ImageUtil.class);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        imageUtilLogger.addAppender(appender);

        try
        {
            newService();
        }
        finally
        {
            imageUtilLogger.detachAppender(appender);
            appender.stop();
        }

        List<String> warnings = new ArrayList<>();
        for (ILoggingEvent event : appender.events)
        {
            if (event.getLevel().isGreaterOrEqual(Level.WARN))
            {
                warnings.add(event.getFormattedMessage());
            }
        }

        Assert.assertEquals(
                "Loading tasks must not log image warnings, but got: " + warnings,
                0,
                warnings.size());
    }

    @Test
    public void everyTaskHasAnImage()
    {
        List<String> missing = new ArrayList<>();
        for (Task task : newService().getAll())
        {
            if (task.image == null)
            {
                missing.add(task.name);
            }
        }

        Assert.assertEquals("Every task must resolve to an image: " + missing, 0, missing.size());
    }

    @Test
    public void tasksWithoutABundledImageShareThePlaceholder()
    {
        TaskServiceImpl service = newService();

        // Neither of these ships a PNG, so both must fall back to the single
        // shared placeholder instance rather than allocating per task.
        Task hueycoatl = service.get("Hueycoatl");
        Task swampSnake = service.get("Swamp snake");

        Assert.assertNotNull("Hueycoatl task must load", hueycoatl);
        Assert.assertNotNull("Swamp snake task must load", swampSnake);
        Assert.assertSame(
                "Tasks with no bundled image must share the placeholder instance",
                hueycoatl.image,
                swampSnake.image);

        // A task that does ship a PNG must get its own image, not the placeholder.
        Task abyssalDemon = service.get("Abyssal demon");
        Assert.assertNotNull("Abyssal demon task must load", abyssalDemon);
        Assert.assertNotSame(
                "Tasks with a bundled image must not get the placeholder",
                hueycoatl.image,
                abyssalDemon.image);
    }

    @Test
    public void placeholderIsReusedAcrossServiceInstances()
    {
        // The placeholder is static, so a second service must hand back the same
        // instance; a per-instance placeholder would leak an image per reload.
        BufferedImage first = newService().get("Hueycoatl").image;
        BufferedImage second = newService().get("Hueycoatl").image;
        Assert.assertSame("Placeholder must be shared across instances", first, second);
    }
}
