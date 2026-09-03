/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Orders two rows sharing a display name; the smaller one is kept. */
    private static final Comparator<TaskSearchResult> PREFERENCE = Comparator
            .<TaskSearchResult>comparingInt(r -> r.isDedicatedEntry() ? 0 : 1)
            .thenComparingInt(r -> r.parentTask.variants == null ? 0 : r.parentTask.variants.length)
            .thenComparing(r -> r.parentTask.name);

    /**
     * Collapses rows that show the same name.
     * <p>
     * The same monster legitimately lives in several task entries — "Dagannoth
     * Prime" is a variant of the Dagannoth family, a member of the Dagannoth
     * Kings group, and a boss entry of its own — but three identically-labelled
     * rows are indistinguishable in the list and differ only in which page they
     * open. One row survives per name, chosen by:
     * <ol>
     *   <li>the entry whose own name <em>is</em> the row name (its dedicated page),</li>
     *   <li>then the entry with the fewest variants (the most specific grouping),</li>
     *   <li>then the alphabetically first parent name — tasks are held in a
     *       {@link java.util.HashMap}, so without a total order the winner
     *       would vary between runs.</li>
     * </ol>
     * Callers must apply any visibility filtering (e.g. hiding bosses) to the
     * results <em>before</em> deduping. Deduping first can elect a winner that
     * the filter then drops, hiding a monster a surviving duplicate would
     * still have shown.
     *
     * @param results search rows, already filtered; may be empty, never null
     * @return one row per display name, sorted by parent task then display name
     */
    public static TaskSearchResult[] dedupeByDisplayName(TaskSearchResult[] results)
    {
        if (results.length < 2)
        {
            return results;
        }

        Map<String, TaskSearchResult> best = new LinkedHashMap<>();
        for (TaskSearchResult candidate : results)
        {
            String key = candidate.displayName.toLowerCase();
            TaskSearchResult incumbent = best.get(key);
            if (incumbent == null || PREFERENCE.compare(candidate, incumbent) < 0)
            {
                best.put(key, candidate);
            }
        }

        return best.values()
                .stream()
                .sorted(Comparator
                        .comparing((TaskSearchResult r) -> r.parentTask.name)
                        .thenComparing(r -> r.displayName))
                .toArray(TaskSearchResult[]::new);
    }

    /** True when this row's parent task exists specifically for the monster shown. */
    private boolean isDedicatedEntry()
    {
        return parentTask.name.equalsIgnoreCase(displayName);
    }
}
