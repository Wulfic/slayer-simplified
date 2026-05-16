/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation;

import com.slayersimplified.domain.Task;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

/**
 * Custom list cell renderer for Slayer tasks in the search panel.
 * Displays the task name with hover highlighting.
 */
public class SlayerTaskRenderer extends JLabel implements ListCellRenderer<Task>
{
    private static int hoverIndex = -1;

    public void setHoverIndex(int index)
    {
        hoverIndex = index;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends Task> list,
            Task value,
            int index,
            boolean isSelected,
            boolean cellHasFocus)
    {
        setOpaque(true);
        setFont(FontManager.getRunescapeSmallFont().deriveFont(FontManager.getRunescapeSmallFont().getSize2D() + 4f));
        setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 42));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(0, 5, 0, 0)
        ));

        if (index == hoverIndex)
        {
            setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            setForeground(Color.WHITE);
        }
        else
        {
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setForeground(ColorScheme.TEXT_COLOR);
        }

        setText(value.name);
        return this;
    }
}
