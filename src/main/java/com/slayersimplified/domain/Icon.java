/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

import com.slayersimplified.SlayerSimplifiedPlugin;
import net.runelite.client.util.ImageUtil;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.function.UnaryOperator;

/**
 * Icons used throughout the plugin UI (tab icons, navigation button, etc.).
 * Each icon references an image resource bundled with the plugin.
 */
public enum Icon
{
    COMBAT("/images/combat.png"),
    COMPASS("/images/compass.png"),
    INVENTORY("/images/inventory.png"),
    PLUGIN_ICON("/images/plugin_icon.png"),
    SLAYER_SKILL("/images/slayer_icon.png"),
    WIKI("/images/wiki.png"),
    LOOT("/images/loot.png"),
    NOTES("/images/notes.png"),
    ;

    private final String file;
    private volatile BufferedImage cachedImage;

    Icon(String file)
    {
        this.file = file;
    }

    public BufferedImage getImage()
    {
        if (cachedImage == null)
        {
            cachedImage = ImageUtil.loadImageResource(SlayerSimplifiedPlugin.class, file);
        }
        return cachedImage;
    }

    public ImageIcon getIcon()
    {
        return getIcon(UnaryOperator.identity());
    }

    public ImageIcon getIcon(@Nonnull UnaryOperator<BufferedImage> func)
    {
        BufferedImage img = func.apply(getImage());
        return new ImageIcon(img);
    }
}
