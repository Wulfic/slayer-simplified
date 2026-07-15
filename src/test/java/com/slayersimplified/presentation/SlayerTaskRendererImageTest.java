/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.Gson;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.TaskServiceImpl;
import net.runelite.client.util.ImageUtil;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the icon fallback in {@link SlayerTaskRenderer}.
 *
 * The renderer loads monster images independently of TaskServiceImpl, so
 * silencing the warning in the service alone still left the panel logging a
 * WARN per task with no bundled PNG — lazily, on every first render.
 */
public class SlayerTaskRendererImageTest
{
    private static final class CapturingAppender extends AppenderBase<ILoggingEvent>
    {
        private final List<ILoggingEvent> events = new ArrayList<>();

        @Override
        protected void append(ILoggingEvent event)
        {
            events.add(event);
        }
    }

    /**
     * Every renderer assertion lives in this one method on purpose.
     *
     * SlayerTaskRenderer caches icons in a static map, so only the first lookup
     * of a given task actually touches the classpath. Split across methods, a
     * warming call could land outside the appender and let a warning slip past
     * unseen, passing even against the unguarded loader. Reflection is not
     * allowed here, so the cache cannot be reset between tests; keeping the
     * first lookup inside the capture is what makes this test honest. No other
     * test may call SlayerTaskRenderer.
     */
    @Test
    public void renderingEveryTaskIconLogsNoWarningsAndFallsBackToPlaceholder()
    {
        TaskServiceImpl service = new TaskServiceImpl(
                new Gson(),
                "/data/tasks",
                "/data/non_slayer_tasks",
                "/data/boss_tasks",
                "/data/animal_tasks",
                "https://oldschool.runescape.wiki/w/",
                "/images/monsters/");

        Logger imageUtilLogger = (Logger) LoggerFactory.getLogger(ImageUtil.class);
        CapturingAppender appender = new CapturingAppender();
        appender.start();
        imageUtilLogger.addAppender(appender);

        Icon hueycoatlIcon;
        Icon swampSnakeIcon;
        Icon abyssalDemonIcon;

        try
        {
            // Cold cache: resolve an icon for every task and variant, exactly as
            // the panel does when it first renders each row.
            for (Task task : service.getAll())
            {
                Assert.assertNotNull(
                        "Every task must resolve to an icon: " + task.name,
                        SlayerTaskRenderer.getMonsterIcon(task.name));

                if (task.variants != null)
                {
                    for (String variant : task.variants)
                    {
                        Assert.assertNotNull(
                                "Every variant must resolve to an icon: " + variant,
                                SlayerTaskRenderer.getVariantIcon(variant, task.name));
                    }
                }
            }

            hueycoatlIcon = SlayerTaskRenderer.getMonsterIcon("Hueycoatl");
            swampSnakeIcon = SlayerTaskRenderer.getMonsterIcon("Swamp snake");
            abyssalDemonIcon = SlayerTaskRenderer.getMonsterIcon("Abyssal demon");
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
                "Rendering task icons must not log image warnings, but got: " + warnings,
                0,
                warnings.size());

        // Tasks with no bundled PNG share the one placeholder icon.
        Assert.assertSame(
                "Tasks with no bundled image must share the placeholder icon",
                hueycoatlIcon,
                swampSnakeIcon);

        // A task that does ship a PNG must get a real icon instead.
        Assert.assertNotSame(
                "Tasks with a bundled image must not get the placeholder",
                hueycoatlIcon,
                abyssalDemonIcon);
    }
}
