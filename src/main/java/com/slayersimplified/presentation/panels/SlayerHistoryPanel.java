/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.domain.Task;
import com.slayersimplified.domain.TaskHistoryEntry;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import com.slayersimplified.services.SlayerHistoryService;
import com.slayersimplified.services.TaskService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Panel displaying the player's logged slayer task history.
 * Mirrors the layout shown in RuneLite's built-in Slayer History view:
 * count | icon | name + master + date | #task-number
 */
public class SlayerHistoryPanel extends JPanel
{
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US);
    private static final Color COUNT_COLOR = new Color(255, 200, 0);
    private static final int ICON_SIZE = 36;

    private final SlayerHistoryService historyService;
    private final TaskService taskService;
    private final Runnable onClose;

    private final JLabel tasksLoggedLabel = new JLabel();
    private final JPanel listPanel = new JPanel();

    public SlayerHistoryPanel(SlayerHistoryService historyService, TaskService taskService, Runnable onClose)
    {
        this.historyService = historyService;
        this.taskService = taskService;
        this.onClose = onClose;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(buildHeaderPanel(), BorderLayout.NORTH);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ScrollBarStyling.apply(scrollPane);

        add(scrollPane, BorderLayout.CENTER);
    }

    /** Reloads history data from the service and rebuilds the entry list. */
    public void refresh()
    {
        List<TaskHistoryEntry> entries = historyService.getHistory();
        tasksLoggedLabel.setText("Tasks logged: " + entries.size());

        listPanel.removeAll();
        if (entries.isEmpty())
        {
            JLabel empty = new JLabel("No tasks logged yet.");
            empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            empty.setFont(FontManager.getRunescapeSmallFont());
            empty.setBorder(new EmptyBorder(10, 8, 0, 0));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(empty);
        }
        else
        {
            for (TaskHistoryEntry entry : entries)
            {
                listPanel.add(buildEntryRow(entry));
            }
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private JPanel buildHeaderPanel()
    {
        JButton backButton = new JButton("\u2190");
        backButton.setFont(FontManager.getRunescapeSmallFont());
        backButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        backButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backButton.setToolTipText("Back");
        backButton.addActionListener(e -> onClose.run());

        JLabel titleLabel = new JLabel("Slayer History");
        titleLabel.setForeground(new Color(255, 152, 0));
        titleLabel.setFont(FontManager.getRunescapeBoldFont());

        tasksLoggedLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        tasksLoggedLabel.setFont(FontManager.getRunescapeSmallFont());

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setBorder(new EmptyBorder(6, 4, 6, 6));
        titleRow.add(backButton, BorderLayout.WEST);
        titleRow.add(titleLabel, BorderLayout.CENTER);
        titleRow.add(tasksLoggedLabel, BorderLayout.EAST);

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(sep, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildEntryRow(TaskHistoryEntry entry)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));

        // WEST: kill count + monster icon
        JPanel leftPanel = new JPanel(new BorderLayout(2, 0));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        leftPanel.setPreferredSize(new Dimension(58, 0));

        if (entry.count > 0)
        {
            JLabel countLabel = new JLabel(String.valueOf(entry.count));
            countLabel.setForeground(COUNT_COLOR);
            countLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            countLabel.setVerticalAlignment(SwingConstants.TOP);
            countLabel.setPreferredSize(new Dimension(20, 0));
            leftPanel.add(countLabel, BorderLayout.WEST);
        }

        Task task = taskService.get(entry.taskName);
        if (task != null && task.image != null)
        {
            BufferedImage scaled = ImageUtil.resizeImage(task.image, ICON_SIZE, ICON_SIZE);
            JLabel iconLabel = new JLabel(new ImageIcon(scaled));
            iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
            leftPanel.add(iconLabel, BorderLayout.CENTER);
        }
        row.add(leftPanel, BorderLayout.WEST);

        // CENTER: name, master, date stacked vertically
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        centerPanel.setBorder(new EmptyBorder(0, 4, 0, 4));

        JLabel nameLabel = new JLabel(entry.taskName != null ? entry.taskName : "");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        centerPanel.add(nameLabel);

        if (entry.master != null && !entry.master.isEmpty())
        {
            JLabel masterLabel = new JLabel(entry.master);
            masterLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            masterLabel.setFont(FontManager.getRunescapeSmallFont());
            masterLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(masterLabel);
        }

        if (entry.timestamp > 0)
        {
            LocalDateTime dt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault());
            JLabel dateLabel = new JLabel(DATE_FMT.format(dt));
            dateLabel.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            dateLabel.setFont(FontManager.getRunescapeSmallFont());
            dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            centerPanel.add(dateLabel);
        }
        row.add(centerPanel, BorderLayout.CENTER);

        // EAST: task number (#97)
        if (entry.taskNumber > 0)
        {
            JLabel numLabel = new JLabel("#" + entry.taskNumber);
            numLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            numLabel.setFont(FontManager.getRunescapeSmallFont());
            numLabel.setVerticalAlignment(SwingConstants.CENTER);
            row.add(numLabel, BorderLayout.EAST);
        }

        return row;
    }
}
