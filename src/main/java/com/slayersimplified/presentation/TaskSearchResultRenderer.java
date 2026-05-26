/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation;

import com.slayersimplified.domain.TaskSearchResult;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * List-cell renderer for {@link TaskSearchResult} items in the search panel.
 * <p>
 * Shows the result's {@code displayName} (variant name or task name) and tries
 * to load a variant-specific image; if none exists the parent task's image is
 * used as a fallback (see {@link SlayerTaskRenderer#getVariantIcon}).
 */
public class TaskSearchResultRenderer extends JPanel implements ListCellRenderer<TaskSearchResult>
{
    private static final int ROW_HEIGHT = 42;
    private static final int ICON_SIZE  = 32;

    private static int hoverIndex = -1;

    private final JLabel nameLabel = new JLabel();
    private final JLabel iconLabel = new JLabel();

    public TaskSearchResultRenderer()
    {
        setLayout(new BorderLayout(4, 0));
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        iconLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
        add(nameLabel, BorderLayout.CENTER);
        add(iconLabel, BorderLayout.EAST);
    }

    public void setHoverIndex(int index)
    {
        hoverIndex = index;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<? extends TaskSearchResult> list,
            TaskSearchResult value,
            int index,
            boolean isSelected,
            boolean cellHasFocus)
    {
        setOpaque(true);
        setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, ROW_HEIGHT));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(0, 5, 0, 0)
        ));

        final Color bg = (index == hoverIndex)
                ? ColorScheme.DARKER_GRAY_HOVER_COLOR
                : ColorScheme.DARKER_GRAY_COLOR;
        setBackground(bg);

        nameLabel.setOpaque(false);
        nameLabel.setFont(FontManager.getRunescapeSmallFont()
                .deriveFont(FontManager.getRunescapeSmallFont().getSize2D() + 4f));
        nameLabel.setForeground(index == hoverIndex ? Color.WHITE : ColorScheme.TEXT_COLOR);
        nameLabel.setText(value.displayName);

        iconLabel.setOpaque(false);
        iconLabel.setIcon(SlayerTaskRenderer.getVariantIcon(value.displayName, value.parentTask.name));

        return this;
    }
}
