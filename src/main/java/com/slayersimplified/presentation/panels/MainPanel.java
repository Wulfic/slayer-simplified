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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    /** Pinned row showing the active task name with a quick-nav button. */
    private final JPanel currentTaskPanel = new JPanel(new BorderLayout(4, 0));
    private final JButton currentTaskButton = new JButton()
    {
        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color bg = getModel().isArmed()
                    ? new Color(28, 28, 28)
                    : (getModel().isRollover() ? new Color(50, 50, 50) : new Color(38, 38, 38));
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(60, 60, 60));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
        }
    };
    private final JButton currentTaskNavButton = new JButton("Nav")
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
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
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
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
        }
    };

    private final JButton cancelNavButton = new JButton("\u2715")
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
            Color base = hovered ? new Color(160, 55, 55) : getBackground();
            g2.setColor(getModel().isArmed() ? base.darker() : base);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
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
            g2.setColor(hovered ? new Color(215, 105, 105) : new Color(190, 80, 80));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
        }
    };

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

        this.taskSearchPanel = new TaskSearchPanel(this::onSearchBarChanged, this::onLocationSearchChanged, this::onTaskSelected);
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

        // Gear icon button — opens inline settings (rounded)
        JButton gearButton = new JButton("\u2699")
        {
            boolean hovered;
            float   angle = 0f;
            Timer   spinTimer;
            {
                spinTimer = new Timer(16, e -> { angle = (angle + 6f) % 360f; repaint(); });
                addMouseListener(new MouseAdapter()
                {
                    @Override public void mouseEntered(MouseEvent e) { hovered = true;  spinTimer.start(); repaint(); }
                    @Override public void mouseExited(MouseEvent e)  { hovered = false; spinTimer.stop(); angle = 0f; repaint(); }
                });
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Color base = hovered ? new Color(50, 50, 50) : getBackground();
                g2.setColor(getModel().isArmed() ? base.darker() : base);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setFont(getFont());
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.rotate(Math.toRadians(angle), cx, cy);
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
                g2.setColor(hovered ? new Color(100, 100, 100) : new Color(75, 75, 75));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        gearButton.setFont(gearButton.getFont().deriveFont(Font.BOLD, 14f));
        gearButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        gearButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        gearButton.setFocusPainted(false);
        gearButton.setContentAreaFilled(false);
        gearButton.setOpaque(false);
        gearButton.setPreferredSize(new Dimension(24, 24));
        gearButton.setMinimumSize(new Dimension(24, 24));
        gearButton.setMargin(new Insets(0, 0, 0, 0));
        gearButton.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        gearButton.setHorizontalAlignment(SwingConstants.CENTER);
        gearButton.setVerticalAlignment(SwingConstants.CENTER);
        gearButton.setToolTipText("Plugin settings");

        // Cancel nav button — rounded red X, lives in the task banner row
        cancelNavButton.setFont(cancelNavButton.getFont().deriveFont(Font.BOLD, 11f));
        cancelNavButton.setBackground(new Color(130, 40, 40));
        cancelNavButton.setForeground(Color.WHITE);
        cancelNavButton.setFocusPainted(false);
        cancelNavButton.setContentAreaFilled(false);
        cancelNavButton.setOpaque(false);
        cancelNavButton.setPreferredSize(new Dimension(22, 22));
        cancelNavButton.setMinimumSize(new Dimension(22, 22));
        cancelNavButton.setMaximumSize(new Dimension(22, 22));
        cancelNavButton.setMargin(new Insets(0, 0, 0, 0));
        cancelNavButton.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        cancelNavButton.setHorizontalAlignment(SwingConstants.CENTER);
        cancelNavButton.setVerticalAlignment(SwingConstants.CENTER);
        cancelNavButton.setToolTipText("Clear the current navigation waypoint");
        cancelNavButton.addActionListener(e -> navigationService.clearNavigation());

        // Single header row: [History (full width) | Gear]
        JPanel topButtonPanel = new JPanel(new BorderLayout(0, 0));
        topButtonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Current-task banner: monster name as a full-width clickable button
        currentTaskButton.setFont(FontManager.getRunescapeSmallFont());
        currentTaskButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentTaskButton.setText(config.preferredMaster().getDisplayName());
        currentTaskButton.setHorizontalAlignment(SwingConstants.CENTER);
        currentTaskButton.setVerticalAlignment(SwingConstants.CENTER);
        currentTaskButton.setToolTipText("Click to view task details");
        currentTaskButton.setFocusPainted(false);
        currentTaskButton.setContentAreaFilled(false);
        currentTaskButton.setOpaque(false);
        currentTaskButton.setMargin(new Insets(0, 0, 0, 0));
        currentTaskButton.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
        currentTaskButton.addActionListener(e ->
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
        });

        currentTaskNavButton.setFont(FontManager.getRunescapeSmallFont());
        currentTaskNavButton.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
        currentTaskNavButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        currentTaskNavButton.setPreferredSize(new Dimension(50, 22));
        currentTaskNavButton.setMinimumSize(new Dimension(50, 22));
        currentTaskNavButton.setMaximumSize(new Dimension(50, 22));
        currentTaskNavButton.setFocusPainted(false);
        currentTaskNavButton.setContentAreaFilled(false);
        currentTaskNavButton.setOpaque(false);
        currentTaskNavButton.setMargin(new Insets(0, 0, 0, 0));
        currentTaskNavButton.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        currentTaskNavButton.setHorizontalAlignment(SwingConstants.CENTER);
        currentTaskNavButton.setVerticalAlignment(SwingConstants.CENTER);
        currentTaskNavButton.setToolTipText("Navigate to current slayer task");
        currentTaskNavButton.addActionListener(e -> quickNavigate());

        currentTaskPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        currentTaskPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 3, 4));

        JPanel navButtonGroup = new JPanel();
        navButtonGroup.setLayout(new BoxLayout(navButtonGroup, BoxLayout.X_AXIS));
        navButtonGroup.setOpaque(false);
        navButtonGroup.add(cancelNavButton);
        navButtonGroup.add(Box.createHorizontalStrut(4));
        navButtonGroup.add(currentTaskNavButton);

        currentTaskPanel.add(currentTaskButton, BorderLayout.CENTER);
        currentTaskPanel.add(navButtonGroup, BorderLayout.EAST);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        JPanel northWrapper = new JPanel(new GridBagLayout());
        northWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // History pill button fills the top row
        JButton historyButton = new JButton("History")
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isArmed()
                        ? ColorScheme.DARKER_GRAY_COLOR.darker()
                        : (getModel().isRollover() ? new Color(45, 45, 45) : ColorScheme.DARKER_GRAY_COLOR);
                g2.setColor(bg);
                int arc = 10;
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            protected void paintBorder(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(65, 65, 65));
                int arc = 10;
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                g2.dispose();
            }
        };
        historyButton.setFont(FontManager.getRunescapeSmallFont());
        historyButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        historyButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        historyButton.setFocusPainted(false);
        historyButton.setContentAreaFilled(false);
        historyButton.setOpaque(false);
        historyButton.setToolTipText("View your slayer task history");

        // Wrap history pill with padding so it floats inside the header bar
        JPanel historyWrapper = new JPanel(new BorderLayout());
        historyWrapper.setOpaque(false);
        historyWrapper.setBorder(BorderFactory.createEmptyBorder(3, 6, 2, 6));
        historyWrapper.add(historyButton);

        // Gear wrapper: left divider line + padding
        JPanel gearWrapper = new JPanel(new BorderLayout());
        gearWrapper.setOpaque(false);
        gearWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(20, 20, 20)),
                BorderFactory.createEmptyBorder(3, 4, 2, 4)));
        gearWrapper.add(gearButton);

        // Full-width bottom separator on the panel itself
        topButtonPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(20, 20, 20)));
        topButtonPanel.add(historyWrapper, BorderLayout.CENTER);
        topButtonPanel.add(gearWrapper, BorderLayout.EAST);

        gbc.gridy = 0;
        northWrapper.add(topButtonPanel, gbc);
        gbc.gridy = 1;
        northWrapper.add(currentTaskPanel, gbc);

        setLayout(new BorderLayout(0, 0));
        setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50), 2));

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
            currentTaskButton.setText(taskName);
            currentTaskButton.setForeground(new Color(255, 152, 0));
            currentTaskButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            currentTaskNavButton.setToolTipText("Navigate to current slayer task");
        }
        else
        {
            SlayerMaster effectiveMaster = getEffectiveMaster();
            String masterName = effectiveMaster.getDisplayName();
            currentTaskButton.setText(masterName);
            if (config.streakOptimizerEnabled())
            {
                currentTaskButton.setForeground(new Color(100, 180, 255));
                currentTaskButton.setToolTipText(optimizerService.getRecommendationReason());
                currentTaskNavButton.setToolTipText(
                        "Navigate to " + masterName + " (Streak Optimizer)");
            }
            else
            {
                currentTaskButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                currentTaskButton.setToolTipText(null);
                currentTaskNavButton.setToolTipText("Navigate to " + masterName);
            }
            currentTaskButton.setCursor(Cursor.getDefaultCursor());
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

        com.slayersimplified.domain.TaskSearchResult[] results =
                taskService.searchWithVariants(searchTerm.trim());
        if (!config.debugCoordinates())
        {
            results = Arrays.stream(results)
                    .filter(r -> !"A DEBUG TASK".equals(r.parentTask.name))
                    .toArray(com.slayersimplified.domain.TaskSearchResult[]::new);
        }
        if (!config.showNonSlayerEnemies())
        {
            results = Arrays.stream(results)
                    .filter(r -> !isNonSlayerTask(r.parentTask))
                    .toArray(com.slayersimplified.domain.TaskSearchResult[]::new);
        }

        taskSearchPanel.showSearchResults(results);
    }

    private void onLocationSearchChanged(String searchTerm)
    {
        if (searchTerm.isBlank())
        {
            taskSearchPanel.showGroupedView();
            return;
        }

        com.slayersimplified.domain.TaskSearchResult[] results =
                taskService.searchByLocation(searchTerm.trim());
        if (!config.debugCoordinates())
        {
            results = Arrays.stream(results)
                    .filter(r -> !"A DEBUG TASK".equals(r.parentTask.name))
                    .toArray(com.slayersimplified.domain.TaskSearchResult[]::new);
        }
        if (!config.showNonSlayerEnemies())
        {
            results = Arrays.stream(results)
                    .filter(r -> !isNonSlayerTask(r.parentTask))
                    .toArray(com.slayersimplified.domain.TaskSearchResult[]::new);
        }

        taskSearchPanel.showSearchResults(results);
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
