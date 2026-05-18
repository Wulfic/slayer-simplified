/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.slayersimplified.domain.TaskHistoryEntry;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Persists the player's slayer task history to
 * {@code .runelite/slayer-simplified/history.json}.
 *
 * <p>The history list is guarded by synchronization so it can be safely
 * written from the client thread and read from the EDT. Disk writes are
 * submitted to a single-thread executor to keep the client thread free.</p>
 */
@Slf4j
@Singleton
public class SlayerHistoryService
{
    private static final int MAX_ENTRIES = 200;
    private static final File HISTORY_DIR = new File(RuneLite.RUNELITE_DIR, "slayer-simplified");
    private static final File HISTORY_FILE = new File(HISTORY_DIR, "history.json");
    private static final Type LIST_TYPE = new TypeToken<List<TaskHistoryEntry>>()
    {
    }.getType();

    private final Gson gson;
    private final List<TaskHistoryEntry> history = new ArrayList<>();
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public SlayerHistoryService(Gson gson)
    {
        this.gson = gson;
        load();
    }

    /**
     * Prepends a new entry to the history and triggers an async save.
     * Safe to call from any thread.
     */
    public void addEntry(TaskHistoryEntry entry)
    {
        synchronized (history)
        {
            history.add(0, entry);
            if (history.size() > MAX_ENTRIES)
            {
                history.subList(MAX_ENTRIES, history.size()).clear();
            }
        }
        saveExecutor.submit(this::save);
    }

    /**
     * Backfills the most recent entry's count and task number if they are
     * still zero (e.g. assigned before the RuneLite Slayer plugin had populated
     * its RSProfile config). Only updates when the latest entry matches the
     * provided task name. Safe to call from any thread.
     *
     * @return {@code true} if the latest entry was modified.
     */
    public boolean updateLatestEntry(String taskName, int count, int taskNumber)
    {
        if (taskName == null || taskName.isEmpty())
        {
            return false;
        }
        boolean changed = false;
        synchronized (history)
        {
            if (history.isEmpty())
            {
                return false;
            }
            TaskHistoryEntry top = history.get(0);
            if (top.taskName == null || !top.taskName.equalsIgnoreCase(taskName))
            {
                return false;
            }
            if (count > 0 && top.count != count)
            {
                top.count = count;
                changed = true;
            }
            if (taskNumber > 0 && top.taskNumber != taskNumber)
            {
                top.taskNumber = taskNumber;
                changed = true;
            }
        }
        if (changed)
        {
            saveExecutor.submit(this::save);
        }
        return changed;
    }

    /**
     * Marks the most recent entry as skipped (cancelled via slayer points)
     * if its task name matches. Safe to call from any thread.
     *
     * @return {@code true} if the latest entry was modified.
     */
    public boolean markLatestSkipped(String taskName)
    {
        if (taskName == null || taskName.isEmpty())
        {
            return false;
        }
        synchronized (history)
        {
            if (history.isEmpty())
            {
                return false;
            }
            TaskHistoryEntry top = history.get(0);
            if (top.taskName == null || !top.taskName.equalsIgnoreCase(taskName) || top.skipped)
            {
                return false;
            }
            top.skipped = true;
        }
        saveExecutor.submit(this::save);
        return true;
    }

    /**
     * Returns a snapshot of the history list (most recent first).
     * Safe to call from any thread.
     */
    public List<TaskHistoryEntry> getHistory()
    {
        synchronized (history)
        {
            return new ArrayList<>(history);
        }
    }

    /** Must be called on plugin shutdown to stop the save executor. */
    public void shutDown()
    {
        saveExecutor.shutdownNow();
    }

    private void load()
    {
        if (!HISTORY_FILE.exists())
        {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(HISTORY_FILE), StandardCharsets.UTF_8))
        {
            List<TaskHistoryEntry> loaded = gson.fromJson(reader, LIST_TYPE);
            if (loaded != null)
            {
                synchronized (history)
                {
                    history.addAll(loaded);
                }
            }
        }
        catch (IOException e)
        {
            log.debug("Could not load slayer history", e);
        }
    }

    private void save()
    {
        final List<TaskHistoryEntry> snapshot;
        synchronized (history)
        {
            snapshot = new ArrayList<>(history);
        }
        try
        {
            HISTORY_DIR.mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(HISTORY_FILE), StandardCharsets.UTF_8))
            {
                gson.toJson(snapshot, LIST_TYPE, writer);
            }
        }
        catch (IOException e)
        {
            log.debug("Could not save slayer history", e);
        }
    }
}
