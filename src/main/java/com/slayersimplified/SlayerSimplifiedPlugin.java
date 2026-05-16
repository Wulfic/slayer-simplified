/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Main plugin entry point for Slayer Simplified. Extends the Slayer Assistant
 * plugin with navigate-to-location functionality via the Shortest Path plugin's
 * PluginMessage API. Tracks the current slayer task via chat message parsing
 * and provides a Quick Navigate feature.
 */
package com.slayersimplified;

import com.google.inject.Binder;
import com.google.inject.Provides;
import com.slayersimplified.domain.Icon;
import com.slayersimplified.domain.SlayerMaster;
import com.slayersimplified.domain.TaskHistoryEntry;
import com.slayersimplified.modules.TaskServiceModule;
import com.slayersimplified.presentation.CoordinatesOverlay;
import com.slayersimplified.presentation.TaskReminderOverlay;
import com.slayersimplified.presentation.panels.MainPanel;
import com.slayersimplified.presentation.SlayerTargetOverlay;
import com.slayersimplified.services.SlayerHistoryService;
import com.slayersimplified.services.SlayerTaskTracker;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.SwingUtilities;

@Slf4j
@PluginDescriptor(
        name = "Slayer Simplified",
        description = "Slayer task assistant with Shortest Path navigation integration",
        tags = {"slay", "slayer", "navigation", "path", "shortest"}
)
public class SlayerSimplifiedPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private MainPanel mainPanel;

    @Inject
    private SlayerTaskTracker taskTracker;

    @Inject
    private SlayerSimplifiedConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private SlayerTargetOverlay targetOverlay;

    @Inject
    private CoordinatesOverlay coordinatesOverlay;

    @Inject
    private TaskReminderOverlay taskReminderOverlay;

    @Inject
    private SlayerHistoryService historyService;

    @Inject
    private ConfigManager configManager;

    private NavigationButton navButton;

    /** Set when the player types !task; cleared when the game response triggers navigation. */
    private volatile boolean pendingTaskNavigation = false;

    /** Name of the most recently interacted slayer master NPC, for history attribution. */
    private String lastInteractedMasterName = "";

    @Override
    public void configure(Binder binder)
    {
        binder.install(new TaskServiceModule());
    }

    @Provides
    SlayerSimplifiedConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(SlayerSimplifiedConfig.class);
    }

    @Override
    protected void startUp()
    {
        navButton = NavigationButton.builder()
                .tooltip("Slayer Simplified")
                .icon(Icon.PLUGIN_ICON.getImage())
                .priority(10)
                .panel(mainPanel)
                .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(targetOverlay);
        overlayManager.add(coordinatesOverlay);
        overlayManager.add(taskReminderOverlay);
        SwingUtilities.invokeLater(mainPanel::refreshCurrentTask);
        // Populate the NPC highlight set in case the plugin is enabled while already logged in.
        clientThread.invokeLater(targetOverlay::onTaskChanged);
        log.info("Slayer Simplified started");
    }

    @Override
    protected void shutDown()
    {
        pendingTaskNavigation = false;
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(targetOverlay);
        overlayManager.remove(coordinatesOverlay);
        overlayManager.remove(taskReminderOverlay);
        historyService.shutDown();
        mainPanel.shutDown();
        log.info("Slayer Simplified stopped");
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Parse slayer-related game messages (task assignment, completion, etc.)
        if (event.getType() == ChatMessageType.GAMEMESSAGE
                || event.getType() == ChatMessageType.SPAM)
        {
            String rawMsg = event.getMessage();
            if (rawMsg == null) return;
            String message = Text.removeTags(rawMsg);
            SlayerTaskTracker.ParseResult result = taskTracker.parseChatMessage(message);

            // Auto-navigate when a new task is assigned
            if (result == SlayerTaskTracker.ParseResult.NEW_TASK)
            {
                pendingTaskNavigation = false;
                final String newTask = taskTracker.getCurrentTaskName();
                final int count = taskTracker.getLastAssignedCount();
                final int taskNumber = readTaskStreak();
                final String master = lastInteractedMasterName;
                final boolean remindCape = config.remindSlayerCape()
                        && client.getRealSkillLevel(Skill.SLAYER) == 99;

                historyService.addEntry(new TaskHistoryEntry(
                        newTask, count, master, System.currentTimeMillis(), taskNumber));

                SwingUtilities.invokeLater(() ->
                {
                    mainPanel.refreshCurrentTask();
                    mainPanel.showTaskReminderIfNeeded(newTask, remindCape);
                    if (config.autoNavigate())
                    {
                        mainPanel.quickNavigate();
                    }
                });
            }
            // When game responds to !task with current task info, navigate now
            else if (result == SlayerTaskTracker.ParseResult.CURRENT_TASK && pendingTaskNavigation)
            {
                pendingTaskNavigation = false;
                if (config.autoNavigate())
                {
                    SwingUtilities.invokeLater(() -> mainPanel.quickNavigate());
                }
            }
            else if (result == SlayerTaskTracker.ParseResult.TASK_COMPLETE
                    || result == SlayerTaskTracker.ParseResult.NO_TASK)
            {
                SwingUtilities.invokeLater(mainPanel::refreshCurrentTask);
            }

            // Keep NPC highlighting in sync whenever the task state changes.
            if (result != SlayerTaskTracker.ParseResult.NONE)
            {
                targetOverlay.onTaskChanged();
            }
            return;
        }

        // Listen for the player typing "!task" in public chat
        if (event.getType() == ChatMessageType.PUBLICCHAT)
        {
            if (event.getName() == null || client.getLocalPlayer() == null)
            {
                return;
            }

            String sender = Text.sanitize(event.getName());
            String localName = Text.sanitize(client.getLocalPlayer().getName());

            if (localName != null && sender.equals(localName))
            {
                String rawMsg = event.getMessage();
                if (rawMsg == null) return;
                String message = Text.removeTags(rawMsg).trim();
                if (message.equalsIgnoreCase("!task"))
                {
                    if (!config.autoNavigate())
                    {
                        return;
                    }

                    // Set flag so the GAMEMESSAGE response triggers navigation
                    pendingTaskNavigation = true;

                    // Fallback: if no matching game message arrives within 10 ticks,
                    // try navigating with whatever task name is already stored
                    Runnable fallback = () ->
                    {
                        if (pendingTaskNavigation)
                        {
                            pendingTaskNavigation = false;
                            SwingUtilities.invokeLater(() -> mainPanel.quickNavigate());
                        }
                    };
                    // Chain 10 invokeLater calls for ~10 tick delay
                    Runnable chain = fallback;
                    for (int i = 0; i < 9; i++)
                    {
                        final Runnable next = chain;
                        chain = () -> clientThread.invokeLater(next);
                    }
                    clientThread.invokeLater(chain);
                }
            }
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        targetOverlay.onNpcSpawned(event.getNpc());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        targetOverlay.onNpcDespawned(event.getNpc());
    }

    @Subscribe
    public void onNpcLootReceived(NpcLootReceived event)
    {
        if (!targetOverlay.isTracked(event.getNpc()))
        {
            return;
        }
        String taskName = taskTracker.getCurrentTaskName();
        if (taskName == null || taskName.isEmpty())
        {
            return;
        }
        String key = "kc_" + taskName.toLowerCase().replace(" ", "_");
        String stored = configManager.getConfiguration(SlayerSimplifiedConfig.CONFIG_GROUP, key);
        int current = 0;
        if (stored != null)
        {
            try { current = Integer.parseInt(stored); } catch (NumberFormatException ignored) {}
        }
        configManager.setConfiguration(SlayerSimplifiedConfig.CONFIG_GROUP, key, current + 1);
    }

    /**
     * Detects when the player clicks on a known slayer master NPC so we can
     * attribute the next task assignment to the correct master.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        String option = event.getMenuOption();
        if (!"Talk-to".equalsIgnoreCase(option) && !"Assignment".equalsIgnoreCase(option))
        {
            return;
        }
        String target = Text.removeTags(event.getMenuTarget());
        for (SlayerMaster master : SlayerMaster.values())
        {
            String displayName = master.getDisplayName();
            if (master == SlayerMaster.NIEVE)
            {
                if (target.equals("Nieve") || target.equals("Steve"))
                {
                    lastInteractedMasterName = displayName;
                    return;
                }
            }
            else if (target.equalsIgnoreCase(displayName))
            {
                lastInteractedMasterName = displayName;
                return;
            }
        }
    }

    /**
     * Reads the player's slayer task streak from RuneLite's built-in Slayer
     * plugin RSProfile config. Returns 0 if unavailable.
     */
    private int readTaskStreak()
    {
        String streakStr = configManager.getRSProfileConfiguration("slayer", "streak");
        if (streakStr != null)
        {
            try
            {
                return Integer.parseInt(streakStr.trim());
            }
            catch (NumberFormatException ignored)
            {
            }
        }
        return 0;
    }
}
