/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.presentation.components.ScrollBarStyling;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

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

    private static final int LIST_PADDING = 8;
    private static final int ENTRY_PADDING = 6;

    /** Horizontal space between the viewport edge and text sitting directly in the list. */
    private static final int INTRO_INSET = LIST_PADDING * 2;
    /** Same, for text nested one level deeper inside a contributor entry. */
    private static final int ENTRY_INSET = INTRO_INSET + (ENTRY_PADDING * 2);

    /** Floor so a collapsed viewport can never produce a zero or negative wrap width. */
    private static final int MIN_WRAP_WIDTH = 40;

    private final Runnable onClose;
    private final JPanel listPanel = new ViewportWidthPanel();
    private final List<WrappingLabel> wrappingLabels = new ArrayList<>();

    public SpecialThanksPanel(Runnable onClose)
    {
        this.onClose = onClose;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildHeaderPanel(), BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        listPanel.setBorder(new EmptyBorder(LIST_PADDING, LIST_PADDING, LIST_PADDING, LIST_PADDING));

        JLabel intro = wrappingLabel("Thank you to everyone who has helped improve Slayer Simplified.",
                INTRO_INSET);
        intro.setBorder(new EmptyBorder(0, 0, 8, 0));
        listPanel.add(intro);

        addContributor("vividflash",
                "Contributed the fix silencing startup warnings for tasks without a bundled monster image.");
        addContributor("danielvxsp",
                "Reported the bug where opening the plugin tab extended the client's minimum window size.");
        addContributor("Bruster112",
                "Contributed the fix gating the task overlays behind combat, so they only show once you're actually fighting the assigned monster.");

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ScrollBarStyling.apply(scrollPane);

        // The viewport's width is the real text width — it already excludes
        // RuneLite's panel borders and the scrollbar, whatever they happen to be.
        JViewport viewport = scrollPane.getViewport();
        viewport.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                rewrapText(viewport.getWidth());
            }
        });

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Re-wraps every body label to the pane's measured width. Package-private so
     * the render test can drive it without realising a real window.
     */
    void rewrapText(int viewportWidth)
    {
        for (WrappingLabel label : wrappingLabels)
        {
            label.wrapTo(viewportWidth);
        }
        listPanel.revalidate();
    }

    /** Adds one contributor entry: GitHub username above a one-line description. */
    private void addContributor(String username, String contribution)
    {
        // Stretches to the pane's full width but never taller than its content,
        // so the entry background spans the panel without BoxLayout inflating it.
        JPanel entry = new JPanel()
        {
            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
        entry.setBackground(ColorScheme.DARK_GRAY_COLOR);
        entry.setAlignmentX(Component.LEFT_ALIGNMENT);
        entry.setBorder(new EmptyBorder(ENTRY_PADDING, ENTRY_PADDING, ENTRY_PADDING, ENTRY_PADDING));

        JLabel name = new JLabel(username);
        name.setForeground(USERNAME_COLOR);
        name.setFont(FontManager.getRunescapeBoldFont());
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel what = wrappingLabel(contribution, ENTRY_INSET);

        entry.add(name);
        entry.add(Box.createVerticalStrut(2));
        entry.add(what);

        listPanel.add(entry);
        listPanel.add(Box.createVerticalStrut(6));
    }

    /**
     * Body-text label that wraps to the pane instead of running off it.
     *
     * @param inset horizontal space this label's text sits inside of, measured
     *              from the viewport edge
     */
    private JLabel wrappingLabel(String text, int inset)
    {
        WrappingLabel label = new WrappingLabel(text, inset);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrappingLabels.add(label);
        return label;
    }

    /**
     * An HTML label reports its full unwrapped width as its minimum size, so
     * BoxLayout cannot shrink it and long text runs off the pane. Only an explicit
     * CSS width makes it wrap — and that width has to be the real one, since the
     * plugin panel's usable width depends on RuneLite's own borders and scrollbar
     * and on how wide the user has dragged the sidebar. So each label re-wraps
     * itself against the measured viewport width whenever the viewport resizes.
     */
    private static final class WrappingLabel extends JLabel
    {
        private final String rawText;
        private final int inset;
        private int wrapWidth = -1;

        private WrappingLabel(String rawText, int inset)
        {
            this.rawText = rawText;
            this.inset = inset;
            // Best-effort width until the pane is first laid out and measured.
            wrapTo(PluginPanel.PANEL_WIDTH);
        }

        /** Re-wraps to {@code viewportWidth}; a no-op when the usable width is unchanged. */
        private void wrapTo(int viewportWidth)
        {
            int width = Math.max(MIN_WRAP_WIDTH, viewportWidth - inset);
            if (width == wrapWidth)
            {
                return;
            }
            wrapWidth = width;
            // Width is in pt, not px: Swing's CSS scales px by 1.3, so a px width
            // renders ~30% wider than asked and the text overruns the pane. pt maps
            // 1:1 onto device pixels.
            setText("<html><body style='width:" + width + "pt'>" + rawText + "</body></html>");
        }
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

    /** Keeps the contributor list at the viewport's width so nothing scrolls sideways. */
    private static class ViewportWidthPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
