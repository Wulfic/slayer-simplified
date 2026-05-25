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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import okhttp3.OkHttpClient;

import javax.inject.Inject;
import javax.swing.*;
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
    private final SlayerStreakOptimizerService optimizerService;

    private final TaskSearchPanel taskSearchPanel;
    private final TaskSelectedPanel taskSelectedPanel;
    private SlayerHistoryPanel historyPanel;

    /** The task currently displayed in TaskSelectedPanel, or null if not showing. */
    private Task currentlySelectedTask;

    private final Map<Panel, JPanel> panels = new HashMap<>();
    private final JPanel currentPanelContainer = new JPanel(new CardLayout());
    private final JButton quickNavButton = new JButton("Quick Navigate");

    /** Pinned row showing the active task name with a quick-nav button. */
    private final JPanel currentTaskPanel = new JPanel(new BorderLayout(6, 0));
    private final JLabel currentTaskLabel = new JLabel();
    private final JButton currentTaskNavButton = new JButton("Nav");

    private final ConfigManager configManager;

    @Inject
    public MainPanel(
            TaskService taskService,
            NavigationService navigationService,
            LocationCoordinateService locationCoordinateService,
            LocationRequirementService requirementService,
            SlayerTaskTracker taskTracker,
            FavoriteLocationService favoriteService,
            SlayerSimplifiedConfig config,
            OkHttpClient okHttpClient,
            MonsterNotesService notesService,
            SlayerHistoryService historyService,
            SlayerStreakOptimizerService optimizerService,
            ConfigManager configManager)
    {
        super(false);
        this.taskService = taskService;
        this.navigationService = navigationService;
        this.locationCoordinateService = locationCoordinateService;
        this.taskTracker = taskTracker;
        this.favoriteService = favoriteService;
        this.config = config;
        this.optimizerService = optimizerService;
        this.configManager = configManager;

        this.taskSearchPanel = new TaskSearchPanel(this::onSearchBarChanged, this::onTaskSelected);
        this.taskSelectedPanel = new TaskSelectedPanel(
                this::onTaskClosed, navigationService, locationCoordinateService, favoriteService,
                requirementService, okHttpClient, notesService, this::refreshTaskReminder, config::debugCoordinates);

        Task[] orderedTasks = taskService.getAll(Comparator.comparing(t -> t.name));
        if (!config.debugCoordinates())
        {
            orderedTasks = Arrays.stream(orderedTasks)
                    .filter(t -> !"A DEBUG TASK".equals(t.name))
                    .toArray(Task[]::new);
        }
        if (!config.showNonSlayerEnemies())
        {
            orderedTasks = Arrays.stream(orderedTasks)
                    .filter(t -> !isNonSlayerTask(t))
                    .toArray(Task[]::new);
        }
        taskSearchPanel.setAllTasks(orderedTasks);

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

        // Gear icon button — opens inline settings
        JButton gearButton = new JButton("\u2699");
        gearButton.setFont(gearButton.getFont().deriveFont(Font.BOLD, 14f));
        gearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gearButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gearButton.setFocusPainted(false);
        gearButton.setPreferredSize(new Dimension(28, 0));
        gearButton.setMinimumSize(new Dimension(28, 0));
        gearButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 2, 5, 2)
        ));
        gearButton.setToolTipText("Plugin settings");

        // Cancel (X) button — red square to the left of gear
        JButton cancelXButton = new JButton("\u2715");
        cancelXButton.setFont(cancelXButton.getFont().deriveFont(Font.BOLD, 11f));
        cancelXButton.setBackground(new Color(160, 50, 50));
        cancelXButton.setForeground(Color.WHITE);
        cancelXButton.setFocusPainted(false);
        cancelXButton.setPreferredSize(new Dimension(28, 0));
        cancelXButton.setMinimumSize(new Dimension(28, 0));
        cancelXButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 2, 5, 2)
        ));
        cancelXButton.setToolTipText("Clear the current navigation waypoint");
        cancelXButton.addActionListener(e -> navigationService.clearNavigation());

        // Right-side icon buttons: [X][Gear]
        JPanel iconButtons = new JPanel(new GridLayout(1, 2, 0, 0));
        iconButtons.setBackground(ColorScheme.DARK_GRAY_COLOR);
        iconButtons.add(cancelXButton);
        iconButtons.add(gearButton);

        // Single header row: [History | Quick Navigate | X | Gear]
        JPanel topButtonPanel = new JPanel(new BorderLayout(2, 0));
        topButtonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Current-task banner: shows the active monster name with a Nav button
        currentTaskLabel.setFont(FontManager.getRunescapeSmallFont());
        currentTaskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentTaskLabel.setText(config.preferredMaster().getDisplayName());
        currentTaskLabel.setToolTipText("Click to view task details");
        currentTaskLabel.setCursor(Cursor.getDefaultCursor());
        currentTaskLabel.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                String name = taskTracker.getCurrentTaskName();
                if (name == null || name.isEmpty())
                {
                    return;
                }
                Task t = taskService.get(name);
                if (t == null)
                {
                    Task[] matches = taskService.searchPartialName(name);
                    if (matches.length > 0)
                    {
                        t = matches[0];
                    }
                }
                if (t != null)
                {
                    onTaskSelected(t);
                }
            }
        });

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

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        JPanel northWrapper = new JPanel(new GridBagLayout());
        northWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // History button — same styling as Quick Navigate, placed to its left
        JButton historyButton = new JButton("History");
        historyButton.setFont(FontManager.getRunescapeSmallFont());
        historyButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        historyButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        historyButton.setFocusPainted(false);
        historyButton.setPreferredSize(new Dimension(70, 0));
        historyButton.setMinimumSize(new Dimension(70, 0));
        historyButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(5, 0, 5, 0)));
        historyButton.setToolTipText("View your slayer task history");
        topButtonPanel.add(historyButton, BorderLayout.WEST);
        topButtonPanel.add(quickNavButton, BorderLayout.CENTER);
        topButtonPanel.add(iconButtons, BorderLayout.EAST);

        gbc.gridy = 0;
        northWrapper.add(topButtonPanel, gbc);
        gbc.gridy = 1;
        northWrapper.add(currentTaskPanel, gbc);

        setLayout(new BorderLayout(0, 0));

        SettingsPanel settingsPanel = new SettingsPanel(config, () -> showPanel(Panel.TASK_SEARCH));
        gearButton.addActionListener(e ->
        {
            settingsPanel.refresh();
            showPanel(Panel.SETTINGS);
        });

        SlayerHistoryPanel historyPanel = new SlayerHistoryPanel(
                historyService, taskService, taskTracker,
                () -> showPanel(Panel.TASK_SEARCH),
                this::showTask);
        this.historyPanel = historyPanel;
        historyButton.addActionListener(e ->
        {
            historyPanel.refresh();
            showPanel(Panel.HISTORY);
        });

        panels.put(Panel.TASK_SEARCH, taskSearchPanel);
        panels.put(Panel.TASK_SELECTED, taskSelectedPanel);
        panels.put(Panel.SETTINGS, settingsPanel);
        panels.put(Panel.HISTORY, historyPanel);

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        currentPanelContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Add all panels to the CardLayout container once; switching is free
        for (Map.Entry<Panel, JPanel> entry : panels.entrySet())
        {
            currentPanelContainer.add(entry.getValue(), entry.getKey().name());
        }

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

    /** Refreshes the KC badge in the task detail header if a task is currently displayed. */
    public void refreshKc()
    {
        if (currentlySelectedTask != null)
        {
            taskSelectedPanel.update(currentlySelectedTask, getKillCount(currentlySelectedTask.name));
        }
    }

    /** Refreshes the history panel. Safe to call from any Swing thread. */
    public void refreshHistory()
    {
        if (historyPanel != null)
        {
            historyPanel.refresh();
        }
    }

    /**
     * Refreshes the current-task banner. Call on the EDT whenever the active
     * task may have changed (plugin start, new task assigned, task completed).
     */
    public void refreshCurrentTask()
    {
        String taskName = taskTracker.getCurrentTaskName();
        boolean hasTask = taskName != null && !taskName.isEmpty();
        if (hasTask)
        {
            currentTaskLabel.setText(taskName);
            currentTaskLabel.setForeground(new Color(255, 152, 0));
            currentTaskLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            currentTaskNavButton.setToolTipText("Navigate to current slayer task");
        }
        else
        {
            SlayerMaster effectiveMaster = getEffectiveMaster();
            String masterName = effectiveMaster.getDisplayName();
            currentTaskLabel.setText(masterName);
            if (config.streakOptimizerEnabled())
            {
                currentTaskLabel.setForeground(new Color(100, 180, 255));
                currentTaskLabel.setToolTipText(optimizerService.getRecommendationReason());
                currentTaskNavButton.setToolTipText(
                        "Navigate to " + masterName + " (Streak Optimizer)");
            }
            else
            {
                currentTaskLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                currentTaskLabel.setToolTipText(null);
                currentTaskNavButton.setToolTipText("Navigate to " + masterName);
            }
            currentTaskLabel.setCursor(Cursor.getDefaultCursor());
        }
        currentTaskPanel.setVisible(true);
        revalidate();
        repaint();
        refreshTaskReminder();
    }

    /**
     * Shows a one-time popup if the Slayer cape reminder is enabled (99 Slayer).
     * Notes and required items are displayed in the persistent live window
     * managed by {@link #refreshTaskReminder()}.
     * Must be called on the EDT.
     */
    public void showTaskReminderIfNeeded(String taskName, boolean remindCape)
    {
        if (!remindCape)
        {
            return;
        }

        JDialog dialog = new JDialog();
        dialog.setTitle(taskName + " \u2014 Reminder");
        dialog.setModal(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));

        JLabel capeHeader = new JLabel("Slayer Cape");
        capeHeader.setForeground(new Color(255, 152, 0));
        capeHeader.setFont(FontManager.getRunescapeBoldFont());
        capeHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(capeHeader);
        content.add(Box.createVerticalStrut(4));

        JLabel capeMsg = new JLabel("\u2022  You have 99 Slayer \u2014 bring your Slayer cape!");
        capeMsg.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        capeMsg.setFont(FontManager.getRunescapeSmallFont());
        capeMsg.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(capeMsg);
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

    /** No-op — reminder content is now rendered by {@link com.slayersimplified.presentation.TaskReminderOverlay}. */
    public void refreshTaskReminder()
    {
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
            // No known task — navigate to the effective master
            SlayerMaster master = getEffectiveMaster();
            navigationService.navigateTo(master.getWorldPoint());
            log.debug("Quick Nav: no task, navigating to {} (optimizer={})",
                    master.getDisplayName(), config.streakOptimizerEnabled());
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
        currentlySelectedTask = task;
        taskSelectedPanel.update(task, getKillCount(task.name));
        showPanel(Panel.TASK_SELECTED);
        SwingUtilities.invokeLater(() -> taskSelectedPanel.selectLocationsTab());
        log.debug("Quick Nav: showing locations for {} (no favorite set)", task.name);
    }

    /**
     * Returns the Slayer master to navigate to when the player has no active task.
     * If the Streak Point Optimizer is enabled, the optimizer's recommendation is
     * returned; otherwise falls back to {@link SlayerSimplifiedConfig#preferredMaster()}.
     */
    private SlayerMaster getEffectiveMaster()
    {
        if (config.streakOptimizerEnabled())
        {
            return optimizerService.getRecommendedMaster();
        }
        return config.preferredMaster();
    }

    private static boolean isNonSlayerTask(Task t)
    {
        if (t.masters == null)
        {
            return false;
        }
        for (String m : t.masters)
        {
            if (SlayerMaster.NON_SLAYER_ENEMIES.getDisplayName().equals(m)
                    || SlayerMaster.ANIMALS.getDisplayName().equals(m)
                    || SlayerMaster.BOSSES.getDisplayName().equals(m))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Re-renders the currently selected task's detail panel (if any).
     * Called when debug mode changes so LocationsTab re-evaluates row colours.
     */
    public void refreshSelectedTask()
    {
        SwingUtilities.invokeLater(() ->
        {
            if (currentlySelectedTask != null)
            {
                taskSelectedPanel.update(currentlySelectedTask, getKillCount(currentlySelectedTask.name));
            }
        });
    }

    /** Rebuilds the task search list, applying the current locationDebug filter. Safe to call from any thread. */
    public void refreshTaskList()
    {
        SwingUtilities.invokeLater(() ->
        {
            Task[] tasks = taskService.getAll(Comparator.comparing(t -> t.name));
            if (!config.debugCoordinates())
            {
                tasks = Arrays.stream(tasks)
                        .filter(t -> !"A DEBUG TASK".equals(t.name))
                        .toArray(Task[]::new);
            }
            if (!config.showNonSlayerEnemies())
            {
                tasks = Arrays.stream(tasks)
                        .filter(t -> !isNonSlayerTask(t))
                        .toArray(Task[]::new);
            }
            taskSearchPanel.setAllTasks(tasks);
        });
    }

    private void onSearchBarChanged(String searchTerm)
    {
        if (searchTerm.isBlank())
        {
            taskSearchPanel.showGroupedView();
            return;
        }

        Task[] matchedTasks = taskService.searchPartialName(searchTerm.trim());
        if (!config.debugCoordinates())
        {
            matchedTasks = Arrays.stream(matchedTasks)
                    .filter(t -> !"A DEBUG TASK".equals(t.name))
                    .toArray(Task[]::new);
        }
        if (!config.showNonSlayerEnemies())
        {
            matchedTasks = Arrays.stream(matchedTasks)
                    .filter(t -> !isNonSlayerTask(t))
                    .toArray(Task[]::new);
        }

        taskSearchPanel.showSearchResults(matchedTasks);
    }

    /** Opens the task detail panel for the given task. Safe to call from the EDT. */
    public void showTask(Task task)
    {
        onTaskSelected(task);
    }

    private void onTaskSelected(Task task)
    {
        currentlySelectedTask = task;
        taskSelectedPanel.update(task, getKillCount(task.name));
        showPanel(Panel.TASK_SELECTED);
    }

    private void onTaskClosed()
    {
        currentlySelectedTask = null;
        navigationService.clearNavigation();
        showPanel(Panel.TASK_SEARCH);
    }

    private int getKillCount(String taskName)
    {
        if (taskName == null || taskName.isEmpty())
        {
            return 0;
        }
        String key = "kc_" + taskName.toLowerCase().replace(" ", "_");
        String stored = configManager.getConfiguration(SlayerSimplifiedConfig.CONFIG_GROUP, key);
        if (stored == null)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(stored);
        }
        catch (NumberFormatException ignored)
        {
            return 0;
        }
    }

    private void showPanel(Panel panel)
    {
        SwingUtilities.invokeLater(() ->
        {
            ((CardLayout) currentPanelContainer.getLayout()).show(currentPanelContainer, panel.name());
        });
    }
}
