/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Refactored: displays each variant (plus the base monster itself) as a
 * collapsible accordion section, each with its own location rows,
 * favourite-star toggles, and Nav buttons.
 *
 * Section layout:
 *   [â–¼ Guard dog]              â† clickable header
 *     [Ardougne          â˜…  Nav]
 *     [Brimhaven         â˜…  Nav]
 *   [â–¶ Wild dog]               â† collapsed
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
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Locations tab that groups each variant's locations under a collapsible
 * accordion header. The base monster entry is listed first and starts
 * expanded; all other variants start collapsed.
 *
 * Section layout:
 *   [â–¼ Guard dog]              â† clickable header
 *     [Ardougne          â˜…  Nav]
 *     [Brimhaven         â˜…  Nav]
 *   [â–¶ Wild dog]               â† collapsed
 */
@Slf4j
public class LocationsTab extends JScrollPane implements Tab<LocationsTab.LocationsData>
{
    // â”€â”€ Data model â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Data passed to {@link #update(LocationsData)}.
     * The base monster is always the first variant entry.
     */
    public static class LocationsData
    {
        public final String monsterName;
        public final java.util.List<VariantEntry> variants;

        public LocationsData(String monsterName, java.util.List<VariantEntry> variants)
        {
            this.monsterName = monsterName;
            this.variants = variants;
        }

        /** One accordion section: a named variant and its specific locations. */
        public static class VariantEntry
        {
            public final String name;
            public final String[] locations;

            public VariantEntry(String name, String[] locations)
            {
                this.name = name;
                this.locations = locations;
            }
        }
    }

    // â”€â”€ Services â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private final NavigationService navigationService;
    private final LocationCoordinateService locationCoordinateService;
    private final FavoriteLocationService favoriteService;
    private final LocationRequirementService requirementService;
    private final Supplier<Boolean> debugMode;

    // â”€â”€ Layout â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Root scrollable panel â€” sections are added here. */
    private final JPanel contentPanel = new ViewportWidthPanel();

    /** Debug info row shown at top when debug mode is on. */
    private final JPanel debugPanel = new JPanel(new BorderLayout());
    private final JLabel debugNavLabel = new JLabel("No nav yet");

    // â”€â”€ State â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Tracked so listeners can be removed on rebuild. */
    private final List<JButton> buttons = new ArrayList<>();
    private final List<ActionListener> listeners = new ArrayList<>();

    /** All location row panels across every section, for bulk colour resets. */
    private final List<JPanel> allLocationRows = new ArrayList<>();

    /** Location currently being navigated to, or null. */
    private String activeLocation = null;

    /** Monster name from the most recent {@link #update} call. */
    private String currentMonsterName = null;

    // â”€â”€ Constructor â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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

    // â”€â”€ Tab interface â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void update(LocationsData data)
    {
        clearRows();
        activeLocation = null;

        debugPanel.setVisible(debugMode.get());
        if (debugMode.get())
        {
            WorldPoint last = navigationService.getLastTarget();
            debugNavLabel.setText(last != null
                    ? "Last Nav â†’ x:" + last.getX() + "  y:" + last.getY() + "  plane:" + last.getPlane()
                    : "No nav yet");
        }

        if (data == null || data.variants == null || data.variants.isEmpty())
        {
            addPlaceholderRow("None");
            contentPanel.revalidate();
            contentPanel.repaint();
            return;
        }

        currentMonsterName = data.monsterName;

        if (data.variants.size() == 1)
        {
            // Only one section: render locations flat without an accordion header
            buildFlatLocations(data.variants.get(0));
        }
        else
        {
            // Multiple variants: full accordion layout
            boolean firstSection = true;
            for (LocationsData.VariantEntry variant : data.variants)
            {
                contentPanel.add(buildVariantSection(variant, firstSection));
                firstSection = false;
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

    // ── Flat layout (single variant) ──────────────────────────────────────────────────────────────────

    private void buildFlatLocations(LocationsData.VariantEntry variant)
    {
        if (variant.locations == null || variant.locations.length == 0)
        {
            addPlaceholderRow("None");
            return;
        }

        List<String> valid = new ArrayList<>();
        for (String loc : variant.locations)
        {
            if (loc == null || loc.isEmpty()) continue;
            valid.add(loc);
            JPanel row = createLocationRow(loc, variant.name);
            contentPanel.add(row);
            allLocationRows.add(row);
        }

        // Auto-favourite when exactly one accessible location and none set yet
        if (valid.size() == 1 && currentMonsterName != null
                && favoriteService.getFavoriteForVariant(currentMonsterName, variant.name) == null
                && (debugMode.get() || requirementService.isAvailable(valid.get(0))))
        {
            favoriteService.setFavoriteForVariant(currentMonsterName, variant.name, valid.get(0));
            for (Component comp : contentPanel.getComponents())
            {
                if (comp instanceof JPanel && comp != debugPanel)
                {
                    updateStarsInRow((JPanel) comp, variant.name);
                }
            }
        }
    }

    // â”€â”€ Accordion section â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private JPanel buildVariantSection(LocationsData.VariantEntry variant, boolean startExpanded)
    {
        JPanel section = new JPanel()
        {
            @Override
            public Dimension getMaximumSize()
            {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        // â”€â”€ Header â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel header = new JPanel(new BorderLayout(6, 0));
        header.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
                new EmptyBorder(5, 8, 5, 8)
        ));
        header.setCursor(new Cursor(Cursor.HAND_CURSOR));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel arrowLabel = new JLabel(startExpanded ? "\u25BC" : "\u25BA");
        arrowLabel.setFont(FontManager.getRunescapeSmallFont());
        arrowLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JLabel nameLabel = new JLabel(variant.name);
        nameLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(
                Font.BOLD, FontManager.getRunescapeSmallFont().getSize2D() + 1f));
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        header.add(arrowLabel, BorderLayout.WEST);
        header.add(nameLabel, BorderLayout.CENTER);

        // â”€â”€ Locations list â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        JPanel locPanel = new JPanel();
        locPanel.setLayout(new BoxLayout(locPanel, BoxLayout.Y_AXIS));
        locPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        locPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        locPanel.setVisible(startExpanded);

        boolean hasLocations = variant.locations != null && variant.locations.length > 0;

        if (!hasLocations)
        {
            JLabel noLocs = new JLabel("No locations mapped");
            noLocs.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            noLocs.setFont(FontManager.getRunescapeSmallFont());
            noLocs.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 8));
            locPanel.add(noLocs);
        }
        else
        {
            List<String> valid = new ArrayList<>();
            for (String loc : variant.locations)
            {
                if (loc == null || loc.isEmpty())
                {
                    continue;
                }
                valid.add(loc);
                JPanel row = createLocationRow(loc, variant.name);
                locPanel.add(row);
                allLocationRows.add(row);
            }

            // Auto-favourite when there is exactly one accessible location and none set yet
            if (valid.size() == 1 && currentMonsterName != null
                    && favoriteService.getFavoriteForVariant(currentMonsterName, variant.name) == null
                    && (debugMode.get() || requirementService.isAvailable(valid.get(0))))
            {
                favoriteService.setFavoriteForVariant(currentMonsterName, variant.name, valid.get(0));
                for (Component comp : locPanel.getComponents())
                {
                    if (comp instanceof JPanel)
                    {
                        updateStarsInRow((JPanel) comp, variant.name);
                    }
                }
            }
        }

        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                boolean nowExpanded = !locPanel.isVisible();
                locPanel.setVisible(nowExpanded);
                arrowLabel.setText(nowExpanded ? "\u25BC" : "\u25BA");
                revalidate();
                repaint();
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                header.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                header.setBackground(ColorScheme.DARK_GRAY_COLOR);
            }
        });

        section.add(header);
        section.add(locPanel);
        return section;
    }

    // â”€â”€ Location row â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private JPanel createLocationRow(String locationName, String variantName)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.putClientProperty("locationName", locationName);
        row.putClientProperty("variantName", variantName);
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                BorderFactory.createEmptyBorder(4, 16, 4, 4)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        JLabel label = new JLabel("<html>" + capitalize(locationName) + "</html>");
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setFont(FontManager.getRunescapeSmallFont());
        row.add(label, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.setOpaque(false);

        WorldPoint coords = locationCoordinateService.getCoordinates(locationName);
        boolean debug = debugMode.get();
        boolean reqMet = debug || requirementService.isAvailable(locationName);
        String reqDesc = requirementService.getRequirementDescription(locationName);
        String missing = reqMet ? "" : requirementService.getMissingText(locationName);

        boolean isFav = currentMonsterName != null
                && favoriteService.isFavoriteForVariant(currentMonsterName, variantName, locationName);
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
            favButton.setToolTipText(isFav ? "Remove favourite" : "Set as favourite location");
            favButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            ActionListener favListener = e -> onFavouriteClicked(locationName, variantName, row);
            favButton.addActionListener(favListener);
            buttons.add(favButton);
            listeners.add(favListener);
        }
        buttonPanel.add(favButton);
        buttonPanel.add(Box.createHorizontalStrut(2));

        if (!reqMet)
        {
            label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
            label.setToolTipText("Requires: " + missing);
        }
        else if (!reqDesc.isEmpty())
        {
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
                row.setBackground(locationName.equals(activeLocation)
                        ? new Color(50, 70, 50)
                        : ColorScheme.DARKER_GRAY_COLOR);
            }
        });

        return row;
    }

    // â”€â”€ Favourite / nav interactions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void onFavouriteClicked(String locationName, String variantName, JPanel clickedRow)
    {
        if (currentMonsterName == null)
        {
            return;
        }

        boolean nowFavourite = favoriteService.toggleFavoriteForVariant(currentMonsterName, variantName, locationName);

        // Refresh stars for all rows in the same variant's locPanel
        Container locPanel = clickedRow.getParent();
        if (locPanel != null)
        {
            for (Component comp : locPanel.getComponents())
            {
                if (comp instanceof JPanel)
                {
                    updateStarsInRow((JPanel) comp, variantName);
                }
            }
        }

        log.debug("{} {} as favourite for {} / variant {}",
                nowFavourite ? "Set" : "Cleared", locationName, currentMonsterName, variantName);
    }

    private void updateStarsInRow(JPanel row, String variantName)
    {
        Component east = ((BorderLayout) row.getLayout()).getLayoutComponent(BorderLayout.EAST);
        if (!(east instanceof JPanel))
        {
            return;
        }

        Object locNameProp = row.getClientProperty("locationName");
        String locationName = (locNameProp instanceof String) ? (String) locNameProp : null;
        if (locationName == null || currentMonsterName == null)
        {
            return;
        }

        boolean isFav = favoriteService.isFavoriteForVariant(currentMonsterName, variantName, locationName);

        for (Component btn : ((JPanel) east).getComponents())
        {
            if (btn instanceof JButton)
            {
                String text = ((JButton) btn).getText();
                if ("\u2605".equals(text) || "\u2606".equals(text))
                {
                    ((JButton) btn).setText(isFav ? "\u2605" : "\u2606");
                    btn.setForeground(isFav ? new Color(255, 215, 0) : ColorScheme.LIGHT_GRAY_COLOR);
                    ((JButton) btn).setToolTipText(isFav ? "Remove favourite" : "Set as favourite location");
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
            debugNavLabel.setText("Last Nav â†’ x:" + coords.getX() + "  y:" + coords.getY() + "  plane:" + coords.getPlane());
        }

        resetAllRowColors();
        row.setBackground(new Color(50, 70, 50));
        log.debug("Navigating to {} at {}", locationName, coords);
    }

    // â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
        for (JPanel row : allLocationRows)
        {
            Object loc = row.getClientProperty("locationName");
            if (loc instanceof String && !loc.equals(activeLocation))
            {
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
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
        allLocationRows.clear();
        contentPanel.removeAll();
        contentPanel.add(debugPanel, 0);
    }

    // â”€â”€ ViewportWidthPanel â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

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
