/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.StreakFillerMaster;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;

/**
 * Inline settings panel displayed within the plugin panel when the gear icon
 * is clicked. Mirrors all settings from {@link SlayerSimplifiedConfig} and
 * writes changes back immediately via the config interface setters.
 */
public class SettingsPanel extends JPanel
{
    private final SlayerSimplifiedConfig config;
    private final Runnable onClose;
    private final Runnable onShowSpecialThanks;

    private JComboBox<SlayerMaster> masterCombo;
    private JCheckBox highlightCheck;
    private JButton colorButton;
    private JCheckBox autoNavCheck;
    private JCheckBox debugCheck;
    private JCheckBox remindCapeCheck;
    private JCheckBox showReminderOverlayCheck;
    private JCheckBox streakOptimizerCheck;
    private JComboBox<StreakFillerMaster> fillerMasterCombo;
    private JCheckBox showNonSlayerCheck;
    private JCheckBox tileNotesCheck;

    public SettingsPanel(SlayerSimplifiedConfig config, Runnable onClose, Runnable onShowSpecialThanks)
    {
        this.config = config;
        this.onClose = onClose;
        this.onShowSpecialThanks = onShowSpecialThanks;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(10, 8, 8, 8));

        buildUI();
    }

    /** Re-reads all current config values and updates every control. */
    public void refresh()
    {
        boolean optimizerOn = config.streakOptimizerEnabled();
        showNonSlayerCheck.setSelected(config.showNonSlayerEnemies());
        tileNotesCheck.setSelected(config.tileNotes());
        streakOptimizerCheck.setSelected(optimizerOn);
        masterCombo.setSelectedItem(config.preferredMaster());
        masterCombo.setEnabled(!optimizerOn);
        fillerMasterCombo.setSelectedItem(config.streakFillerMaster());
        fillerMasterCombo.setEnabled(optimizerOn);
        highlightCheck.setSelected(config.highlightTarget());
        colorButton.setBackground(config.highlightColor());
        autoNavCheck.setSelected(config.autoNavigate());
        debugCheck.setSelected(config.debugCoordinates());
        remindCapeCheck.setSelected(config.remindSlayerCape());
        showReminderOverlayCheck.setSelected(config.showReminderOverlay());
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

        // --- Streak Point Optimizer ---
        streakOptimizerCheck = makeCheckBox(config.streakOptimizerEnabled());
        streakOptimizerCheck.addActionListener(e ->
        {
            boolean on = streakOptimizerCheck.isSelected();
            config.setStreakOptimizerEnabled(on);
            masterCombo.setEnabled(!on);
            fillerMasterCombo.setEnabled(on);
        });
        add(makeRow("Streak Optimizer", streakOptimizerCheck,
                "<html>Recommend the best master each task to maximise Slayer reward points<br>"
                        + "(Turael boosting: Turael for fillers, Konar/Chaeldar for milestones).<br>"
                        + "<b>Overrides the Preferred Master setting below.</b></html>"));
        add(Box.createVerticalStrut(6));

        // --- Streak Filler Master ---
        fillerMasterCombo = new JComboBox<>(StreakFillerMaster.values());
        fillerMasterCombo.setSelectedItem(config.streakFillerMaster());
        fillerMasterCombo.setEnabled(config.streakOptimizerEnabled());
        fillerMasterCombo.setFont(FontManager.getRunescapeSmallFont());
        fillerMasterCombo.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        fillerMasterCombo.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        fillerMasterCombo.setPreferredSize(new Dimension(120, 22));
        fillerMasterCombo.setMaximumSize(new Dimension(120, 22));
        fillerMasterCombo.addActionListener(e ->
        {
            StreakFillerMaster selected = (StreakFillerMaster) fillerMasterCombo.getSelectedItem();
            if (selected != null)
            {
                config.setStreakFillerMaster(selected);
            }
        });
        add(makeRow("Filler Master", fillerMasterCombo,
                "<html>Master used for non-milestone tasks while the optimizer is enabled.<br>"
                        + "<b>Turael / Spria</b> — 0 pts, shortest tasks (classic Turael boosting).<br>"
                        + "<b>Mazchna</b> — 6 pts per filler, longer tasks (Mazchna boosting).<br>"
                        + "Milestone tasks (10/50/100/250/1000) always use your highest-eligible master.</html>"));
        add(Box.createVerticalStrut(6));

        // --- Preferred Master ---
        SlayerMaster[] settingsMasters = Arrays.stream(SlayerMaster.values())
                .filter(m -> m != SlayerMaster.NON_SLAYER_ENEMIES)
                .toArray(SlayerMaster[]::new);
        masterCombo = new JComboBox<>(settingsMasters);
        masterCombo.setSelectedItem(config.preferredMaster());
        masterCombo.setEnabled(!config.streakOptimizerEnabled());
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
        add(makeRow("Location Debug", debugCheck,
                "Show current coordinates and nav target on screen, and enable the AAAAA test monster in the list"));
        add(Box.createVerticalStrut(6));

        // --- Slayer Cape Reminder ---
        remindCapeCheck = makeCheckBox(config.remindSlayerCape());
        remindCapeCheck.addActionListener(e -> config.setRemindSlayerCape(remindCapeCheck.isSelected()));
        add(makeRow("Cape Reminder (99)", remindCapeCheck,
                "Pop up a reminder to bring your Slayer cape on each new assignment (only triggers when you have 99 Slayer)"));
        add(Box.createVerticalStrut(6));

        // --- Show Task Reminder Overlay ---
        showReminderOverlayCheck = makeCheckBox(config.showReminderOverlay());
        showReminderOverlayCheck.addActionListener(e -> config.setShowReminderOverlay(showReminderOverlayCheck.isSelected()));
        add(makeRow("Task Reminder Overlay", showReminderOverlayCheck,
                "Show the on-screen overlay with required items, suggested items, and your notes while on a slayer task"));
        add(Box.createVerticalStrut(6));

        // --- Tile Notes ---
        tileNotesCheck = makeCheckBox(config.tileNotes());
        tileNotesCheck.addActionListener(e -> config.setTileNotes(tileNotesCheck.isSelected()));
        add(makeRow("TileNotes", tileNotesCheck,
                "<html>Highlight every known training-spot tile for your current task directly in the<br>"
                        + "game scene. The location name is drawn above each tile. Only tiles within<br>"
                        + "your loaded area are visible — walk to the spot to see the marker.</html>"));
        // --- Show Non-Slayer Enemies ---
        showNonSlayerCheck = makeCheckBox(config.showNonSlayerEnemies());
        showNonSlayerCheck.addActionListener(e -> config.setShowNonSlayerEnemies(showNonSlayerCheck.isSelected()));

        JPanel nonSlayerRow = new JPanel(new BorderLayout(8, 0));
        nonSlayerRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        nonSlayerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        nonSlayerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nonSlayerRow.setToolTipText("Show non-slayer enemies grouped in the task browser (work in progress)");

        JPanel nonSlayerLabelPanel = new JPanel(new GridLayout(2, 1, 0, 0));
        nonSlayerLabelPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel nonSlayerLabel = new JLabel("Show non-slayer enemies");
        nonSlayerLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        nonSlayerLabel.setFont(FontManager.getRunescapeSmallFont());
        JLabel nonSlayerWip = new JLabel("(WIP)");
        nonSlayerWip.setForeground(new Color(180, 140, 50));
        nonSlayerWip.setFont(FontManager.getRunescapeSmallFont());
        nonSlayerLabelPanel.add(nonSlayerLabel);
        nonSlayerLabelPanel.add(nonSlayerWip);

        nonSlayerRow.add(nonSlayerLabelPanel, BorderLayout.CENTER);
        nonSlayerRow.add(showNonSlayerCheck, BorderLayout.EAST);
        add(nonSlayerRow);
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

        add(Box.createVerticalStrut(12));
        add(makeSeparator());
        add(Box.createVerticalStrut(8));

        // --- Bug report link ---
        JLabel bugLabel = new JLabel("<html>Found a <font color='#4fc3f7'><u>bug</u></font>?</html>");
        bugLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        bugLabel.setFont(FontManager.getRunescapeSmallFont());
        bugLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bugLabel.setToolTipText("https://github.com/Wulfic/slayer-simplified/issues");
        bugLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                LinkBrowser.browse("https://github.com/Wulfic/slayer-simplified/issues");
            }
        });
        JPanel bugRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bugRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bugRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bugRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        bugRow.add(bugLabel);
        add(bugRow);
        add(Box.createVerticalStrut(4));

        // --- Feature suggestion link ---
        JLabel featureLabel = new JLabel("<html>Have a <font color='#4fc3f7'><u>feature suggestion</u></font>?</html>");
        featureLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        featureLabel.setFont(FontManager.getRunescapeSmallFont());
        featureLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        featureLabel.setToolTipText("https://github.com/Wulfic/slayer-simplified/issues");
        featureLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                LinkBrowser.browse("https://github.com/Wulfic/slayer-simplified/issues");
            }
        });
        JPanel featureRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        featureRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        featureRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        featureRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        featureRow.add(featureLabel);
        add(featureRow);
        add(Box.createVerticalStrut(4));

        // --- Ko-fi support link ---
        JLabel kofiLabel = new JLabel("<html><font color='#4fc3f7'><u>Buy me a Ko-fi</u></font></html>");
        kofiLabel.setFont(FontManager.getRunescapeSmallFont());
        kofiLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        kofiLabel.setToolTipText("https://ko-fi.com/wulfic");
        kofiLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                LinkBrowser.browse("https://ko-fi.com/wulfic");
            }
        });
        JPanel kofiRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        kofiRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        kofiRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        kofiRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        kofiRow.add(kofiLabel);
        add(kofiRow);
        add(Box.createVerticalStrut(8));

        // --- Special Thanks ---
        JButton thanksButton = new JButton("Special Thanks");
        thanksButton.setFont(FontManager.getRunescapeSmallFont());
        thanksButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        thanksButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        thanksButton.setFocusPainted(false);
        thanksButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        thanksButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        thanksButton.setToolTipText("Everyone who has helped improve the plugin");
        thanksButton.addActionListener(e -> onShowSpecialThanks.run());
        add(thanksButton);
        add(Box.createVerticalStrut(8));
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
