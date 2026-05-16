/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * NEW component — replaces the plain TextTab for the Locations tab.
 * Renders each location as a row with a favorite star toggle and a
 * "Nav" button that sends a path request to the Shortest Path plugin.
 */
package com.slayersimplified.presentation.components.tabs;

import com.slayersimplified.domain.Tab;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import com.slayersimplified.services.FavoriteLocationService;
import com.slayersimplified.services.LocationCoordinateService;
import com.slayersimplified.services.NavigationService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrollable tab that displays Slayer monster locations with favorite
 * toggles and navigate buttons. Extends JScrollPane so the location
 * list scrolls when it overflows the tab area.
 *
 * Each row layout:  [Location Name    ★  Nav]
 *   ★  = favorite toggle (filled when this is the preferred location)
 *   Nav = sends a path request to Shortest Path via NavigationService
 *
 * Locations without mapped coordinates show a "—" instead of Nav.
 */
@Slf4j
public class LocationsTab extends JScrollPane implements Tab<String[]>
{
    private final NavigationService navigationService;
    private final LocationCoordinateService locationCoordinateService;
    private final FavoriteLocationService favoriteService;

    /** Inner panel holding the location rows. */
    private final JPanel contentPanel = new JPanel();

    /** Track buttons and listeners for cleanup on shutdown. */
    private final List<JButton> buttons = new ArrayList<>();
    private final List<ActionListener> listeners = new ArrayList<>();

    /** Currently active navigation target, if any. */
    private String activeLocation = null;

    /** The monster whose locations are currently displayed. */
    private String currentMonsterName = null;

    public LocationsTab(
            NavigationService navigationService,
            LocationCoordinateService locationCoordinateService,
            FavoriteLocationService favoriteService)
    {
        this.navigationService = navigationService;
        this.locationCoordinateService = locationCoordinateService;
        this.favoriteService = favoriteService;

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        setViewportView(contentPanel);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(null);
        getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ScrollBarStyling.apply(this);
    }

    /**
     * Set the current monster name before calling update().
     * Needed so favorites can be stored per-monster.
     */
    public void setCurrentMonster(String monsterName)
    {
        this.currentMonsterName = monsterName;
    }

    @Override
    public void update(String[] locations)
    {
        clearRows();
        activeLocation = null;

        if (locations == null || locations.length == 0)
        {
            addPlaceholderRow("None");
            return;
        }

        for (String location : locations)
        {
            if (location == null || location.isEmpty())
            {
                continue;
            }
            contentPanel.add(createLocationRow(location));
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    @Override
    public void shutDown()
    {
        navigationService.clearNavigation();
        clearRows();
    }

    /**
     * Builds a single location row:  [label    ★  Nav]
     */
    private JPanel createLocationRow(String locationName)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.putClientProperty("locationName", locationName);
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(4, 8, 4, 4)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        // Location name label — plain font matching the rest of the UI
        JLabel label = new JLabel(capitalize(locationName));
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        row.add(label, BorderLayout.CENTER);

        // Button panel on the right (favorite + nav)
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setOpaque(false);

        // Favorite toggle star
        boolean isFav = currentMonsterName != null
                && favoriteService.isFavorite(currentMonsterName, locationName);
        JButton favButton = new JButton(isFav ? "\u2605" : "\u2606");
        favButton.setFont(favButton.getFont().deriveFont(Font.PLAIN, 16f));
        favButton.setPreferredSize(new Dimension(28, 24));
        favButton.setFocusPainted(false);
        favButton.setBorderPainted(false);
        favButton.setContentAreaFilled(false);
        favButton.setForeground(isFav ? new Color(255, 215, 0) : ColorScheme.LIGHT_GRAY_COLOR);
        favButton.setToolTipText(isFav ? "Remove favorite" : "Set as favorite location");
        favButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        ActionListener favListener = e -> onFavoriteClicked(locationName, favButton);
        favButton.addActionListener(favListener);
        buttons.add(favButton);
        listeners.add(favListener);
        buttonPanel.add(favButton);
        buttonPanel.add(Box.createHorizontalStrut(2));

        // Navigate button (only if coordinates exist)
        WorldPoint coords = locationCoordinateService.getCoordinates(locationName);

        if (coords != null)
        {
            JButton navButton = new JButton("Nav");
            navButton.setFont(FontManager.getRunescapeSmallFont());
            navButton.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            navButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            navButton.setPreferredSize(new Dimension(50, 24));
            navButton.setMinimumSize(new Dimension(50, 24));
            navButton.setMaximumSize(new Dimension(50, 24));
            navButton.setFocusPainted(false);
            navButton.setMargin(new Insets(0, 2, 0, 2));
            navButton.setToolTipText("Navigate to " + capitalize(locationName));

            ActionListener navListener = e -> onNavigateClicked(locationName, coords, row);
            navButton.addActionListener(navListener);
            buttons.add(navButton);
            listeners.add(navListener);
            buttonPanel.add(navButton);
        }
        else
        {
            JLabel noNav = new JLabel("\u2014");
            noNav.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            noNav.setToolTipText("Coordinates not yet mapped");
            buttonPanel.add(noNav);
        }

        row.add(buttonPanel, BorderLayout.EAST);

        // Hover effect
        row.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e)
            {
                row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                if (locationName.equals(activeLocation))
                {
                    row.setBackground(new Color(50, 70, 50));
                }
                else
                {
                    row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
                }
            }
        });

        return row;
    }

    /**
     * Toggle favorite and update all star icons so only one is filled.
     */
    private void onFavoriteClicked(String locationName, JButton clickedStar)
    {
        if (currentMonsterName == null)
        {
            return;
        }

        boolean nowFavorite = favoriteService.toggleFavorite(currentMonsterName, locationName);

        // Refresh all star buttons in the current view
        for (Component comp : contentPanel.getComponents())
        {
            if (comp instanceof JPanel)
            {
                updateStarsInRow((JPanel) comp);
            }
        }

        log.debug("{} {} as favorite for {}",
                nowFavorite ? "Set" : "Cleared", locationName, currentMonsterName);
    }

    /**
     * Finds the star button in a row and updates its state.
     */
    private void updateStarsInRow(JPanel row)
    {
        // The EAST component is the buttonPanel
        Component east = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST);
        if (!(east instanceof JPanel))
        {
            return;
        }

        // Read the original location name stored as a client property (avoids label-text casing mismatch)
        Object locNameProp = row.getClientProperty("locationName");
        String locationName = (locNameProp instanceof String) ? (String) locNameProp : null;
        if (locationName == null || currentMonsterName == null)
        {
            return;
        }

        boolean isFav = favoriteService.isFavorite(currentMonsterName, locationName);

        for (Component btn : ((JPanel) east).getComponents())
        {
            if (btn instanceof JButton)
            {
                String text = ((JButton) btn).getText();
                if ("\u2605".equals(text) || "\u2606".equals(text))
                {
                    ((JButton) btn).setText(isFav ? "\u2605" : "\u2606");
                    btn.setForeground(isFav ? new Color(255, 215, 0) : ColorScheme.LIGHT_GRAY_COLOR);
                    ((JButton) btn).setToolTipText(isFav ? "Remove favorite" : "Set as favorite location");
                }
            }
        }
    }

    private void onNavigateClicked(String locationName, WorldPoint coords, JPanel row)
    {
        if (locationName.equals(activeLocation))
        {
            navigationService.clearNavigation();
            activeLocation = null;
            resetAllRowColors();
            log.debug("Cleared navigation for {}", locationName);
            return;
        }

        navigationService.navigateTo(coords);
        activeLocation = locationName;

        resetAllRowColors();
        row.setBackground(new Color(50, 70, 50));

        log.debug("Navigating to {} at {}", locationName, coords);
    }

    private void addPlaceholderRow(String text)
    {
        JLabel label = new JLabel(text);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        contentPanel.add(label);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void resetAllRowColors()
    {
        for (Component comp : contentPanel.getComponents())
        {
            if (comp instanceof JPanel)
            {
                comp.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }
        }
    }

    private static String capitalize(String s)
    {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void clearRows()
    {
        for (int i = 0; i < buttons.size(); i++)
        {
            buttons.get(i).removeActionListener(listeners.get(i));
        }
        buttons.clear();
        listeners.clear();
        contentPanel.removeAll();
    }
}
