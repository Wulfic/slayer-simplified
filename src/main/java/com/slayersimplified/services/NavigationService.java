/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Integration with the Shortest Path plugin via its official PluginMessage API.
 * See: https://github.com/Skretzo/shortest-path/wiki/Cross-plugin-communication
 */
package com.slayersimplified.services;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.WidgetNode;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.api.worldmap.WorldMap;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends path requests to the Shortest Path plugin using RuneLite's
 * cross-plugin PluginMessage API. No direct dependency on the Shortest Path
 * codebase is required — communication is entirely via the EventBus.
 *
 * If Shortest Path is not installed, the messages are simply ignored.
 */
@Slf4j
@Singleton
public class NavigationService
{
    /** Shortest Path plugin's namespace for PluginMessage events. */
    private static final String SHORTEST_PATH_NAMESPACE = "shortestpath";

    /** PluginMessage name to request displaying a path. */
    private static final String ACTION_PATH = "path";

    /** PluginMessage name to clear the currently displayed path. */
    private static final String ACTION_CLEAR = "clear";

    /** Map key for the target WorldPoint in a path request. */
    private static final String KEY_TARGET = "target";

    private final EventBus eventBus;
    private final ClientThread clientThread;
    private final Client client;

    /** The last WorldPoint sent to Shortest Path, or null if no navigation has been requested. */
    private volatile WorldPoint lastTarget;

    @Inject
    public NavigationService(EventBus eventBus, ClientThread clientThread, Client client)
    {
        this.eventBus = eventBus;
        this.clientThread = clientThread;
        this.client = client;
    }

    /** Returns the last navigation target sent to Shortest Path, or null if none. */
    public WorldPoint getLastTarget()
    {
        return lastTarget;
    }

    /**
     * Request Shortest Path to calculate and display a path from the player's
     * current location to the given target WorldPoint.
     *
     * @param target the destination coordinate to navigate to
     */
    public void navigateTo(WorldPoint target)
    {
        if (target == null)
        {
            log.warn("Cannot navigate to null target");
            return;
        }

        this.lastTarget = target;

        Map<String, Object> data = new HashMap<>();
        // Omitting "start" — Shortest Path defaults to the player's current location
        data.put(KEY_TARGET, target);

        clientThread.invokeLater(() ->
        {
            eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, ACTION_PATH, data));
            log.debug("Sent navigation request to Shortest Path: target={}", target);
        });
    }

    // World map opening — interface and script IDs are stable OSRS client values.
    // WORLD_MAP_INTERFACE_ID  = InterfaceID.Worldmap.UNIVERSE >> 16 = 595
    // WORLD_MAP_PAN_SCRIPT_ID = script 1756 (args: int 1, packed-coord, int 1)
    private static final int WORLD_MAP_INTERFACE_ID  = 595;
    private static final int WORLD_MAP_PAN_SCRIPT_ID = 1756;
    private static final int FIXED_MAP_PARENT    = (548 << 16) | 42;
    private static final int RESIZABLE_MAP_PARENT_CLASSIC = (161 << 16) | 18;
    private static final int RESIZABLE_MAP_PARENT_MODERN  = (164 << 16) | 18;

    /** Packed widget IDs that should close our world map when clicked. */
    // InterfaceID.Worldmap.CLOSE = 38993958
    // InterfaceID.Orbs.WORLDMAP  = somewhere in interface 160; the minimap orb toggles the map
    private static final int WIDGET_CLOSE_BUTTON = InterfaceID.Worldmap.CLOSE;
    private static final int WIDGET_ORB_WORLDMAP = InterfaceID.Orbs.WORLDMAP;

    /** The WidgetNode returned when we opened the world map, null if we did not open it or it is already closed. */
    private WidgetNode worldMapNode;

    /**
     * Opens the in-game world map (if not already open) and pans it to the given location.
     * Safe to call from any thread — all client interaction is dispatched to the client thread.
     *
     * @param location the WorldPoint to display on the world map
     */
    public void openOnWorldMap(WorldPoint location)
    {
        if (location == null)
        {
            return;
        }
        clientThread.invokeLater(() ->
        {
            // Set the position target before opening so the map starts centred there.
            WorldMap worldMap = client.getWorldMap();
            if (worldMap != null)
            {
                worldMap.setWorldMapPositionTarget(location);
            }

            // Open the world map if it is not already visible.
            Widget frame = client.getWidget(InterfaceID.Worldmap.FRAME);
            if (frame == null || frame.isHidden())
            {
                int parentId = getWorldMapParentId();
                worldMapNode = client.openInterface(parentId, WORLD_MAP_INTERFACE_ID, WidgetModalMode.NON_MODAL);
            }

            // Pan the map to the packed world-point coordinate.
            int packed = (location.getPlane() << 28) | (location.getX() << 14) | location.getY();
            client.runScript(WORLD_MAP_PAN_SCRIPT_ID, 1, packed, 1);

            log.debug("Opened world map and panned to {}", location);
        });
    }

    /** Returns the component ID of the viewport slot that hosts the world map for the current client layout. */
    private int getWorldMapParentId()
    {
        if (!client.isResized())
        {
            return FIXED_MAP_PARENT;
        }
        else if (client.getTopLevelInterfaceId() == 161)
        {
            return RESIZABLE_MAP_PARENT_CLASSIC;
        }
        else
        {
            return RESIZABLE_MAP_PARENT_MODERN;
        }
    }

    /**
     * Returns true if the given packed widget ID is one of the widgets that should close
     * our plugin-opened world map (the X button or the minimap world map orb).
     */
    public boolean isOurWorldMapCloseWidget(int widgetId)
    {
        return worldMapNode != null
                && (widgetId == WIDGET_CLOSE_BUTTON || widgetId == WIDGET_ORB_WORLDMAP);
    }

    /**
     * Closes the world map that was opened by this service, if any.
     * Must be called on the client thread.
     */
    public void closeWorldMap()
    {
        if (worldMapNode == null)
        {
            return;
        }
        try
        {
            client.closeInterface(worldMapNode, true);
        }
        catch (IllegalArgumentException ignored)
        {
        }
        worldMapNode = null;
        log.debug("Closed plugin-opened world map");
    }

    /**
     * Closes the world map if it was opened by this service.
     * Safe to call from any thread.
     */
    public void closeWorldMapAsync()
    {
        if (worldMapNode != null)
        {
            clientThread.invokeLater(this::closeWorldMap);
        }
    }

    /**
     * Request Shortest Path to clear the currently displayed path and marker.
     */
    public void clearNavigation()
    {
        clientThread.invokeLater(() ->
        {
            eventBus.post(new PluginMessage(SHORTEST_PATH_NAMESPACE, ACTION_CLEAR));
            log.debug("Sent clear navigation request to Shortest Path");
        });
    }
}
