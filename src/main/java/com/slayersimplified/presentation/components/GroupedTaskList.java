/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components;

import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.Task;
import com.slayersimplified.presentation.SlayerTaskRenderer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Displays all Slayer tasks grouped by Slayer Master, with collapsible sections
 * for each master. Sections default to collapsed and preserve their state across
 * task list refreshes.
 */
public class GroupedTaskList extends JPanel
{
    private final Consumer<Task> onTaskSelected;
    private final Map<SlayerMaster, Boolean> expandedStates = new EnumMap<>(SlayerMaster.class);

    public GroupedTaskList(Consumer<Task> onTaskSelected)
    {
        this.onTaskSelected = onTaskSelected;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
    }

    /**
     * Rebuilds the grouped sections from the provided task array.
     * Expanded/collapsed states are preserved across calls.
     * Must be called from any thread — switches to EDT internally.
     */
    public void setTasks(Task[] allTasks)
    {
        Map<SlayerMaster, List<Task>> byMaster = new EnumMap<>(SlayerMaster.class);
        for (SlayerMaster m : SlayerMaster.values())
        {
            byMaster.put(m, new ArrayList<>());
        }

        for (Task task : allTasks)
        {
            if (task.masters == null)
            {
                continue;
            }
            for (String masterName : task.masters)
            {
                SlayerMaster m = SlayerMaster.fromTaskMasterName(masterName);
                if (m != null && !byMaster.get(m).contains(task))
                {
                    byMaster.get(m).add(task);
                }
            }
        }

        for (List<Task> tasks : byMaster.values())
        {
            tasks.sort(Comparator.comparing(t -> t.name));
        }

        SwingUtilities.invokeLater(() ->
        {
            removeAll();
            for (SlayerMaster master : SlayerMaster.values())
            {
                List<Task> tasks = byMaster.get(master);
                if (!tasks.isEmpty())
                {
                    add(buildSection(master, tasks));
                }
            }
            add(Box.createVerticalGlue());
            revalidate();
            repaint();
        });
    }

    private JPanel buildSection(SlayerMaster master, List<Task> tasks)
    {
        // Override getMaximumSize so BoxLayout never stretches this section
        // taller than its actual content — critical for tight collapsed headers.
        JPanel section = new JPanel()
        {
            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        boolean expanded = expandedStates.getOrDefault(master, false);

        // ── Header row ──────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(6, 8, 6, 8)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel arrowLabel = new JLabel(expanded ? "\u25BC" : "\u25BA");
        arrowLabel.setFont(FontManager.getRunescapeSmallFont());
        arrowLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel nameLabel = new JLabel(master.getDisplayName() + "  (" + tasks.size() + ")");
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(
                Font.BOLD, FontManager.getRunescapeSmallFont().getSize2D() + 2f));
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        header.add(arrowLabel, BorderLayout.WEST);
        header.add(nameLabel, BorderLayout.CENTER);

        // ── Tasks container ─────────────────────────────────────────────────
        JPanel tasksPanel = new JPanel();
        tasksPanel.setLayout(new BoxLayout(tasksPanel, BoxLayout.Y_AXIS));
        tasksPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        tasksPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tasksPanel.setVisible(expanded);

        for (Task task : tasks)
        {
            tasksPanel.add(buildTaskRow(task));
        }

        // Toggle on header click
        MouseAdapter toggleAdapter = new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                boolean nowExpanded = !tasksPanel.isVisible();
                expandedStates.put(master, nowExpanded);
                tasksPanel.setVisible(nowExpanded);
                arrowLabel.setText(nowExpanded ? "\u25BC" : "\u25BA");
                revalidate();
                repaint();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                header.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        };
        header.addMouseListener(toggleAdapter);

        section.add(header);
        section.add(tasksPanel);
        return section;
    }

    private JPanel buildTaskRow(Task task)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setPreferredSize(new Dimension(0, 42));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(0, 5, 0, 0)
        ));

        JLabel nameLabel = new JLabel(task.name);
        nameLabel.setFont(FontManager.getRunescapeSmallFont()
                .deriveFont(FontManager.getRunescapeSmallFont().getSize2D() + 4f));
        nameLabel.setForeground(ColorScheme.TEXT_COLOR);
        nameLabel.setOpaque(false);

        JLabel iconLabel = new JLabel(SlayerTaskRenderer.getMonsterIcon(task.name));
        iconLabel.setBorder(new EmptyBorder(0, 0, 0, 4));
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(iconLabel, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                onTaskSelected.accept(task);
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
                nameLabel.setForeground(Color.WHITE);
                row.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                nameLabel.setForeground(ColorScheme.TEXT_COLOR);
                row.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
        });

        return row;
    }
}
