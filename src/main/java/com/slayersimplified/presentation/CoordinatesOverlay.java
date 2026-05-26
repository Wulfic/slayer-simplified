/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Debug overlay that renders the player's current WorldPoint coordinates.
 * Enabled via the "Debug Coordinates" config toggle. Useful for mapping
 * new location entries in location_coordinates.json.
 */
package com.slayersimplified.presentation;

import com.slayersimplified.SlayerSimplifiedConfig;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;

@Singleton
public class CoordinatesOverlay extends Overlay
{
    private final Client client;
    private final SlayerSimplifiedConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    public CoordinatesOverlay(Client client, SlayerSimplifiedConfig config)
    {
        this.client = client;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.debugCoordinates())
        {
            return null;
        }

        if (client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null)
        {
            return null;
        }

        WorldPoint wp = client.getLocalPlayer().getWorldLocation();

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Location Debug")
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("X:")
                .right(String.valueOf(wp.getX()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Y:")
                .right(String.valueOf(wp.getY()))
                .build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Plane:")
                .right(String.valueOf(wp.getPlane()))
                .build());

        return panelComponent.render(graphics);
    }
}
