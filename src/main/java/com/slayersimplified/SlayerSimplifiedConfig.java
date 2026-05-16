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
            position = 1
    )
    default SlayerMaster preferredMaster()
    {
        return SlayerMaster.DURADEL;
    }

    @ConfigItem(
            keyName = "highlightTarget",
            name = "Highlight Target",
            description = "Draw an outline around your current slayer task NPCs",
            position = 2
    )
    default boolean highlightTarget()
    {
        return true;
    }

    @Alpha
    @ConfigItem(
            keyName = "highlightColor",
            name = "Highlight Color",
            description = "Color of the target NPC outline",
            position = 3
    )
    default Color highlightColor()
    {
        return Color.RED;
    }

    @ConfigItem(
            keyName = "autoNavigate",
            name = "Auto Navigate (!task)",
            description = "Automatically navigate to your slayer task when typing !task or receiving a new assignment",
            position = 4
    )
    default boolean autoNavigate()
    {
        return true;
    }

    @ConfigItem(
            keyName = "debugCoordinates",
            name = "Debug Coordinates",
            description = "Show an overlay with your current WorldPoint coordinates. Useful for mapping new locations.",
            position = 5
    )
    default boolean debugCoordinates()
    {
        return false;
    }

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
}
