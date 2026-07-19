/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.services.TaskEngagementService;
import com.slayersimplified.services.TileNoteService;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import net.runelite.api.Point;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Map;

/**
 * Renders a highlighted tile polygon and location label on every training-spot
 * tile for the current slayer task.  Only active when the "TileNotes" setting
 * is enabled.
 *
 * <p>Tile data comes from {@link TileNoteService}; only tiles that fall within
 * the player's loaded scene are drawn.
 */
@Singleton
public class TileNoteOverlay extends Overlay
{
    private static final Color FILL_COLOR = new Color(255, 200, 0, 45);
    private static final Color BORDER_COLOR = new Color(255, 200, 0, 200);
    private static final Color LABEL_COLOR = new Color(255, 220, 60);

    private final Client client;
    private final SlayerSimplifiedConfig config;
    private final TileNoteService tileNoteService;
    private final TaskEngagementService engagement;

    @Inject
    public TileNoteOverlay(Client client, SlayerSimplifiedConfig config,
                           TileNoteService tileNoteService, TaskEngagementService engagement)
    {
        this.client = client;
        this.config = config;
        this.tileNoteService = tileNoteService;
        this.engagement = engagement;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.tileNotes())
        {
            return null;
        }

        Map<String, WorldPoint> tiles;
        if (config.debugCoordinates())
        {
            // Location Debug: show all known tile notes, respecting the non-slayer filter
            tiles = tileNoteService.getAllDebugTiles(config.showNonSlayerEnemies());
        }
        else
        {
            // Normal mode: gated behind task engagement so markers only show
            // once the player is actually working the task (see TaskEngagementService).
            if (!engagement.shouldShowOverlays())
            {
                return null;
            }
            // Normal mode: only the current/last-navigated task
            if (tileNoteService.isCurrentTaskNonSlayer() && !config.showNonSlayerEnemies())
            {
                return null;
            }
            tiles = tileNoteService.getCurrentTaskTiles();
        }

        if (tiles.isEmpty())
        {
            return null;
        }

        for (Map.Entry<String, WorldPoint> entry : tiles.entrySet())
        {
            WorldPoint wp = entry.getValue();
            LocalPoint lp = LocalPoint.fromWorld(client.getTopLevelWorldView(), wp);
            if (lp == null)
            {
                // Tile is outside the loaded scene
                continue;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null)
            {
                continue;
            }

            graphics.setColor(FILL_COLOR);
            graphics.fillPolygon(poly);
            graphics.setColor(BORDER_COLOR);
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.drawPolygon(poly);

            String label = entry.getKey();
            Point textPoint = Perspective.getCanvasTextLocation(client, graphics, lp, label, 0);
            if (textPoint != null)
            {
                OverlayUtil.renderTextLocation(graphics, textPoint, label, LABEL_COLOR);
            }
        }

        return null;
    }
}
