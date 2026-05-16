/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Header component showing the monster name and image at the top of the
 * task detail view.
 */
public class Header extends JLabel
{
    public Header()
    {
        setFont(this.getFont().deriveFont(Font.BOLD, 18f));
        setForeground(ColorScheme.BRAND_ORANGE);
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalTextPosition(SwingConstants.TOP);
        setHorizontalTextPosition(SwingConstants.CENTER);
        setIconTextGap(10);
    }

    public void update(String title, ImageIcon icon)
    {
        update(title, icon, 0);
    }

    /**
     * Updates the header, optionally drawing a "KC: N" badge in the
     * bottom-left corner of the monster image when kc is greater than zero.
     */
    public void update(String title, ImageIcon icon, int kc)
    {
        setText(title);
        if (icon == null || kc <= 0)
        {
            setIcon(icon);
            return;
        }
        Image src = icon.getImage();
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        if (w <= 0 || h <= 0)
        {
            setIcon(icon);
            return;
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, null);

        String badge = "KC: " + kc;
        g.setFont(new Font("Arial", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        int textW = fm.stringWidth(badge) + 8;
        int textH = fm.getAscent() + fm.getDescent() + 4;
        int bx = 4;
        int by = h - textH - 4;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(bx, by, textW, textH, 4, 4);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.drawString(badge, bx + 4, by + fm.getAscent() + 2);
        g.dispose();

        setIcon(new ImageIcon(img));
    }
}
