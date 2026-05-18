/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Header component showing the monster name, image, and total kill counter
 * at the top of the task detail view.
 *
 * <p>The KC label is a real Swing component so it supports mouse-over tooltips
 * and is always visible (even at zero), unlike a pixel-painted badge.
 */
public class Header extends JPanel
{
    private final JLabel titleLabel = new JLabel();
    private final JLabel imageLabel = new JLabel();
    private final JLabel kcLabel   = new JLabel();

    public Header()
    {
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        titleLabel.setForeground(ColorScheme.BRAND_ORANGE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

        kcLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
        kcLabel.setForeground(Color.WHITE);
        kcLabel.setBorder(new EmptyBorder(0, 2, 0, 0));
        kcLabel.setToolTipText("Total kill count for this monster");

        // KC row: left-aligned so it sits at the bottom-left beneath the image
        JPanel kcRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 1));
        kcRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        kcRow.add(kcLabel);

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setLayout(new BorderLayout(0, 4));
        add(titleLabel, BorderLayout.NORTH);
        add(imageLabel, BorderLayout.CENTER);
        add(kcRow,      BorderLayout.SOUTH);
    }

    public void update(String title, ImageIcon icon)
    {
        update(title, icon, 0);
    }

    /**
     * Updates the header with the monster name, image, and total kill count.
     * The KC label is always rendered (shows "KC: 0" when no kills are tracked yet).
     */
    public void update(String title, ImageIcon icon, int kc)
    {
        titleLabel.setText(title);
        imageLabel.setIcon(icon);
        kcLabel.setText("KC: " + String.format("%,d", kc));
    }
}
