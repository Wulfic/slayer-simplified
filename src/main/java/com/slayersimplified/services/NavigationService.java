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
import net.runelite.api.coords.WorldPoint;
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

    /** The last WorldPoint sent to Shortest Path, or null if no navigation has been requested. */
    private volatile WorldPoint lastTarget;

    @Inject
    public NavigationService(EventBus eventBus, ClientThread clientThread)
    {
        this.eventBus = eventBus;
        this.clientThread = clientThread;
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
