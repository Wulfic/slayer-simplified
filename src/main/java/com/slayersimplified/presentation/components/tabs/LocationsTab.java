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
import com.slayersimplified.services.LocationRequirementService;
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
import java.util.function.Supplier;
import javax.swing.Scrollable;

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
    private final LocationRequirementService requirementService;
    private final Supplier<Boolean> debugMode;

    /** Inner panel holding the location rows — Scrollable so the JScrollPane constrains its width to the viewport. */
    private final JPanel contentPanel = new ViewportWidthPanel();

    /** Debug panel shown at the top of the locations list when debug mode is enabled. */
    private final JPanel debugPanel = new JPanel(new BorderLayout());
    private final JLabel debugNavLabel = new JLabel("No nav yet");

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
            FavoriteLocationService favoriteService,
            LocationRequirementService requirementService,
            Supplier<Boolean> debugMode)
    {
        this.navigationService = navigationService;
        this.locationCoordinateService = locationCoordinateService;
        this.favoriteService = favoriteService;
        this.requirementService = requirementService;
        this.debugMode = debugMode;

        // Debug info panel — shown at top when debug mode is on
        debugNavLabel.setForeground(new Color(100, 220, 255));
        debugNavLabel.setFont(FontManager.getRunescapeSmallFont());
        debugPanel.setBackground(new Color(30, 30, 50));
        debugPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 60, 90)),
                BorderFactory.createEmptyBorder(3, 8, 3, 4)
        ));
        debugPanel.add(debugNavLabel, BorderLayout.CENTER);
        debugPanel.setVisible(false);

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        contentPanel.add(debugPanel);

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

        // Show/hide debug panel based on current debug mode setting
        debugPanel.setVisible(debugMode.get());
        if (debugMode.get())
        {
            WorldPoint last = navigationService.getLastTarget();
            if (last != null)
            {
                debugNavLabel.setText("Last Nav → x:" + last.getX() + "  y:" + last.getY() + "  plane:" + last.getPlane());
            }
            else
            {
                debugNavLabel.setText("No nav yet");
            }
        }

        if (locations == null || locations.length == 0)
        {
            addPlaceholderRow("None");
            return;
        }

        List<String> valid = new ArrayList<>();
        for (String location : locations)
        {
            if (location == null || location.isEmpty())
            {
                continue;
            }
            valid.add(location);
            contentPanel.add(createLocationRow(location));
        }

        // Auto-favorite when there is exactly one location and none has been
        // set yet — but only if the player actually meets its requirements
        // (or debug mode is on). Otherwise we'd silently pin an unreachable
        // location for them.
        if (valid.size() == 1 && currentMonsterName != null
                && favoriteService.getFavorite(currentMonsterName) == null
                && (debugMode.get() || requirementService.isAvailable(valid.get(0))))
        {
            favoriteService.setFavorite(currentMonsterName, valid.get(0));
            for (Component comp : contentPanel.getComponents())
            {
                if (comp instanceof JPanel)
                {
                    updateStarsInRow((JPanel) comp);
                }
            }
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
        // Allow up to two lines so long names wrap rather than push buttons off-screen
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        // HTML label wraps text — safe now that contentPanel tracks viewport width
        JLabel label = new JLabel("<html>" + capitalize(locationName) + "</html>");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        row.add(label, BorderLayout.CENTER);

        // Button panel on the right (favorite + nav)
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setOpaque(false);

        // Navigate button (only if coordinates exist)
        WorldPoint coords = locationCoordinateService.getCoordinates(locationName);

        // Requirement check — debug mode bypasses to allow testing every location.
        boolean debug = debugMode.get();
        boolean reqMet = debug || requirementService.isAvailable(locationName);
        String reqDesc = requirementService.getRequirementDescription(locationName);
        String missing = reqMet ? "" : requirementService.getMissingText(locationName);

        // Favorite toggle star — disabled when requirements aren't met so the
        // player can't pin a location they can't actually reach.
        boolean isFav = currentMonsterName != null
                && favoriteService.isFavorite(currentMonsterName, locationName);
        JButton favButton = new JButton(isFav ? "\u2605" : "\u2606");
        favButton.setFont(favButton.getFont().deriveFont(Font.PLAIN, 16f));
        favButton.setPreferredSize(new Dimension(28, 24));
        favButton.setFocusPainted(false);
        favButton.setBorderPainted(false);
        favButton.setContentAreaFilled(false);

        if (!reqMet)
        {
            favButton.setEnabled(false);
            favButton.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            favButton.setToolTipText("Requires: " + missing);
            favButton.setCursor(Cursor.getDefaultCursor());
        }
        else
        {
            favButton.setForeground(isFav ? new Color(255, 215, 0) : ColorScheme.LIGHT_GRAY_COLOR);
            favButton.setToolTipText(isFav ? "Remove favorite" : "Set as favorite location");
            favButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            ActionListener favListener = e -> onFavoriteClicked(locationName, favButton);
            favButton.addActionListener(favListener);
            buttons.add(favButton);
            listeners.add(favListener);
        }
        buttonPanel.add(favButton);
        buttonPanel.add(Box.createHorizontalStrut(2));

        if (!reqMet)
        {
            // Gray the label so the row reads as unavailable.
            label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            label.setToolTipText("Requires: " + missing);
        }
        else if (!reqDesc.isEmpty())
        {
            // Met but informational — surface the requirement on hover.
            label.setToolTipText("Requires: " + reqDesc);
        }

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

            if (!reqMet)
            {
                navButton.setEnabled(false);
                navButton.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
                navButton.setToolTipText("Requires: " + missing);
            }
            else
            {
                ActionListener navListener = e -> onNavigateClicked(locationName, coords, row);
                navButton.addActionListener(navListener);
                buttons.add(navButton);
                listeners.add(navListener);
            }
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

        if (debugMode.get())
        {
            debugNavLabel.setText("Last Nav → x:" + coords.getX() + "  y:" + coords.getY() + "  plane:" + coords.getPlane());
        }

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
        // Debug panel is always the first child — re-add it after clearing
        contentPanel.add(debugPanel, 0);
    }

    /**
     * A JPanel that implements Scrollable so the enclosing JScrollPane always
     * sizes it to match the viewport width. This ensures BoxLayout rows are
     * constrained to the visible area and right-side buttons stay on screen.
     */
    private static class ViewportWidthPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 128;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
}
