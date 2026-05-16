/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.SlayerMaster;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Inline settings panel displayed within the plugin panel when the gear icon
 * is clicked. Mirrors all settings from {@link SlayerSimplifiedConfig} and
 * writes changes back immediately via the config interface setters.
 */
public class SettingsPanel extends JPanel
{
    private final SlayerSimplifiedConfig config;
    private final Runnable onClose;

    private JComboBox<SlayerMaster> masterCombo;
    private JCheckBox highlightCheck;
    private JButton colorButton;
    private JCheckBox autoNavCheck;
    private JCheckBox debugCheck;
    private JCheckBox remindCapeCheck;

    public SettingsPanel(SlayerSimplifiedConfig config, Runnable onClose)
    {
        this.config = config;
        this.onClose = onClose;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 8, 8, 8));

        buildUI();
    }

    /** Re-reads all current config values and updates every control. */
    public void refresh()
    {
        masterCombo.setSelectedItem(config.preferredMaster());
        highlightCheck.setSelected(config.highlightTarget());
        colorButton.setBackground(config.highlightColor());
        autoNavCheck.setSelected(config.autoNavigate());
        debugCheck.setSelected(config.debugCoordinates());
        remindCapeCheck.setSelected(config.remindSlayerCape());
    }

    private void buildUI()
    {
        JLabel title = new JLabel("Settings");
        title.setForeground(new Color(255, 152, 0));
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(8));
        add(makeSeparator());
        add(Box.createVerticalStrut(10));

        // --- Preferred Master ---
        masterCombo = new JComboBox<>(SlayerMaster.values());
        masterCombo.setSelectedItem(config.preferredMaster());
        masterCombo.setFont(FontManager.getRunescapeSmallFont());
        masterCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        masterCombo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        masterCombo.setPreferredSize(new Dimension(120, 22));
        masterCombo.setMaximumSize(new Dimension(120, 22));
        masterCombo.addActionListener(e ->
        {
            SlayerMaster selected = (SlayerMaster) masterCombo.getSelectedItem();
            if (selected != null)
            {
                config.setPreferredMaster(selected);
            }
        });
        add(makeRow("Preferred Master", masterCombo,
                "The Slayer master to navigate to when you have no active task"));
        add(Box.createVerticalStrut(6));

        // --- Highlight Target ---
        highlightCheck = makeCheckBox(config.highlightTarget());
        highlightCheck.addActionListener(e -> config.setHighlightTarget(highlightCheck.isSelected()));
        add(makeRow("Highlight Target", highlightCheck,
                "Draw a coloured outline around your current task NPCs so they are easy to spot"));
        add(Box.createVerticalStrut(6));

        // --- Highlight Color ---
        colorButton = makeColorSwatch(config.highlightColor());
        colorButton.addActionListener(e ->
        {
            Color chosen = JColorChooser.showDialog(this, "Highlight Color", config.highlightColor());
            if (chosen != null)
            {
                colorButton.setBackground(chosen);
                config.setHighlightColor(chosen);
            }
        });
        add(makeRow("Highlight Color", colorButton,
                "Click the swatch to choose the colour of the NPC outline"));
        add(Box.createVerticalStrut(6));

        // --- Auto Navigate ---
        autoNavCheck = makeCheckBox(config.autoNavigate());
        autoNavCheck.addActionListener(e -> config.setAutoNavigate(autoNavCheck.isSelected()));
        add(makeRow("Auto Navigate", autoNavCheck,
                "Automatically trigger Shortest Path navigation when you receive a new assignment or type !task in chat"));
        add(Box.createVerticalStrut(6));

        // --- Debug Coordinates ---
        debugCheck = makeCheckBox(config.debugCoordinates());
        debugCheck.addActionListener(e -> config.setDebugCoordinates(debugCheck.isSelected()));
        add(makeRow("Debug Coordinates", debugCheck,
                "Overlay your current WorldPoint on screen \u2014 useful for mapping new monster spawn locations"));
        add(Box.createVerticalStrut(6));

        // --- Slayer Cape Reminder ---
        remindCapeCheck = makeCheckBox(config.remindSlayerCape());
        remindCapeCheck.addActionListener(e -> config.setRemindSlayerCape(remindCapeCheck.isSelected()));
        add(makeRow("Cape Reminder (99)", remindCapeCheck,
                "Pop up a reminder to bring your Slayer cape on each new assignment (only triggers when you have 99 Slayer)"));
        add(Box.createVerticalStrut(12));

        add(makeSeparator());
        add(Box.createVerticalStrut(10));

        // --- Done button ---
        JButton doneButton = new JButton("Done");
        doneButton.setFont(FontManager.getRunescapeSmallFont());
        doneButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        doneButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        doneButton.setFocusPainted(false);
        doneButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        doneButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        doneButton.addActionListener(e -> onClose.run());
        add(doneButton);
    }

    private JPanel makeRow(String labelText, JComponent control)
    {
        return makeRow(labelText, control, null);
    }

    private JPanel makeRow(String labelText, JComponent control, String tooltip)
    {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel(labelText);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        if (tooltip != null)
        {
            label.setToolTipText(tooltip);
            row.setToolTipText(tooltip);
        }

        row.add(label, BorderLayout.CENTER);
        row.add(control, BorderLayout.EAST);
        return row;
    }

    private JCheckBox makeCheckBox(boolean selected)
    {
        JCheckBox cb = new JCheckBox();
        cb.setSelected(selected);
        cb.setBackground(ColorScheme.DARK_GRAY_COLOR);
        cb.setForeground(Color.WHITE);
        cb.setOpaque(true);
        cb.setFocusPainted(false);
        cb.setIcon(buildCheckIcon(false));
        cb.setSelectedIcon(buildCheckIcon(true));
        cb.setPressedIcon(buildCheckIcon(false));
        return cb;
    }

    private static Icon buildCheckIcon(boolean checked)
    {
        return new Icon()
        {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getIconWidth(), h = getIconHeight();
                g2.setColor(ColorScheme.MEDIUM_GRAY_COLOR);
                g2.fillRoundRect(x, y, w, h, 3, 3);
                g2.setColor(checked ? new Color(50, 110, 50) : ColorScheme.DARKER_GRAY_COLOR);
                g2.fillRoundRect(x + 1, y + 1, w - 2, h - 2, 2, 2);
                if (checked)
                {
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(x + 3, y + 7, x + 6, y + 10);
                    g2.drawLine(x + 6, y + 10, x + 11, y + 4);
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth()
            {
                return 14;
            }

            @Override
            public int getIconHeight()
            {
                return 14;
            }
        };
    }

    private JButton makeColorSwatch(Color color)
    {
        JButton btn = new JButton();
        btn.setBackground(color);
        btn.setPreferredSize(new Dimension(40, 20));
        btn.setMaximumSize(new Dimension(40, 20));
        btn.setMinimumSize(new Dimension(40, 20));
        btn.setFocusPainted(false);
        btn.setToolTipText("Click to choose highlight color");
        return btn;
    }

    private JSeparator makeSeparator()
    {
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        return sep;
    }
}
