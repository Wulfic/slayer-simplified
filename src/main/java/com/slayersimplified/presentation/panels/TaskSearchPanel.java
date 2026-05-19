/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.domain.Task;
import com.slayersimplified.presentation.SlayerTaskRenderer;
import com.slayersimplified.presentation.components.GroupedTaskList;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import com.slayersimplified.presentation.components.SearchBar;
import com.slayersimplified.presentation.components.SelectList;

import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Panel containing the search bar and task list.
 * <p>
 * When the search bar is empty the list displays all tasks grouped by
 * Slayer Master with collapsible sections.  When the user types a search
 * term the view switches to a flat, filtered list.
 */
public class TaskSearchPanel extends JPanel
{
    private static final String GROUPED_VIEW = "grouped";
    private static final String SEARCH_VIEW  = "search";

    private final SearchBar searchBar;
    private final SelectList<Task> selectList;
    private final SlayerTaskRenderer taskRenderer = new SlayerTaskRenderer();
    private final GroupedTaskList groupedTaskList;

    /** Container that switches between the grouped and flat views. */
    private final JPanel listContainer = new JPanel(new CardLayout());

    public TaskSearchPanel(Consumer<String> onSearch, Consumer<Task> onSelect)
    {
        searchBar = new SearchBar(onSearch);
        selectList = new SelectList<>(taskRenderer, onSelect, this::onTaskHover);
        groupedTaskList = new GroupedTaskList(onSelect);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // ── Flat search-results scroll pane ─────────────────────────────────
        JScrollPane searchScroll = new JScrollPane(selectList);
        searchScroll.setBorder(null);
        searchScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchScroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        searchScroll.getVerticalScrollBar().setUnitIncrement(16);
        ScrollBarStyling.apply(searchScroll);

        // ── Grouped view scroll pane ─────────────────────────────────────────
        JScrollPane groupedScroll = new JScrollPane(groupedTaskList);
        groupedScroll.setBorder(null);
        groupedScroll.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        groupedScroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        groupedScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        groupedScroll.getVerticalScrollBar().setUnitIncrement(16);
        ScrollBarStyling.apply(groupedScroll);

        listContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        listContainer.add(groupedScroll, GROUPED_VIEW);
        listContainer.add(searchScroll, SEARCH_VIEW);

        add(searchBar, BorderLayout.NORTH);
        add(listContainer, BorderLayout.CENTER);
    }

    public void shutDown()
    {
        searchBar.shutDown();
        selectList.shutDown();
    }

    /**
     * Populates the grouped view with all tasks and switches to it.
     * Call this once at startup and whenever the full task list changes.
     */
    public void setAllTasks(Task[] tasks)
    {
        groupedTaskList.setTasks(tasks);
        showGroupedView();
    }

    /**
     * Updates the flat list with filtered results and switches to it.
     * Call this when the search bar has active text.
     */
    public void showSearchResults(Task[] tasks)
    {
        selectList.update(tasks);
        ((CardLayout) listContainer.getLayout()).show(listContainer, SEARCH_VIEW);
    }

    /** Switches back to the grouped-by-master view. */
    public void showGroupedView()
    {
        ((CardLayout) listContainer.getLayout()).show(listContainer, GROUPED_VIEW);
    }

    private void onTaskHover(int index)
    {
        taskRenderer.setHoverIndex(index);
        setCursor(new Cursor(index != -1 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
