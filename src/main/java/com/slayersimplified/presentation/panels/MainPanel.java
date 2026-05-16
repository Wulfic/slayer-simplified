/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Main plugin panel. Adds a "Quick Navigate" button that auto-routes
 * based on the player's current slayer task state.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.Panel;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Main plugin panel showing either the task search list or the selected
 * task detail view. Contains a "Quick Navigate" button that:
 *   - No task → navigates to the configured preferred slayer master
 *   - Has task + favorite location → auto-navigates to that location
 *   - Has task + no favorite → opens the monster's locations tab
 */
@Slf4j
public class MainPanel extends PluginPanel
{
    private final TaskService taskService;
    private final NavigationService navigationService;
    private final LocationCoordinateService locationCoordinateService;
    private final SlayerTaskTracker taskTracker;
    private final FavoriteLocationService favoriteService;
    private final SlayerSimplifiedConfig config;
    private final MonsterNotesService notesService;

    private final TaskSearchPanel taskSearchPanel;
    private final TaskSelectedPanel taskSelectedPanel;

    private final Map<Panel, JPanel> panels = new HashMap<>();
    private final JPanel currentPanelContainer = new JPanel(new BorderLayout());
    private final JButton quickNavButton = new JButton("Quick Navigate");
    private final JButton cancelNavButton = new JButton("Cancel Navigation");

    /** Pinned row showing the active task name with a quick-nav button. */
    private final JPanel currentTaskPanel = new JPanel(new BorderLayout(6, 0));
    private final JLabel currentTaskLabel = new JLabel();
    private final JButton currentTaskNavButton = new JButton("Nav");

    @Inject
    public MainPanel(
            TaskService taskService,
            NavigationService navigationService,
            LocationCoordinateService locationCoordinateService,
            SlayerTaskTracker taskTracker,
            FavoriteLocationService favoriteService,
            SlayerSimplifiedConfig config,
            OkHttpClient okHttpClient,
            MonsterNotesService notesService)
    {
        super(false);
        this.taskService = taskService;
        this.navigationService = navigationService;
        this.locationCoordinateService = locationCoordinateService;
        this.taskTracker = taskTracker;
        this.favoriteService = favoriteService;
        this.config = config;
        this.notesService = notesService;

        this.taskSearchPanel = new TaskSearchPanel(this::onSearchBarChanged, this::onTaskSelected);
        this.taskSelectedPanel = new TaskSelectedPanel(
                this::onTaskClosed, navigationService, locationCoordinateService, favoriteService,
                okHttpClient, notesService);

        Task[] orderedTasks = taskService.getAll(Comparator.comparing(t -> t.name));
        taskSearchPanel.updateTaskList(orderedTasks);

        // Quick Navigate button styling — match RuneLite panel aesthetic
        quickNavButton.setFont(FontManager.getRunescapeSmallFont());
        quickNavButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        quickNavButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        quickNavButton.setFocusPainted(false);
        quickNavButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 0, 5, 0)
        ));
        quickNavButton.setToolTipText("Navigate based on current slayer task");
        quickNavButton.addActionListener(e -> quickNavigate());

        // Cancel Navigation button styling
        cancelNavButton.setFont(FontManager.getRunescapeSmallFont());
        cancelNavButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        cancelNavButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        cancelNavButton.setFocusPainted(false);
        cancelNavButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 0, 5, 0)
        ));
        cancelNavButton.setToolTipText("Clear the current navigation waypoint");
        cancelNavButton.addActionListener(e -> navigationService.clearNavigation());

        JPanel topButtonPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        topButtonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        topButtonPanel.add(quickNavButton);
        topButtonPanel.add(cancelNavButton);

        // Current-task banner: shows the active monster name with a Nav button
        currentTaskLabel.setFont(FontManager.getRunescapeSmallFont());
        currentTaskLabel.setForeground(new Color(255, 152, 0));
        currentTaskLabel.setToolTipText("Your current slayer task");

        currentTaskNavButton.setFont(FontManager.getRunescapeSmallFont());
        currentTaskNavButton.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
        currentTaskNavButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentTaskNavButton.setPreferredSize(new Dimension(50, 24));
        currentTaskNavButton.setMinimumSize(new Dimension(50, 24));
        currentTaskNavButton.setMaximumSize(new Dimension(50, 24));
        currentTaskNavButton.setFocusPainted(false);
        currentTaskNavButton.setMargin(new Insets(0, 2, 0, 2));
        currentTaskNavButton.setToolTipText("Navigate to current slayer task");
        currentTaskNavButton.addActionListener(e -> quickNavigate());

        currentTaskPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        currentTaskPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 4)));
        currentTaskPanel.add(currentTaskLabel, BorderLayout.CENTER);
        currentTaskPanel.add(currentTaskNavButton, BorderLayout.EAST);
        currentTaskPanel.setVisible(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        JPanel northWrapper = new JPanel(new GridBagLayout());
        northWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        gbc.gridy = 0;
        northWrapper.add(topButtonPanel, gbc);
        gbc.gridy = 1;
        northWrapper.add(currentTaskPanel, gbc);

        setLayout(new BorderLayout(0, 0));

        panels.put(Panel.TASK_SEARCH, taskSearchPanel);
        panels.put(Panel.TASK_SELECTED, taskSelectedPanel);

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        currentPanelContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(northWrapper, BorderLayout.NORTH);
        add(currentPanelContainer, BorderLayout.CENTER);
        showPanel(Panel.TASK_SEARCH);

        // Catch-all: consume any mouse wheel events that bubble up from child
        // components without their own scroll handling (e.g. WikiTab, tab headers).
        // This prevents events from reaching the RuneLite game canvas and
        // triggering unwanted camera zoom.
        addMouseWheelListener(MainPanel::consumeMouseWheel);
        currentPanelContainer.addMouseWheelListener(MainPanel::consumeMouseWheel);
    }

    private static void consumeMouseWheel(MouseWheelEvent e)
    {
        e.consume();
    }

    public void shutDown()
    {
        navigationService.clearNavigation();
        taskSearchPanel.shutDown();
        taskSelectedPanel.shutDown();
    }

    /**
     * Refreshes the current-task banner. Call on the EDT whenever the active
     * task may have changed (plugin start, new task assigned, task completed).
     */
    public void refreshCurrentTask()
    {
        String taskName = taskTracker.getCurrentTaskName();
        boolean hasTask = taskName != null && !taskName.isEmpty();
        currentTaskLabel.setText(hasTask ? taskName : "");
        currentTaskPanel.setVisible(hasTask);
        revalidate();
        repaint();
    }

    /**
     * Shows a non-modal reminder popup for the given task if it has required
     * items or custom notes saved. Does nothing if neither is present.
     * Must be called on the EDT.
     */
    public void showTaskReminderIfNeeded(String taskName)
    {
        if (taskName == null || taskName.isEmpty())
        {
            return;
        }

        Task task = taskService.get(taskName);
        if (task == null)
        {
            Task[] matches = taskService.searchPartialName(taskName);
            if (matches.length > 0)
            {
                task = matches[0];
            }
        }
        if (task == null)
        {
            return;
        }

        boolean hasItems = task.itemsRequired != null
                && Arrays.stream(task.itemsRequired)
                         .anyMatch(s -> s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("none"));
        String notes = notesService.getNotes(task.name);
        boolean hasNotes = !notes.isEmpty();

        if (!hasItems && !hasNotes)
        {
            return;
        }

        buildAndShowReminderDialog(task, hasItems, notes, hasNotes);
    }

    private void buildAndShowReminderDialog(Task task, boolean hasItems, String notes, boolean hasNotes)
    {
        JDialog dialog = new JDialog();
        dialog.setTitle(task.name);
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(new EmptyBorder(12, 14, 10, 14));

        if (hasItems)
        {
            JLabel header = new JLabel("Required Items");
            header.setForeground(new Color(255, 152, 0));
            header.setFont(FontManager.getRunescapeBoldFont());
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(header);
            content.add(Box.createVerticalStrut(4));

            for (String item : task.itemsRequired)
            {
                if (item != null && !item.trim().isEmpty() && !item.equalsIgnoreCase("none"))
                {
                    JLabel itemLabel = new JLabel("\u2022  " + item);
                    itemLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                    itemLabel.setFont(FontManager.getRunescapeSmallFont());
                    itemLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    content.add(itemLabel);
                }
            }
        }

        if (hasItems && hasNotes)
        {
            content.add(Box.createVerticalStrut(10));
            JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
            sep.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            sep.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(sep);
            content.add(Box.createVerticalStrut(10));
        }

        if (hasNotes)
        {
            JLabel header = new JLabel("Your Notes");
            header.setForeground(new Color(255, 152, 0));
            header.setFont(FontManager.getRunescapeBoldFont());
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(header);
            content.add(Box.createVerticalStrut(4));

            JTextArea notesArea = new JTextArea(notes);
            notesArea.setEditable(false);
            notesArea.setWrapStyleWord(true);
            notesArea.setLineWrap(true);
            notesArea.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            notesArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            notesArea.setFont(FontManager.getRunescapeSmallFont());
            notesArea.setBorder(new EmptyBorder(4, 6, 4, 6));
            notesArea.setAlignmentX(Component.LEFT_ALIGNMENT);
            notesArea.setPreferredSize(new Dimension(280, 80));
            notesArea.setMaximumSize(new Dimension(280, Integer.MAX_VALUE));
            content.add(notesArea);
        }

        content.add(Box.createVerticalStrut(12));

        JButton okButton = new JButton("OK");
        okButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        okButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        okButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        okButton.setFocusPainted(false);
        okButton.addActionListener(e -> dialog.dispose());
        content.add(okButton);

        dialog.add(content);
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    /**
     * Smart navigation based on current slayer task state:
     * 1. No task → navigate to preferred slayer master
     * 2. Has task + favorite set → navigate to favorite location
     * 3. Has task + no favorite → open that monster's locations tab
     *
     * Public so it can be triggered from both the UI button and the !task chat command.
     */
    public void quickNavigate()
    {
        String taskName = taskTracker.getCurrentTaskName();
        log.debug("Quick Nav: currentTaskName='{}'", taskName);

        if (taskName == null)
        {
            // No known task — navigate to preferred slayer master
            SlayerMaster master = config.preferredMaster();
            navigationService.navigateTo(master.getWorldPoint());
            log.debug("Quick Nav: no task, navigating to {}", master.getDisplayName());
            return;
        }

        // Find the matching task in our data
        Task task = taskService.get(taskName);
        log.debug("Quick Nav: exact match for '{}' = {}", taskName, task != null ? task.name : "null");
        if (task == null)
        {
            // Try partial match as fallback
            Task[] matches = taskService.searchPartialName(taskName);
            log.debug("Quick Nav: partial matches for '{}' = {}", taskName, matches.length);
            if (matches.length > 0)
            {
                task = matches[0];
                log.debug("Quick Nav: using partial match '{}'", task.name);
            }
        }

        if (task == null)
        {
            log.warn("Quick Nav: could not find task matching '{}'", taskName);
            return;
        }

        // Check for a favorite location
        String favLocation = favoriteService.getFavorite(task.name);
        log.debug("Quick Nav: favorite for '{}' = '{}'", task.name, favLocation);
        if (favLocation != null)
        {
            WorldPoint coords = locationCoordinateService.getCoordinates(favLocation);
            log.debug("Quick Nav: coords for '{}' = {}", favLocation, coords);
            if (coords != null)
            {
                navigationService.navigateTo(coords);
                log.debug("Quick Nav: navigating to favorite '{}' for {}", favLocation, task.name);
                return;
            }
            else
            {
                log.warn("Quick Nav: favorite '{}' has no coordinates!", favLocation);
            }
        }

        // No favorite — open the monster detail with Locations tab selected
        taskSelectedPanel.update(task);
        showPanel(Panel.TASK_SELECTED);
        SwingUtilities.invokeLater(() -> taskSelectedPanel.selectLocationsTab());
        log.debug("Quick Nav: showing locations for {} (no favorite set)", task.name);
    }

    private void onSearchBarChanged(String searchTerm)
    {
        Task[] matchedTasks = searchTerm.isBlank()
                ? taskService.getAll(Comparator.comparing(t -> t.name))
                : taskService.searchPartialName(searchTerm.trim());

        taskSearchPanel.updateTaskList(matchedTasks);
    }

    private void onTaskSelected(Task task)
    {
        taskSelectedPanel.update(task);
        showPanel(Panel.TASK_SELECTED);
    }

    private void onTaskClosed()
    {
        navigationService.clearNavigation();
        showPanel(Panel.TASK_SEARCH);
    }

    private void showPanel(Panel panel)
    {
        SwingUtilities.invokeLater(() ->
        {
            currentPanelContainer.removeAll();
            currentPanelContainer.add(panels.get(panel), BorderLayout.CENTER);
            currentPanelContainer.revalidate();
            currentPanelContainer.repaint();

            // Force the full component tree to lay out immediately so that
            // nested JScrollPanes (LootTab, InfoTab, etc.) get correct sizes
            // and their scrollbars appear without requiring a manual resize.
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null)
            {
                window.revalidate();
                window.repaint();
            }
        });
    }
}
