/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.presentation.components.ScrollBarStyling;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Scrollable panel crediting everyone who has meaningfully contributed to the
 * plugin — bug reports, fixes, donations, and anything else that helped.
 *
 * Shown as its own card from the settings panel rather than inline, so the
 * list can grow without pushing the settings content out of view.
 */
public class SpecialThanksPanel extends JPanel
{
    private static final Color USERNAME_COLOR = new Color(255, 152, 0);

    private final Runnable onClose;
    private final JPanel listPanel = new JPanel();

    public SpecialThanksPanel(Runnable onClose)
    {
        this.onClose = onClose;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildHeaderPanel(), BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        listPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JLabel intro = new JLabel("<html><body>Thank you to everyone who has helped "
                + "improve Slayer Simplified.</body></html>");
        intro.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        intro.setFont(FontManager.getRunescapeSmallFont());
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        intro.setBorder(new EmptyBorder(0, 0, 8, 0));
        listPanel.add(intro);

        addContributor("vividflash",
                "Contributed the fix silencing startup warnings for tasks without a bundled monster image.");
        addContributor("danielvxsp",
                "Reported the bug where opening the plugin tab extended the client's minimum window size.");

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ScrollBarStyling.apply(scrollPane);

        add(scrollPane, BorderLayout.CENTER);
    }

    /** Adds one contributor entry: GitHub username above a one-line description. */
    private void addContributor(String username, String contribution)
    {
        JPanel entry = new JPanel();
        entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
        entry.setBackground(ColorScheme.DARK_GRAY_COLOR);
        entry.setAlignmentX(Component.LEFT_ALIGNMENT);
        entry.setBorder(new EmptyBorder(6, 6, 6, 6));

        JLabel name = new JLabel(username);
        name.setForeground(USERNAME_COLOR);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel what = new JLabel("<html><body>" + contribution + "</body></html>");
        what.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        what.setFont(FontManager.getRunescapeSmallFont());
        what.setAlignmentX(Component.LEFT_ALIGNMENT);

        entry.add(name);
        entry.add(Box.createVerticalStrut(2));
        entry.add(what);

        listPanel.add(entry);
        listPanel.add(Box.createVerticalStrut(6));
    }

    private JPanel buildHeaderPanel()
    {
        JButton backButton = new JButton("←");
        backButton.setFont(new Font("Dialog", Font.PLAIN, 13));
        backButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        backButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.setToolTipText("Back to settings");
        backButton.setMargin(new Insets(0, 0, 0, 0));
        backButton.setPreferredSize(new Dimension(18, 18));
        backButton.addActionListener(e -> onClose.run());

        JLabel titleLabel = new JLabel("Special Thanks");
        titleLabel.setForeground(USERNAME_COLOR);
        titleLabel.setFont(FontManager.getRunescapeBoldFont());

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setBorder(new EmptyBorder(6, 4, 6, 6));
        titleRow.add(backButton, BorderLayout.WEST);
        titleRow.add(titleLabel, BorderLayout.CENTER);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(sep, BorderLayout.SOUTH);
        return header;
    }
}
