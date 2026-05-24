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
import com.slayersimplified.services.SlayerTaskTracker;
import com.slayersimplified.services.TaskService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

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

    private static final Color ACTIVE_BORDER_COLOR = new Color(80, 200, 80);
    private static final Color SKIPPED_COLOR = new Color(220, 90, 90);

    private final SlayerHistoryService historyService;
    private final TaskService taskService;
    private final SlayerTaskTracker taskTracker;
    private final Runnable onClose;
    private final Consumer<Task> onTaskSelected;

    private final JLabel tasksLoggedLabel = new JLabel();
    private final JPanel listPanel = new JPanel();

    public SlayerHistoryPanel(SlayerHistoryService historyService, TaskService taskService,
                               SlayerTaskTracker taskTracker, Runnable onClose,
                               Consumer<Task> onTaskSelected)
    {
        this.historyService = historyService;
        this.taskService = taskService;
        this.taskTracker = taskTracker;
        this.onClose = onClose;
        this.onTaskSelected = onTaskSelected;

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

        // Safe to call from EDT — reads only from config, not client varpValue
        String currentName = taskTracker.getCurrentTaskName();
        boolean hasCurrentTask = currentName != null && !currentName.isEmpty();

        // Is the current active task already at the top of the logged history?
        boolean currentTaskIsFirstEntry = hasCurrentTask && !entries.isEmpty()
                && currentName.equalsIgnoreCase(entries.get(0).taskName);

        listPanel.removeAll();

        if (!hasCurrentTask && entries.isEmpty())
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
            // If the current task was not captured in the log (e.g. assigned before
            // the plugin was running), prepend a live "Current Task" row.
            if (hasCurrentTask && !currentTaskIsFirstEntry)
            {
                listPanel.add(buildCurrentTaskRow(currentName, entries.size() + 1));
            }

            for (int i = 0; i < entries.size(); i++)
            {
                boolean isActive = i == 0 && currentTaskIsFirstEntry;
                listPanel.add(buildEntryRow(entries.get(i), isActive, entries.size() - i));
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

    /** Builds a highlighted row for the player's currently active task (not yet in the log). */
    private JPanel buildCurrentTaskRow(String taskName, int taskCount)
    {
        int total = taskTracker.getCurrentTaskTotal(); // volatile read, safe from EDT
        int streak = taskTracker.getCurrentAssignmentNumber();
        TaskHistoryEntry synthetic = new TaskHistoryEntry(taskName, total, null, 0, streak);
        JPanel row = buildEntryRow(synthetic, true, taskCount);

        // If the streak is unknown we can't show a task number, so fall back to an "Active" badge
        if (streak <= 0)
        {
            JLabel badge = new JLabel("Active");
            badge.setForeground(ACTIVE_BORDER_COLOR);
            badge.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            badge.setBorder(new EmptyBorder(0, 4, 0, 0));
            row.add(badge, BorderLayout.EAST);
        }
        return row;
    }

    private JPanel buildEntryRow(TaskHistoryEntry entry)
    {
        return buildEntryRow(entry, false, 0);
    }

    private JPanel buildEntryRow(TaskHistoryEntry entry, boolean isActive)
    {
        return buildEntryRow(entry, isActive, 0);
    }

    private JPanel buildEntryRow(TaskHistoryEntry entry, boolean isActive, int taskCount)
    {
        // Render-time fallback: if this row represents the active task but its
        // stored count/streak haven't been backfilled yet, read live values from
        // the task tracker (which falls back to the RuneLite Slayer plugin config).
        int displayCount = entry.count;
        int displayNumber = entry.taskNumber;
        if (isActive)
        {
            if (displayCount <= 0)
            {
                displayCount = taskTracker.getCurrentTaskTotal();
            }
            if (displayNumber <= 0)
            {
                displayNumber = taskTracker.getCurrentAssignmentNumber();
            }
        }

        // Look up the Task object for this history entry so we can open the task detail panel.
        Task clickableTask = taskService.get(entry.taskName);

        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        // Outer EmptyBorder = gap between rows; middle = solid box (green when active);
        // inner EmptyBorder = padding around row content.
        row.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(0, 0, 4, 0),
                BorderFactory.createCompoundBorder(
                        isActive
                                ? BorderFactory.createLineBorder(ACTIVE_BORDER_COLOR, 2)
                                : BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
                        BorderFactory.createEmptyBorder(8, 6, 8, 6))));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

        if (clickableTask != null)
        {
            row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            row.setToolTipText("View " + entry.taskName + " details");
            row.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent e)
                {
                    onTaskSelected.accept(clickableTask);
                }

                @Override
                public void mouseEntered(MouseEvent e)
                {
                    setBackground(row, ColorScheme.DARK_GRAY_COLOR);
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    setBackground(row, ColorScheme.DARKER_GRAY_COLOR);
                }
            });
        }

        // WEST: kill count + monster icon (icon vertically centered)
        JPanel leftPanel = new JPanel(new BorderLayout(2, 0));
        leftPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        leftPanel.setPreferredSize(new Dimension(58, 0));

        if (displayCount > 0)
        {
            JLabel countLabel = new JLabel(String.valueOf(displayCount));
            countLabel.setForeground(COUNT_COLOR);
            countLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            countLabel.setVerticalAlignment(SwingConstants.CENTER);
            countLabel.setHorizontalAlignment(SwingConstants.CENTER);
            countLabel.setPreferredSize(new Dimension(20, 0));
            leftPanel.add(countLabel, BorderLayout.WEST);
        }

        Task task = taskService.get(entry.taskName);
        if (task != null && task.image != null)
        {
            BufferedImage scaled = ImageUtil.resizeImage(task.image, ICON_SIZE, ICON_SIZE);
            JLabel iconLabel = new JLabel(new ImageIcon(scaled));
            iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
            iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            leftPanel.add(iconLabel, BorderLayout.CENTER);
        }
        row.add(leftPanel, BorderLayout.WEST);

        // CENTER: name, master, date — vertically centered as a block, horizontally centered
        JPanel centerContent = new JPanel();
        centerContent.setLayout(new BoxLayout(centerContent, BoxLayout.Y_AXIS));
        centerContent.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        JLabel nameLabel = new JLabel(entry.taskName != null ? entry.taskName : "");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(FontManager.getRunescapeFont().deriveFont(Font.BOLD));
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerContent.add(nameLabel);

        if (entry.master != null && !entry.master.isEmpty())
        {
            JLabel masterLabel = new JLabel(entry.master);
            masterLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            masterLabel.setFont(FontManager.getRunescapeSmallFont());
            masterLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerContent.add(masterLabel);
        }

        if (entry.timestamp > 0)
        {
            LocalDateTime dt = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(entry.timestamp), ZoneId.systemDefault());
            JLabel dateLabel = new JLabel(DATE_FMT.format(dt));
            dateLabel.setForeground(Color.WHITE);
            dateLabel.setFont(FontManager.getRunescapeSmallFont());
            dateLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerContent.add(dateLabel);
        }

        // Vertically center the block within the row
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        centerPanel.setBorder(new EmptyBorder(0, 4, 0, 4));
        centerPanel.add(centerContent);
        row.add(centerPanel, BorderLayout.CENTER);

        // EAST: task number (#31) stacked above a "Skipped" badge when applicable
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.Y_AXIS));
        eastPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        if (displayNumber > 0)
        {
            JLabel numLabel = new JLabel("#" + displayNumber);
            numLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            numLabel.setFont(FontManager.getRunescapeSmallFont());
            numLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            eastPanel.add(numLabel);
        }

        if (entry.skipped)
        {
            JLabel skippedLabel = new JLabel("Skipped");
            skippedLabel.setForeground(SKIPPED_COLOR);
            skippedLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD));
            skippedLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            eastPanel.add(skippedLabel);
        }

        if (taskCount > 0)
        {
            eastPanel.add(Box.createVerticalGlue());
            JLabel totalLabel = new JLabel(String.valueOf(taskCount));
            totalLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            totalLabel.setFont(FontManager.getRunescapeSmallFont());
            totalLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            eastPanel.add(totalLabel);
        }

        if (eastPanel.getComponentCount() > 0)
        {
            row.add(eastPanel, BorderLayout.EAST);
        }

        return row;
    }

    /** Recursively sets the background on a component and all its children. */
    private static void setBackground(Component c, Color color)
    {
        c.setBackground(color);
        if (c instanceof Container)
        {
            for (Component child : ((Container) c).getComponents())
            {
                setBackground(child, color);
            }
        }
    }
}
