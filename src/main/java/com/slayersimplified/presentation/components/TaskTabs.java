/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * MODIFIED from original: LOCATIONS tab now uses LocationsTab (with navigate
 * buttons) instead of the plain TextTab.
 */
package com.slayersimplified.presentation.components;

import com.google.gson.Gson;
import com.slayersimplified.domain.Icon;
import com.slayersimplified.domain.Tab;
import com.slayersimplified.domain.TabKey;
import com.slayersimplified.domain.Task;
import com.slayersimplified.presentation.components.tabs.InfoTab;
import com.slayersimplified.presentation.components.tabs.LocationsTab;
import com.slayersimplified.presentation.components.tabs.LootTab;
import com.slayersimplified.presentation.components.tabs.NotesTab;
import com.slayersimplified.presentation.components.tabs.WikiTab;
import com.slayersimplified.services.FavoriteLocationService;
import com.slayersimplified.services.LocationCoordinateService;
import com.slayersimplified.services.LocationRequirementService;
import com.slayersimplified.services.MonsterNotesService;
import com.slayersimplified.services.NavigationService;
import com.slayersimplified.services.TileNoteService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.laf.RuneLiteTabbedPaneUI;
import okhttp3.OkHttpClient;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tabbed pane containing the task detail tabs.
 * The Locations tab uses a NavigationService-aware LocationsTab with
 * favorite star toggles and "Nav" buttons.
 */
@Slf4j
public class TaskTabs extends JTabbedPane
{
    private final Map<TabKey, Tab<?>> tabMap = new HashMap<>();
    private final LocationsTab locationsTab;
    private final LootTab lootTab;
    private final InfoTab infoTab;
    private final LocationRequirementService requirementService;

    /**
     * @param navigationService         service for sending path requests to Shortest Path
     * @param locationCoordinateService service for resolving location names to WorldPoints
     * @param favoriteService           service for persisting favorite locations
     * @param okHttpClient              HTTP client for wiki lookups
     * @param gson                      client Gson for parsing price API responses
     * @param notesService              service for persisting monster notes
     * @param debugMode                 supplier that returns true when location debug mode is enabled
     */
    public TaskTabs(
            NavigationService navigationService,
            LocationCoordinateService locationCoordinateService,
            FavoriteLocationService favoriteService,
            LocationRequirementService requirementService,
            TileNoteService tileNoteService,
            OkHttpClient okHttpClient,
            Gson gson,
            MonsterNotesService notesService,
            Runnable onNotesChanged,
            Supplier<Boolean> debugMode)
    {
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        setUI(new RuneLiteTabbedPaneUI()
        {
            @Override
            protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics)
            {
                int tabCount = getTabCount();
                if (tabCount == 0)
                {
                    return super.calculateTabWidth(tabPlacement, tabIndex, metrics);
                }
                int totalWidth = TaskTabs.this.getWidth();
                if (totalWidth <= 0)
                {
                    totalWidth = 225;
                }
                // Account for tab run insets so all tabs fit in one row
                Insets tabInsets = getTabAreaInsets(tabPlacement);
                int usable = totalWidth - tabInsets.left - tabInsets.right;
                return usable / tabCount;
            }
        });

        TabKey locations = TabKey.LOCATIONS;
        TabKey info = TabKey.INFO;
        TabKey wiki = TabKey.WIKI;
        TabKey loot = TabKey.LOOT;
        TabKey notes = TabKey.NOTES;

        locationsTab = new LocationsTab(navigationService, locationCoordinateService, favoriteService, requirementService, tileNoteService, debugMode);
        lootTab = new LootTab(okHttpClient, gson);
        infoTab = new InfoTab(okHttpClient);
        this.requirementService = requirementService;

        setTab(locations, Icon.COMPASS.getIcon(), locationsTab, locations.getName());
        setTab(info, Icon.SLAYER_SKILL.getIcon(), infoTab, info.getName());
        setTab(loot, Icon.INVENTORY.getIcon(), lootTab, loot.getName());
        setTab(notes, Icon.NOTES.getIcon(), new NotesTab(notesService, onNotesChanged), notes.getName());
        setTab(wiki, Icon.WIKI.getIcon(), new WikiTab(), wiki.getName());
    }

    public void shutDown()
    {
        tabMap.values().forEach(Tab::shutDown);
    }

    /**
     * Programmatically switch to the Locations tab.
     */
    public void selectLocationsTab()
    {
        int index = indexOfComponent((Component) tabMap.get(TabKey.LOCATIONS));
        if (index >= 0)
        {
            setSelectedIndex(index);
        }
    }

    public void update(Task task)
    {
        // Build the variant accordion data for the Locations tab.
        // First entry is always the base monster (task.name); subsequent entries are its named variants.
        List<LocationsTab.LocationsData.VariantEntry> variantEntries = new ArrayList<>();

        if (task.variants != null && task.variants.length > 0)
        {
            // Variants own the full list — each may carry a "--lvl N" flag in its name.
            for (String variantName : task.variants)
            {
                String[] variantLocs = (task.variantLocations != null)
                        ? task.variantLocations.get(variantName)
                        : null;
                variantEntries.add(new LocationsTab.LocationsData.VariantEntry(
                        variantName, variantLocs != null ? variantLocs : new String[0]));
            }
        }
        else
        {
            // No variants: single entry using the task name.
            String[] baseLocs = (task.variantLocations != null) ? task.variantLocations.get(task.name) : null;
            variantEntries.add(new LocationsTab.LocationsData.VariantEntry(
                    task.name, baseLocs != null ? baseLocs : new String[0]));
        }
        updateTab(TabKey.LOCATIONS, new LocationsTab.LocationsData(task.name, variantEntries));

        updateTab(TabKey.INFO, new InfoTab.InfoData(
                task.name,
                task.itemsRequired,
                task.itemsSuggested,
                new Object[][]{task.attackStyles, task.attributes},
                task.masters));
        updateTab(TabKey.WIKI, task.wikiLinks);
        updateTab(TabKey.LOOT, task.name);

        updateTab(TabKey.NOTES, new NotesTab.NotesData(task.name));
    }

    private <T> void updateTab(TabKey key, T data)
    {
        Tab<?> rawTab = tabMap.get(key);

        if (rawTab == null)
        {
            log.error("No tab found with key {}", key.toString());
            return;
        }

        @SuppressWarnings("unchecked")
        Tab<T> tab = (Tab<T>) rawTab;
        tab.update(data);
    }

    private void setTab(TabKey key, ImageIcon icon, Tab<?> tab, String tip)
    {
        tabMap.put(key, tab);
        addTab(null, icon, (Component) tab, tip);
    }
}
