/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Tracks the player's current slayer task by parsing game chat messages
 * and reading the task counter varbit. The plugin class subscribes to
 * ChatMessage events and delegates to this tracker.
 */
package com.slayersimplified.services;

import com.slayersimplified.SlayerSimplifiedConfig;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks the player's current Slayer task. Uses VarPlayer to check
 * if a task is active, and parses chat messages to identify the
 * creature name. The task name is persisted in config so it survives
 * client restarts.
 */
@Slf4j
@Singleton
public class SlayerTaskTracker
{
    /** Result of parsing a chat message. */
    public enum ParseResult
    {
        NEW_TASK,
        CURRENT_TASK,
        TASK_COMPLETE,
        NO_TASK,
        NONE
    }

    /** RuneLite built-in slayer plugin config group. */
    private static final String RL_SLAYER_GROUP = "slayer";
    /** RuneLite built-in slayer plugin config key for the task creature name. */
    private static final String RL_TASK_NAME_KEY = "taskName";
    /** RuneLite built-in slayer plugin config key for the original task amount. */
    private static final String RL_INIT_AMOUNT_KEY = "initialAmount";
    /** RuneLite built-in slayer plugin config key for the remaining task amount. */
    private static final String RL_AMOUNT_KEY = "amount";
    /** RuneLite built-in slayer plugin config key for the streak counter. */
    private static final String RL_STREAK_KEY = "streak";

    // Common chat message patterns for slayer task assignment
    private static final Pattern NEW_TASK_PATTERN =
            Pattern.compile("(?:Your new task is to kill|You are to bring balance to) (\\d+) (.+)\\.");
    private static final Pattern CURRENT_TASK_PATTERN =
            Pattern.compile("You're (?:still hunting|(?:currently )?assigned to kill) (.+?); (?:you have|only) (\\d+)(?: more)? to go\\.");
    private static final Pattern TASK_COMPLETE_PATTERN =
            Pattern.compile("You have completed your task!");
    private static final Pattern NO_TASK_PATTERN =
            Pattern.compile("You need something new to hunt\\.");

    private final Client client;
    private final SlayerSimplifiedConfig config;
    private final ConfigManager configManager;

    /** The kill count from the most recently parsed task assignment. */
    private int lastAssignedCount = 0;

    /**
     * The total kills assigned for the current task. Set on NEW_TASK or via
     * {@link #setCurrentTaskTotal}. Volatile so it can be safely read from the EDT.
     */
    private volatile int currentTaskTotal = 0;

    @Inject
    public SlayerTaskTracker(Client client, SlayerSimplifiedConfig config, ConfigManager configManager)
    {
        this.client = client;
        this.config = config;
        this.configManager = configManager;
        this.currentTaskTotal = config.currentTaskTotal();
    }

    /** Returns the kill count from the last parsed task assignment message. */
    public int getLastAssignedCount()
    {
        return lastAssignedCount;
    }

    /**
     * Returns the total kills assigned for the current task.
     * Falls back to the RuneLite built-in Slayer plugin's RSProfile config
     * if our own value is unset, so the count is available even on plugin
     * restart when the assignment chat message was not observed.
     * Safe to call from any thread.
     */
    public int getCurrentTaskTotal()
    {
        if (currentTaskTotal > 0)
        {
            return currentTaskTotal;
        }
        String val = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_INIT_AMOUNT_KEY);
        if (val == null)
        {
            return 0;
        }
        try
        {
            int parsed = Integer.parseInt(val.trim());
            if (parsed > 0)
            {
                // Cache for next call so we don't keep hitting config
                currentTaskTotal = parsed;
                config.setCurrentTaskTotal(parsed);
            }
            return parsed;
        }
        catch (NumberFormatException ignored)
        {
            return 0;
        }
    }

    /**
     * Updates the known total for the current task (e.g. from the !task chatcommand response).
     * Safe to call from any thread (volatile write). Also persists to config.
     */
    public void setCurrentTaskTotal(int total)
    {
        this.currentTaskTotal = total;
        config.setCurrentTaskTotal(total);
    }

    /**
     * Mirrors the RuneLite built-in Slayer plugin's RSProfile config into our
     * own config. The RL plugin is the authoritative source of truth for the
     * active task (it tracks varbits/server state directly), so when it
     * updates we must overwrite any stale value we cached from a previous
     * task — otherwise navigation and history will keep pointing at the old
     * monster after a skip or task change.
     *
     * <p>Safe to call from any thread.</p>
     *
     * @return {@code true} if our cached task name actually changed.
     */
    public boolean syncFromRuneLiteConfig()
    {
        String rlTaskName = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_TASK_NAME_KEY);
        if (rlTaskName == null || rlTaskName.isEmpty())
        {
            // RuneLite slayer plugin has no task recorded — don't wipe our own
            // value (it may have been set from the assignment chat message
            // before the RL plugin observed the change, or the RL plugin may
            // be disabled).
            return false;
        }

        String normalized = normalizeCreatureName(rlTaskName);
        String previous = config.currentTaskName();
        boolean changed = previous == null
                ? true
                : !previous.equalsIgnoreCase(normalized);

        // RL is authoritative whenever it has a task — overwrite our cache.
        config.setCurrentTaskName(normalized);

        // Refresh the total from RL's initialAmount whenever the name changes.
        if (changed)
        {
            int rlTotal = 0;
            String rlInit = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_INIT_AMOUNT_KEY);
            if (rlInit != null)
            {
                try
                {
                    rlTotal = Integer.parseInt(rlInit.trim());
                }
                catch (NumberFormatException ignored)
                {
                    rlTotal = 0;
                }
            }
            currentTaskTotal = rlTotal;
            config.setCurrentTaskTotal(rlTotal);
            log.debug("Synced task from RuneLite slayer config: '{}' -> '{}' (x{})",
                    previous, normalized, rlTotal);
        }
        return changed;
    }

    /**
     * Check if the player currently has an active slayer task by reading
     * the task remaining count from game state.
     *
     * @return true if the player has a task with remaining kills > 0
     */
    public boolean hasTask()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return false;
        }
        return client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0;
    }

    /** Returns the number of kills remaining on the current slayer task, or 0 if not logged in. */
    public int getRemainingCount()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return 0;
        }
        return client.getVarpValue(VarPlayerID.SLAYER_COUNT);
    }

    /**
     * Returns the number of slayer tasks the player has <em>completed</em>, as
     * stored by RuneLite's built-in Slayer plugin (mirrors
     * {@code VarbitID.SLAYER_TASKS_COMPLETED}). Returns 0 if unknown.
     *
     * <p>This is <strong>not</strong> the current assignment number. For the
     * 1-based number of the active task use {@link #getCurrentAssignmentNumber()}.</p>
     */
    public int getTaskStreak()
    {
        String val = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_STREAK_KEY);
        if (val == null)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(val.trim());
        }
        catch (NumberFormatException ignored)
        {
            return 0;
        }
    }

    /**
     * Returns the 1-based assignment number of the player's <em>current</em>
     * slayer task.
     *
     * <p>RuneLite stores {@code SLAYER_TASKS_COMPLETED} as the streak, which
     * increments only when a task is <em>finished</em>. While a task is active
     * that value is therefore one less than the actual assignment number, so we
     * add one here.</p>
     *
     * <p>Returns {@code 0} when the streak is not yet known (e.g. RuneLite
     * slayer plugin has never populated its RSProfile config).</p>
     */
    public int getCurrentAssignmentNumber()
    {
        String val = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_STREAK_KEY);
        if (val == null)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(val.trim()) + 1;
        }
        catch (NumberFormatException ignored)
        {
            return 0;
        }
    }

    /**
     * Returns the remaining kills on the current slayer task by reading the
     * RuneLite built-in Slayer plugin's RSProfile config. Returns -1 if unknown.
     * A value of 0 indicates the task is completed; a positive value indicates
     * remaining kills (and, when paired with a {@code taskName} change, that the
     * task was skipped via slayer points).
     */
    public int getRemainingAmount()
    {
        String val = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_AMOUNT_KEY);
        if (val == null)
        {
            return -1;
        }
        try
        {
            return Integer.parseInt(val.trim());
        }
        catch (NumberFormatException ignored)
        {
            return -1;
        }
    }

    /**
     * Get the current task creature name. Checks our own config first,
     * then falls back to reading RuneLite's built-in Slayer plugin
     * RSProfile config (which is populated from game varbits on login).
     */
    public String getCurrentTaskName()
    {
        // 1. Our own persisted task name (set when we parse chat messages)
        String name = config.currentTaskName();
        if (name != null && !name.isEmpty())
        {
            // Re-normalize to repair any values stored by a previous broken normalization
            // (e.g. "Elve" stored before the irregular-plural fix was applied).
            String repaired = normalizeCreatureName(name);
            if (!repaired.equals(name))
            {
                log.debug("Repaired stale task name in config: '{}' -> '{}'", name, repaired);
                config.setCurrentTaskName(repaired);
                name = repaired;
            }
            return name;
        }

        // 2. Fallback: read from RuneLite's built-in slayer plugin RSProfile config
        String rlTaskName = configManager.getRSProfileConfiguration(RL_SLAYER_GROUP, RL_TASK_NAME_KEY);
        if (rlTaskName != null && !rlTaskName.isEmpty())
        {
            String normalized = normalizeCreatureName(rlTaskName);
            // Cache in our own config so future lookups are fast
            config.setCurrentTaskName(normalized);
            log.debug("Read task name from RuneLite slayer config: '{}' -> '{}'", rlTaskName, normalized);
            return normalized;
        }

        return null;
    }

    /**
     * Called by the plugin's ChatMessage subscriber. Parses the message
     * to detect task assignments, completions, and status checks.
     *
     * @param message the stripped (tag-free) chat message text
     * @return the type of message detected, or NONE if not slayer-related
     */
    public ParseResult parseChatMessage(String message)
    {
        // New task assignment: "Your new task is to kill 130 abyssal demons."
        Matcher newTask = NEW_TASK_PATTERN.matcher(message);
        if (newTask.find())
        {
            String creatureName = normalizeCreatureName(newTask.group(2));
            config.setCurrentTaskName(creatureName);
            try
            {
                lastAssignedCount = Integer.parseInt(newTask.group(1));
            }
            catch (NumberFormatException ignored)
            {
                lastAssignedCount = 0;
            }
            currentTaskTotal = lastAssignedCount;
            config.setCurrentTaskTotal(lastAssignedCount);
            log.debug("Detected new slayer task: {} x{}", creatureName, lastAssignedCount);
            return ParseResult.NEW_TASK;
        }

        // Current task check: "You're still hunting abyssal demons; you have 45 to go."
        Matcher currentTask = CURRENT_TASK_PATTERN.matcher(message);
        if (currentTask.find())
        {
            String creatureName = normalizeCreatureName(currentTask.group(1));
            config.setCurrentTaskName(creatureName);
            log.debug("Confirmed current slayer task: {}", creatureName);
            return ParseResult.CURRENT_TASK;
        }

        // Task complete
        if (TASK_COMPLETE_PATTERN.matcher(message).find())
        {
            config.setCurrentTaskName("");
            currentTaskTotal = 0;
            config.setCurrentTaskTotal(0);
            log.debug("Slayer task completed");
            return ParseResult.TASK_COMPLETE;
        }

        // No task
        if (NO_TASK_PATTERN.matcher(message).find())
        {
            config.setCurrentTaskName("");
            currentTaskTotal = 0;
            config.setCurrentTaskTotal(0);
            log.debug("No slayer task active");
            return ParseResult.NO_TASK;
        }

        return ParseResult.NONE;
    }

    /** Irregular plural forms that cannot be resolved by simply stripping a trailing 's'. */
    private static final java.util.Map<String, String> IRREGULAR_PLURALS;
    static
    {
        IRREGULAR_PLURALS = new java.util.HashMap<>();
        IRREGULAR_PLURALS.put("elves", "Elf");
        // Alias for values that were incorrectly stored before the irregular-plural fix
        IRREGULAR_PLURALS.put("elve", "Elf");
    }

    /**
     * Normalize a creature name from a chat message to match our tasks.json keys.
     * Chat messages use lowercase plural names like "abyssal demons".
     * Our tasks.json uses title case singular like "Abyssal demon".
     */
    private String normalizeCreatureName(String raw)
    {
        String name = raw.trim();

        // Check irregular plurals first (e.g. "elves" -> "Elf")
        String irregular = IRREGULAR_PLURALS.get(name.toLowerCase());
        if (irregular != null)
        {
            return irregular;
        }

        // Remove trailing 's' for simple plurals (but not "ss" like "boss")
        if (name.endsWith("s") && !name.endsWith("ss") && !name.endsWith("us"))
        {
            name = name.substring(0, name.length() - 1);
        }

        // Title-case the first character
        if (!name.isEmpty())
        {
            name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        return name;
    }
}
