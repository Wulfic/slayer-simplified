/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.StreakFillerMaster;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.Color;

/**
 * Plugin configuration for Slayer Simplified. Exposes settings in
 * RuneLite's plugin configuration panel.
 */
@ConfigGroup(SlayerSimplifiedConfig.CONFIG_GROUP)
public interface SlayerSimplifiedConfig extends Config
{
    String CONFIG_GROUP = "slayersimplified";

    @ConfigItem(
            keyName = "preferredMaster",
            name = "Preferred Slayer Master",
            description = "The slayer master to navigate to when you have no active task",
            position = 1,
            hidden = true
    )
    default SlayerMaster preferredMaster()
    {
        return SlayerMaster.DURADEL;
    }

    @ConfigItem(keyName = "preferredMaster", name = "", description = "")
    void setPreferredMaster(SlayerMaster master);

    @ConfigItem(
            keyName = "highlightTarget",
            name = "Highlight Target",
            description = "Draw an outline around your current slayer task NPCs",
            position = 2,
            hidden = true
    )
    default boolean highlightTarget()
    {
        return true;
    }

    @ConfigItem(keyName = "highlightTarget", name = "", description = "")
    void setHighlightTarget(boolean value);

    @Alpha
    @ConfigItem(
            keyName = "highlightColor",
            name = "Highlight Color",
            description = "Color of the target NPC outline",
            position = 3,
            hidden = true
    )
    default Color highlightColor()
    {
        return Color.RED;
    }

    @ConfigItem(keyName = "highlightColor", name = "", description = "")
    void setHighlightColor(Color color);

    @ConfigItem(
            keyName = "autoNavigate",
            name = "Auto Navigate (!task)",
            description = "Automatically navigate to your slayer task when typing !task or receiving a new assignment",
            position = 4,
            hidden = true
    )
    default boolean autoNavigate()
    {
        return true;
    }

    @ConfigItem(keyName = "autoNavigate", name = "", description = "")
    void setAutoNavigate(boolean value);

    @ConfigItem(
            keyName = "debugCoordinates",
            name = "Location Debug",
            description = "Show an overlay with your current coordinates and nav target. Also enables the A DEBUG TASK test monster and debug nav info in the Locations tab.",
            position = 5,
            hidden = true
    )
    default boolean debugCoordinates()
    {
        return false;
    }

    @ConfigItem(keyName = "debugCoordinates", name = "", description = "")
    void setDebugCoordinates(boolean value);

    @ConfigItem(
            keyName = "remindSlayerCape",
            name = "Remind: Slayer Cape (99)",
            description = "Show a reminder to bring your Slayer cape when you get a new task (only when you have 99 Slayer)",
            position = 6,
            hidden = true
    )
    default boolean remindSlayerCape()
    {
        return false;
    }

    @ConfigItem(keyName = "remindSlayerCape", name = "", description = "")
    void setRemindSlayerCape(boolean value);

    @ConfigItem(
            keyName = "showReminderOverlay",
            name = "Show Task Reminder Overlay",
            description = "Show the on-screen overlay with required items, suggested items, and your notes while on a slayer task",
            position = 7,
            hidden = true
    )
    default boolean showReminderOverlay()
    {
        return true;
    }

    @ConfigItem(keyName = "showReminderOverlay", name = "", description = "")
    void setShowReminderOverlay(boolean value);

    @ConfigItem(
            keyName = "streakOptimizerEnabled",
            name = "Streak Point Optimizer",
            description = "Recommend the optimal Slayer master each task to maximise reward points (Turael boosting). "
                    + "When enabled, this overrides the Preferred Master setting.",
            position = 8,
            hidden = true
    )
    default boolean streakOptimizerEnabled()
    {
        return false;
    }

    @ConfigItem(keyName = "streakOptimizerEnabled", name = "", description = "")
    void setStreakOptimizerEnabled(boolean value);

    @ConfigItem(
            keyName = "streakFillerMaster",
            name = "Streak Filler Master",
            description = "Which master to use for non-milestone (filler) tasks when the Streak Point Optimizer is enabled. "
                    + "Turael / Spria give 0 points per filler but have the shortest tasks (classic Turael boosting). "
                    + "Mazchna gives 6 points per filler but tasks take longer (Mazchna boosting).",
            position = 9,
            hidden = true
    )
    default StreakFillerMaster streakFillerMaster()
    {
        return StreakFillerMaster.TURAEL;
    }

    @ConfigItem(keyName = "streakFillerMaster", name = "", description = "")
    void setStreakFillerMaster(StreakFillerMaster value);

    @ConfigItem(
            keyName = "showNonSlayerEnemies",
            name = "Show non-slayer enemies",
            description = "Show non-slayer enemies in the task browser (Work in progress)",
            position = 10,
            hidden = true
    )
    default boolean showNonSlayerEnemies()
    {
        return false;
    }

    @ConfigItem(keyName = "showNonSlayerEnemies", name = "", description = "")
    void setShowNonSlayerEnemies(boolean value);

    @ConfigItem(
            keyName = "tileNotes",
            name = "TileNotes",
            description = "Highlight each known training-spot tile for your current task in the game scene, "
                    + "showing the location name above the tile. Only tiles within your loaded area are shown.",
            position = 11,
            hidden = true
    )
    default boolean tileNotes()
    {
        return false;
    }

    @ConfigItem(keyName = "tileNotes", name = "", description = "")
    void setTileNotes(boolean value);

    // Hidden config keys used to persist internal state across sessions

    @ConfigItem(
            keyName = "currentTaskName",
            name = "",
            description = "",
            hidden = true
    )
    default String currentTaskName()
    {
        return "";
    }

    @ConfigItem(
            keyName = "currentTaskName",
            name = "",
            description = ""
    )
    void setCurrentTaskName(String taskName);

    @ConfigItem(
            keyName = "currentTaskTotal",
            name = "",
            description = "",
            hidden = true
    )
    default int currentTaskTotal()
    {
        return 0;
    }

    @ConfigItem(
            keyName = "currentTaskTotal",
            name = "",
            description = ""
    )
    void setCurrentTaskTotal(int total);
}
