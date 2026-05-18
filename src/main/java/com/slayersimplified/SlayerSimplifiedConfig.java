/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.slayersimplified.domain.SlayerMaster;
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
            description = "Show an overlay with your current coordinates and nav target. Also enables the AAAAA test monster and debug nav info in the Locations tab.",
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
