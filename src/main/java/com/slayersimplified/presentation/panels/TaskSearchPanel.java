/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.domain.Task;
import com.slayersimplified.domain.TaskSearchResult;
import com.slayersimplified.presentation.SlayerTaskRenderer;
import com.slayersimplified.presentation.TaskSearchResultRenderer;
import com.slayersimplified.presentation.components.GroupedTaskList;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import com.slayersimplified.presentation.components.SearchBar;
import com.slayersimplified.presentation.components.SelectList;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
    private final SelectList<TaskSearchResult> selectList;
    private final TaskSearchResultRenderer searchResultRenderer = new TaskSearchResultRenderer();
    private final GroupedTaskList groupedTaskList;

    /** Container that switches between the grouped and flat views. */
    private final JPanel listContainer = new JPanel(new CardLayout());

    private final Consumer<String> onNameSearch;
    private final Consumer<String> onLocationSearch;
    private JPanel modeToggle;
    private boolean locationMode = false;

    public TaskSearchPanel(Consumer<String> onNameSearch, Consumer<String> onLocationSearch, Consumer<Task> onSelect)
    {
        this.onNameSearch = onNameSearch;
        this.onLocationSearch = onLocationSearch;
        searchBar = new SearchBar(this::dispatchSearch);
        selectList = new SelectList<>(searchResultRenderer,
                result -> onSelect.accept(result.parentTask),
                this::onTaskHover);
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

        // ── Mode toggle (vertical GPS / NPC flip switch) ─────────────────────
        modeToggle = buildModeToggle();

        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        searchRow.add(searchBar, BorderLayout.CENTER);
        searchRow.add(modeToggle, BorderLayout.EAST);

        add(searchRow, BorderLayout.NORTH);
        add(listContainer, BorderLayout.CENTER);
    }

    private JPanel buildModeToggle()
    {
        JPanel toggle = new JPanel()
        {
            private boolean hovered = false;
            private boolean pressed = false;

            {
                setOpaque(false);
                setPreferredSize(new Dimension(40, 30));
                setMaximumSize(new Dimension(40, 30));
                setMinimumSize(new Dimension(40, 30));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("GPS: search by location  \u2022  NPC: search by name");
                addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mousePressed(MouseEvent e)
                    {
                        pressed = true;
                        repaint();
                        onLocationToggleClicked();
                    }

                    @Override
                    public void mouseReleased(MouseEvent e)
                    {
                        pressed = false;
                        repaint();
                    }

                    @Override
                    public void mouseEntered(MouseEvent e)
                    {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e)
                    {
                        hovered = false;
                        pressed = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                final int w        = getWidth();
                final int h        = getHeight();
                final int mid      = h / 2;
                final int outerArc = 9;
                final int innerArc = 6;
                final int pad      = 3;

                // ── Track (recessed background) ────────────────────────────
                g2.setColor(new Color(16, 16, 16));
                g2.fillRoundRect(1, 1, w - 2, h - 2, outerArc, outerArc);

                // ── Knob: GPS = top half, NPC = bottom half ────────────────
                int knobY = locationMode ? pad : mid + 1;
                int knobH = mid - pad - 2;
                if (pressed) knobY += 1;

                g2.setColor(hovered ? new Color(62, 62, 62) : new Color(52, 52, 52));
                g2.fillRoundRect(pad, knobY, w - pad * 2, knobH, innerArc, innerArc);

                if (!pressed)
                {
                    g2.setColor(new Color(105, 105, 105));
                    g2.drawLine(pad + 2, knobY + 1, w - pad - 3, knobY + 1);
                }

                g2.setColor(hovered ? new Color(95, 95, 95) : new Color(80, 80, 80));
                g2.drawRoundRect(pad, knobY, w - pad * 2, knobH, innerArc, innerArc);

                // ── Outer track border ─────────────────────────────────────
                g2.setColor(hovered ? new Color(82, 82, 82) : new Color(60, 60, 60));
                g2.drawRoundRect(1, 1, w - 2, h - 2, outerArc, outerArc);

                // ── Active label centered in the DARK half (opposite the knob) ─────
                Font font = FontManager.getRunescapeSmallFont();
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                String label = locationMode ? "GPS" : "NPC";
                // GPS active → knob at top, dark section is the bottom area
                // NPC active → knob at bottom, dark section is the top area
                int secStart = locationMode ? (pad + knobH + 1) : pad;
                int secEnd   = locationMode ? (h - pad)         : mid;
                int secH     = secEnd - secStart;
                int lx = (w - fm.stringWidth(label)) / 2;
                int ly = secStart + Math.max(0, (secH - fm.getHeight()) / 2) + fm.getAscent() + (locationMode ? 2 : 0);
                g2.setColor(new Color(255, 152, 0));
                g2.drawString(label, lx, ly);

                g2.dispose();
            }
        };
        return toggle;
    }

    private void dispatchSearch(String text)
    {
        if (locationMode)
        {
            onLocationSearch.accept(text);
        }
        else
        {
            onNameSearch.accept(text);
        }
    }

    private void onLocationToggleClicked()
    {
        locationMode = !locationMode;
        modeToggle.repaint();
        String text = searchBar.getText();
        if (text == null || text.isBlank())
        {
            showGroupedView();
        }
        else
        {
            dispatchSearch(text);
        }
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
    public void showSearchResults(TaskSearchResult[] results)
    {
        selectList.update(results);
        ((CardLayout) listContainer.getLayout()).show(listContainer, SEARCH_VIEW);
    }

    /** Switches back to the grouped-by-master view. */
    public void showGroupedView()
    {
        ((CardLayout) listContainer.getLayout()).show(listContainer, GROUPED_VIEW);
    }

    private void onTaskHover(int index)
    {
        searchResultRenderer.setHoverIndex(index);
        setCursor(new Cursor(index != -1 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
