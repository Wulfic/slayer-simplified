/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.slayersimplified.domain.Task;
import com.slayersimplified.domain.TaskSearchResult;

import java.util.Comparator;

/**
 * Service interface for looking up and searching Slayer tasks.
 */
public interface TaskService
{
    Task get(String name);

    Task[] getAll();

    Task[] getAll(Comparator<Task> comparator);

    Task[] searchPartialName(String text);

    /**
     * Searches task names and all variant names for {@code text}.
     * Returns one {@link TaskSearchResult} per matching name; variant hits
     * carry the variant display name but point to the parent task.
     */
    TaskSearchResult[] searchWithVariants(String text);
}
