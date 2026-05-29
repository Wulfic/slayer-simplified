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
import com.slayersimplified.presentation.TileNoteOverlay;
import com.slayersimplified.presentation.panels.MainPanel;
import com.slayersimplified.presentation.SlayerTargetOverlay;
import com.slayersimplified.services.SlayerHistoryService;
import com.slayersimplified.services.SlayerStreakOptimizerService;
import com.slayersimplified.services.SlayerTaskTracker;
import com.slayersimplified.services.LocationRequirementService;
import com.slayersimplified.services.TileNoteService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.TaskService;
import net.runelite.api.NPC;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.events.ConfigChanged;
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
import java.util.HashSet;
import java.util.Set;

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
    private TileNoteOverlay tileNoteOverlay;

    @Inject
    private TileNoteService tileNoteService;

    @Inject
    private SlayerHistoryService historyService;

    @Inject
    private ConfigManager configManager;

    @Inject
    private TaskService taskService;

    @Inject
    private LocationRequirementService locationRequirementService;

    @Inject
    private SlayerStreakOptimizerService streakOptimizerService;

    private NavigationButton navButton;

    /** Set when the player types !task; cleared when the game response triggers navigation. */
    private volatile boolean pendingTaskNavigation = false;

    /**
     * Set to true on LOGGED_IN so the first GameTick re-reads quest/skill state.
     * Using GameTick (rather than clientThread.invokeLater) ensures all quest
     * varbits are fully synced before we query them.
     */
    private volatile boolean pendingRequirementsRefresh = false;

    /** Name of the most recently interacted slayer master NPC, for history attribution. */
    private String lastInteractedMasterName = "";

    /**
     * Last task name we saw in the RuneLite Slayer plugin's RSProfile config.
     * Used to detect transitions to a new task.
     */
    private volatile String lastSeenSlayerTaskName;

    /**
     * Last remaining-kills value we saw on the previous task. If this is &gt; 0
     * when the task name changes, the previous task was cancelled via slayer
     * points (i.e. skipped).
     */
    private volatile int lastSeenSlayerRemaining = -1;

    /**
     * Set to {@code true} when we observe the "You have completed your task!"
     * chat message. Consulted (and cleared) when the task name next changes
     * so we never flag a legitimately completed task as skipped, even if the
     * RuneLite Slayer plugin saves its {@code amount} config out of order.
     */
    private volatile boolean previousTaskCompleted = false;

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
        overlayManager.add(tileNoteOverlay);
        // After every requirement refresh, rebuild the current task panel so the
        // Locations tab immediately reflects the player's latest quest completion.
        // The optimizer is refreshed here too since both run on the client thread
        // and need the same player state (quest completion, combat level).
        locationRequirementService.addRefreshListener(() ->
        {
            streakOptimizerService.refresh();
            SwingUtilities.invokeLater(mainPanel::refreshCurrentTask);
            SwingUtilities.invokeLater(mainPanel::refreshSelectedTask);
        });
        SwingUtilities.invokeLater(() ->
        {
            // Mirror the RuneLite Slayer plugin's authoritative task state into
            // our own config before reading anything, so we recover from any
            // stale name left over from a previous session.
            taskTracker.syncFromRuneLiteConfig();
            syncCurrentTaskToHistory();
            mainPanel.refreshCurrentTask();
            mainPanel.refreshHistory();
        });
        // Seed last-seen slayer state so we can detect future task changes
        // (completion vs skip) by comparing against this baseline.
        lastSeenSlayerTaskName = taskTracker.getCurrentTaskName();
        lastSeenSlayerRemaining = taskTracker.getRemainingAmount();
        // Populate the NPC highlight set in case the plugin is enabled while already logged in.
        clientThread.invokeLater(targetOverlay::onTaskChanged);
        // Seed tile notes for any task already active when the plugin starts.
        tileNoteService.updateForTask(taskTracker.getCurrentTaskName());
        // Cache quest/skill state so the LocationsTab can gray out inaccessible options.
        pendingRequirementsRefresh = true;
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
        overlayManager.remove(tileNoteOverlay);
        tileNoteService.clear();
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
                final int taskNumber = taskTracker.getCurrentAssignmentNumber();
                final String master = lastInteractedMasterName;
                final boolean remindCape = config.remindSlayerCape()
                        && client.getRealSkillLevel(Skill.SLAYER) == 99;

                historyService.addEntry(new TaskHistoryEntry(
                        newTask, count, master, System.currentTimeMillis(), taskNumber));

                SwingUtilities.invokeLater(() ->
                {
                    mainPanel.refreshCurrentTask();
                    mainPanel.refreshHistory();
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
                if (result == SlayerTaskTracker.ParseResult.TASK_COMPLETE)
                {
                    // Remember completion so the upcoming taskName ConfigChanged
                    // is not misinterpreted as a slayer-point skip.
                    previousTaskCompleted = true;
                }
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
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == net.runelite.api.GameState.LOGGED_IN)
        {
            // Flag so the next GameTick re-reads quest/skill state once all
            // varbits for the session are fully synchronised.
            pendingRequirementsRefresh = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (pendingRequirementsRefresh)
        {
            pendingRequirementsRefresh = false;
            locationRequirementService.refresh();
        }
    }

    /**
     * When the quest-complete scroll interface loads, the player has just
     * finished a quest in-session.  Re-read all quest states immediately so
     * the Locations tab unlocks any newly-available areas without requiring
     * the player to log out and back in.
     */
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == InterfaceID.QUESTSCROLL)
        {
            clientThread.invokeLater(locationRequirementService::refresh);
        }
    }

    /** NPC indices the local player has attacked since the plugin started. Cleared on NPC despawn or kill. */
    private final Set<Integer> playerTargetIndices = new HashSet<>();

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        if (event.getSource() != client.getLocalPlayer())
        {
            return;
        }
        if (event.getTarget() instanceof NPC)
        {
            playerTargetIndices.add(((NPC) event.getTarget()).getIndex());
        }
    }

    /**
     * Secondary kill-attribution source: any hitsplat the local player deals to an NPC
     * marks that NPC as a target. This covers auto-retaliate and spells/ranged where
     * InteractingChanged may not have fired before the kill.
     */
    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event)
    {
        if (!(event.getActor() instanceof NPC))
        {
            return;
        }
        if (event.getHitsplat().isMine())
        {
            playerTargetIndices.add(((NPC) event.getActor()).getIndex());
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        if (!(event.getActor() instanceof NPC))
        {
            return;
        }
        NPC npc = (NPC) event.getActor();
        if (!playerTargetIndices.remove(npc.getIndex()))
        {
            return;
        }
        String npcName = npc.getName();
        if (npcName == null)
        {
            return;
        }
        Task task = taskService.get(npcName);
        // Fallback: the NPC name may be a variant of a known task
        // (e.g. "Iorwerth Warrior" is a variant of "Elf", "Deviant spectre" of "Aberrant spectre").
        // Search all tasks so off-assignment kills are counted correctly.
        if (task == null)
        {
            outer:
            for (Task t : taskService.getAll())
            {
                if (t.variants == null) continue;
                for (String variant : t.variants)
                {
                    // Strip --lvl flag (e.g. "Sea Snake Hatchling --lvl 62" -> "Sea Snake Hatchling")
                    int flagIdx = variant.indexOf("--lvl ");
                    String variantName = flagIdx >= 0 ? variant.substring(0, flagIdx).trim() : variant;
                    if (variantName.equalsIgnoreCase(npcName))
                    {
                        task = t;
                        break outer;
                    }
                }
            }
        }
        if (task == null)
        {
            return;
        }
        String key = "kc_" + task.name.toLowerCase().replace(" ", "_");
        String stored = configManager.getConfiguration(SlayerSimplifiedConfig.CONFIG_GROUP, key);
        int current = 0;
        if (stored != null)
        {
            try { current = Integer.parseInt(stored); } catch (NumberFormatException ignored) {}
        }
        configManager.setConfiguration(SlayerSimplifiedConfig.CONFIG_GROUP, key, current + 1);
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event)
    {
        targetOverlay.onNpcChanged(event.getNpc());
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event)
    {
        targetOverlay.onNpcDespawned(event.getNpc());
        playerTargetIndices.remove(event.getNpc().getIndex());
    }

    /**
     * Ensures the player's currently active slayer task is recorded as the
     * most recent entry in the history log. Called when a task is discovered
     * via the RuneLite built-in slayer plugin's RSProfile config (i.e. a task
     * that was already assigned before our plugin observed any chat messages).
     * No-op if the task is already at the top of the history.
     *
     * <p>Safe to call from the EDT.</p>
     */
    private void syncCurrentTaskToHistory()
    {
        String taskName = taskTracker.getCurrentTaskName();
        if (taskName == null || taskName.isEmpty())
        {
            return;
        }

        int total = taskTracker.getCurrentTaskTotal();
        int assignmentNumber = taskTracker.getCurrentAssignmentNumber();

        java.util.List<TaskHistoryEntry> entries = historyService.getHistory();
        if (!entries.isEmpty() && taskName.equalsIgnoreCase(entries.get(0).taskName))
        {
            // Same task already at the top — backfill any missing count/streak now
            // that the RuneLite Slayer plugin has populated its RSProfile config.
            if (historyService.updateLatestEntry(taskName, total, assignmentNumber))
            {
                log.debug("Backfilled active task entry: {} x{} (task #{})", taskName, total, assignmentNumber);
            }
            return;
        }

        historyService.addEntry(new TaskHistoryEntry(
                taskName, total, lastInteractedMasterName,
                System.currentTimeMillis(), assignmentNumber));
        log.debug("Synced active task to history: {} x{} (task #{})", taskName, total, assignmentNumber);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        // React to the RuneLite built-in slayer plugin updating its RSProfile config.
        // This is what tells us about a task that was assigned before our plugin
        // observed any chat messages (e.g. plugin started while a task was active).
        if ("slayer".equals(event.getGroup()))
        {
            String key = event.getKey();
            if ("amount".equals(key))
            {
                // Keep a running snapshot of the remaining kills so we can tell
                // whether the next taskName change was a completion or a skip.
                lastSeenSlayerRemaining = taskTracker.getRemainingAmount();
                return;
            }
            if ("taskName".equals(key) || "initialAmount".equals(key))
            {
                if ("taskName".equals(key))
                {
                    // Authoritative source: pull the new name (and refresh our
                    // own cached config) BEFORE inspecting it, otherwise we'd
                    // keep reading the stale previous task name.
                    taskTracker.syncFromRuneLiteConfig();

                    String newName = taskTracker.getCurrentTaskName();
                    String prevName = lastSeenSlayerTaskName;
                    int prevRemaining = lastSeenSlayerRemaining;
                    boolean wasCompleted = previousTaskCompleted;
                    // Consume the completion flag regardless of what happens below.
                    previousTaskCompleted = false;
                    // Task name changed to a different non-empty value AND we did
                    // NOT observe a "You have completed your task!" chat AND the
                    // previous task still had kills remaining -> player paid to skip.
                    if (!wasCompleted
                            && prevName != null && !prevName.isEmpty()
                            && newName != null && !newName.isEmpty()
                            && !prevName.equalsIgnoreCase(newName)
                            && prevRemaining > 0)
                    {
                        if (historyService.markLatestSkipped(prevName))
                        {
                            log.debug("Marked previous task as skipped: {} ({} kills left)",
                                    prevName, prevRemaining);
                        }
                    }
                    lastSeenSlayerTaskName = newName;
                    // The amount key may or may not have fired yet for the new
                    // task; refresh from config so future skip-checks are accurate.
                    lastSeenSlayerRemaining = taskTracker.getRemainingAmount();
                }
                // Rebuild the NPC highlight set now that the task name has changed.
                // onConfigChanged fires on the client thread, so this is safe to call directly.
                targetOverlay.onTaskChanged();
                // Refresh tile note markers for the new task.
                tileNoteService.updateForTask(taskTracker.getCurrentTaskName());
                SwingUtilities.invokeLater(() ->
                {
                    syncCurrentTaskToHistory();
                    mainPanel.refreshCurrentTask();
                    mainPanel.refreshHistory();
                });
            }
            return;
        }

        if (!SlayerSimplifiedConfig.CONFIG_GROUP.equals(event.getGroup()))
        {
            return;
        }
        if ("debugCoordinates".equals(event.getKey()))
        {
            mainPanel.refreshTaskList();
            mainPanel.refreshSelectedTask();
            return;
        }
        if ("showNonSlayerEnemies".equals(event.getKey()))
        {
            mainPanel.refreshTaskList();
            // Refresh tile notes so non-slayer markers are shown/hidden immediately.
            tileNoteService.updateForTask(taskTracker.getCurrentTaskName());
            return;
        }
        if ("streakOptimizerEnabled".equals(event.getKey())
                || "streakFillerMaster".equals(event.getKey()))
        {
            // Immediately refresh the "no task" label so it switches between
            // the preferred master name and the optimizer recommendation.
            SwingUtilities.invokeLater(mainPanel::refreshCurrentTask);
            return;
        }
        if (event.getKey().startsWith("kc_"))
        {
            SwingUtilities.invokeLater(mainPanel::refreshKc);
        }
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

}
