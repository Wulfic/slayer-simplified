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
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom list cell renderer for Slayer tasks in the search panel.
 * Displays the task name on the left and a small monster thumbnail on the right.
 * Images are loaded lazily from classpath resources and cached after first use.
 * At 32×32 pixels the full set of ~120 monsters uses under 2 MB.
 */
public class SlayerTaskRenderer extends JPanel implements ListCellRenderer<Task>
{
    private static final int ROW_HEIGHT = 42;
    private static final int ICON_SIZE  = 32;

    /** Maps lower-case-underscore task key → scaled icon (or EMPTY_ICON). */
    private static final Map<String, Icon> imageCache = new HashMap<>();
    private static final Icon EMPTY_ICON;

    static
    {
        // Transparent placeholder so every row occupies the same width.
        BufferedImage blank = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
        EMPTY_ICON = new ImageIcon(blank);
    }

    private static int hoverIndex = -1;

    private final JLabel nameLabel = new JLabel();
    private final JLabel iconLabel = new JLabel();

    public SlayerTaskRenderer()
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
            JList<? extends Task> list,
            Task value,
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
        nameLabel.setText(value.name);

        iconLabel.setOpaque(false);
        iconLabel.setIcon(getMonsterIcon(value.name));

        return this;
    }

    public static Icon getMonsterIcon(String taskName)
    {
        final String key = taskName.toLowerCase().replace(" ", "_");
        return imageCache.computeIfAbsent(key, k ->
        {
            try
            {
                BufferedImage img = ImageUtil.loadImageResource(
                        SlayerTaskRenderer.class, "/images/monsters/" + k + ".png");
                if (img != null)
                {
                    return new ImageIcon(ImageUtil.resizeImage(img, ICON_SIZE, ICON_SIZE));
                }
            }
            catch (Exception ignored)
            {
                // No image for this monster — fall through to transparent placeholder.
            }
            return EMPTY_ICON;
        });
    }
}
