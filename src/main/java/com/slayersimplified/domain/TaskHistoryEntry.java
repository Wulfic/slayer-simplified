/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

/**
 * A single recorded slayer task assignment, persisted in the history log.
 * Serialized to JSON via Gson.
 */
public class TaskHistoryEntry
{
    /** Monster name, e.g. "Abyssal demon". */
    public String taskName;

    /** Number of kills assigned. */
    public int count;

    /** Slayer master display name, or empty string if unknown. */
    public String master;

    /** Epoch milliseconds when the task was assigned. */
    public long timestamp;

    /** Player's running task counter at assignment time. 0 if unknown. */
    public int taskNumber;

    /** Required by Gson for deserialization. */
    public TaskHistoryEntry()
    {
    }

    public TaskHistoryEntry(String taskName, int count, String master, long timestamp, int taskNumber)
    {
        this.taskName = taskName;
        this.count = count;
        this.master = master;
        this.timestamp = timestamp;
        this.taskNumber = taskNumber;
    }
}
