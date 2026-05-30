/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Factory that produces the compact button panel used in each location row:
 * a rounded "Nav" button followed by a world-map icon button.
 *
 * <p>Both buttons are disabled when {@code reqMet} is {@code false} and will
 * show the missing-requirement text as a tooltip.  When enabled, the supplied
 * {@link Runnable}s are wired as {@link ActionListener}s and the buttons are
 * registered into {@code buttons} / {@code listeners} so the caller can remove
 * listeners during cleanup.</p>
 */
public final class LocationNavGroup
{
    private static final int NAV_W = 36;
    private static final int NAV_H = 24;
    private static final int MAP_W = 24;
    private static final int MAP_H = 24;
    private static final int ARC   = 10;

    private static final ImageIcon WORLD_ICON;

    static
    {
        BufferedImage img = ImageUtil.loadImageResource(LocationNavGroup.class, "/images/world.png");
        WORLD_ICON = (img != null) ? new ImageIcon(ImageUtil.resizeImage(img, 17, 17)) : null;
    }

    private LocationNavGroup() {}

    /**
     * Creates a transparent {@link JPanel} containing a rounded Nav button and
     * a world-map icon button, separated by a 2-pixel strut.
     *
     * @param locationName display name used for tooltips
     * @param reqMet       whether the player meets the location requirements
     * @param missingText  tooltip text shown when requirements are not met
     * @param onNav        called when the Nav button is clicked
     * @param onMap        called when the world-map button is clicked
     * @param buttons      list to register active buttons into (for listener cleanup)
     * @param listeners    list to register active listeners into (for listener cleanup)
     * @return the assembled button panel
     */
    public static JPanel create(
            String locationName,
            boolean reqMet,
            String missingText,
            Runnable onNav,
            Runnable onMap,
            List<JButton> buttons,
            List<ActionListener> listeners)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setOpaque(false);

        JButton navButton = buildNavButton(locationName, reqMet, missingText);
        JButton mapButton = buildMapButton(locationName, reqMet, missingText);

        if (reqMet)
        {
            ActionListener navListener = e -> onNav.run();
            navButton.addActionListener(navListener);
            buttons.add(navButton);
            listeners.add(navListener);

            ActionListener mapListener = e -> onMap.run();
            mapButton.addActionListener(mapListener);
            buttons.add(mapButton);
            listeners.add(mapListener);
        }

        panel.add(mapButton);
        panel.add(Box.createHorizontalStrut(2));
        panel.add(navButton);
        return panel;
    }

    // ── private helpers ─────────────────────────────────────────────────────

    private static JButton buildNavButton(String locationName, boolean reqMet, String missingText)
    {
        JButton btn = new JButton("Nav")
        {
            boolean hovered;
            {
                addMouseListener(new MouseAdapter()
                {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color base = hovered ? new Color(70, 70, 70) : getBackground();
                g2.setColor(getModel().isArmed() ? base.darker() : base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), ARC, ARC);
                g2.setFont(getFont());
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                int tx = (getWidth() - fm.stringWidth(txt)) / 2;
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(txt, tx, ty);
                g2.dispose();
            }

            @Override
            protected void paintBorder(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hovered ? new Color(115, 115, 115) : new Color(95, 95, 95));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
                g2.dispose();
            }
        };

        btn.setFont(FontManager.getRunescapeSmallFont());
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(NAV_W, NAV_H));
        btn.setMinimumSize(new Dimension(NAV_W, NAV_H));
        btn.setMaximumSize(new Dimension(NAV_W, NAV_H));

        if (reqMet)
        {
            btn.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            btn.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            btn.setToolTipText("Navigate to " + capitalize(locationName));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else
        {
            btn.setEnabled(false);
            btn.setBackground(ColorScheme.DARK_GRAY_COLOR);
            btn.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            btn.setToolTipText("Requires: " + missingText);
        }

        return btn;
    }

    private static JButton buildMapButton(String locationName, boolean reqMet, String missingText)
    {
        JButton btn = WORLD_ICON != null ? new JButton(WORLD_ICON) : new JButton("M");
        btn.setPreferredSize(new Dimension(MAP_W, MAP_H));
        btn.setMinimumSize(new Dimension(MAP_W, MAP_H));
        btn.setMaximumSize(new Dimension(MAP_W, MAP_H));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);

        if (reqMet)
        {
            btn.setToolTipText("Show " + capitalize(locationName) + " on world map");
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        else
        {
            btn.setEnabled(false);
            btn.setToolTipText("Requires: " + missingText);
        }

        return btn;
    }

    private static String capitalize(String s)
    {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
