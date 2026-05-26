/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

/**
 * A single row in the search-results list.
 * <p>
 * A result can be a direct task-name match ({@code displayName == parentTask.name})
 * or a variant-name match ({@code displayName} is the variant's display name,
 * {@code --lvl N} flag already stripped).  Clicking either type navigates to
 * {@code parentTask}.
 */
public class TaskSearchResult
{
    /** The task to open when this row is clicked. Never null. */
    public final Task parentTask;

    /**
     * The name shown in the search row.  For a direct task match this equals
     * {@code parentTask.name}; for a variant match it is the variant display
     * name (with any {@code --lvl N} suffix removed).
     */
    public final String displayName;

    public TaskSearchResult(Task parentTask, String displayName)
    {
        this.parentTask = parentTask;
        this.displayName = displayName;
    }
}
