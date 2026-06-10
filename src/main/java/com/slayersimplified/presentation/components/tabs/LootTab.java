/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components.tabs;

import com.google.gson.Gson;
import com.slayersimplified.domain.Tab;
import com.slayersimplified.loot.DropTableSection;
import com.slayersimplified.loot.WikiItem;
import com.slayersimplified.loot.WikiPriceCache;
import com.slayersimplified.loot.WikiScraper;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import okhttp3.OkHttpClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Tab that displays the loot/drop table for a monster by scraping the OSRS Wiki.
 * Automatically looks up the selected monster and displays all drop sections
 * in a scrollable list.
 */
@Slf4j
public class LootTab extends JScrollPane implements Tab<String>
{
    private final OkHttpClient okHttpClient;
    private final Gson gson;
    private final JPanel contentPanel = new ScrollablePanel();
    private String currentMonster;
    /** Incremented on every new fetch; lets async callbacks discard stale responses. */
    private int requestId = 0;

    private static final Color SECTION_HEADER_BG = ColorScheme.DARKER_GRAY_COLOR.darker();
    private static final Color ITEM_BG = ColorScheme.DARKER_GRAY_COLOR;
    private static final Color ITEM_BG_ALT = new Color(
            ITEM_BG.getRed() + 5, ITEM_BG.getGreen() + 5, ITEM_BG.getBlue() + 5);

    private static final Color RARITY_COMMON = Color.WHITE;
    private static final Color RARITY_RARE = ColorScheme.BRAND_ORANGE.brighter();
    private static final Color RARITY_SUPER_RARE = new Color(200, 50, 200);
    private static final Color PRICE_COLOR = ColorScheme.GRAND_EXCHANGE_ALCH;

    public LootTab(OkHttpClient okHttpClient, Gson gson)
    {
        this.okHttpClient = okHttpClient;
        this.gson = gson;

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        setViewportView(contentPanel);
        getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(null);
        getVerticalScrollBar().setUnitIncrement(16);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ScrollBarStyling.apply(this);
    }

    @Override
    public void update(String monsterName)
    {
        if (monsterName == null || monsterName.isEmpty())
        {
            return;
        }

        // Don't re-fetch if we already have data for this monster
        if (monsterName.equals(currentMonster))
        {
            return;
        }
        currentMonster = monsterName;
        final int thisRequest = ++requestId;

        contentPanel.removeAll();
        showLoadingState();

        WikiScraper.getDropsByMonster(okHttpClient, monsterName)
                .thenCompose(sections -> WikiPriceCache.enrichDropTables(okHttpClient, gson, sections))
                .whenCompleteAsync((dropTableSections, ex) ->
                {
                    SwingUtilities.invokeLater(() ->
                    {
                        // Discard if the user has already moved to a different task
                        if (thisRequest != requestId)
                        {
                            return;
                        }

                        contentPanel.removeAll();

                        if (ex != null || dropTableSections == null || dropTableSections.length == 0)
                        {
                            // Clear so re-selecting this task retries the fetch
                            currentMonster = null;
                            showEmptyState();
                            return;
                        }

                        buildDropTables(dropTableSections);

                        contentPanel.revalidate();
                        contentPanel.repaint();
                        revalidate();
                        repaint();

                        // Scroll to top after layout settles
                        SwingUtilities.invokeLater(() ->
                        {
                            getVerticalScrollBar().setValue(0);
                            revalidate();
                        });
                    });
                });
    }

    @Override
    public void shutDown()
    {
        contentPanel.removeAll();
        currentMonster = null;
    }

    /**
     * Force a re-fetch of the current monster's loot data.
     */
    public void refresh()
    {
        String name = currentMonster;
        currentMonster = null;
        if (name != null)
        {
            update(name);
        }
    }

    private void showLoadingState()
    {
        JLabel loadingLabel = new JLabel("Loading loot table...");
        loadingLabel.setFont(FontManager.getRunescapeSmallFont());
        loadingLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        loadingLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        loadingLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
        contentPanel.add(loadingLabel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void showEmptyState()
    {
        JLabel emptyLabel = new JLabel("No loot data found.");
        emptyLabel.setFont(FontManager.getRunescapeSmallFont());
        emptyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyLabel.setBorder(new EmptyBorder(20, 10, 20, 10));
        contentPanel.add(emptyLabel);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void buildDropTables(DropTableSection[] sections)
    {
        // Many monsters split their drops across several variant tables (e.g. by combat
        // level, world type, or location). Each variant repeats the same category names
        // ("100%", "Weapons and armour", ...), so the variant header must be shown to tell
        // them apart — otherwise the categories look like they are listed multiple times.
        int renderable = 0;
        for (DropTableSection section : sections)
        {
            if (section.getTable() != null && !section.getTable().isEmpty())
            {
                renderable++;
            }
        }
        boolean showVariantHeaders = renderable > 1;

        for (DropTableSection section : sections)
        {
            if (section.getTable() == null || section.getTable().isEmpty())
            {
                continue;
            }

            String variant = section.getHeader();
            if (showVariantHeaders && variant != null && !variant.isEmpty())
            {
                contentPanel.add(createVariantHeader(variant));
            }

            for (var entry : section.getTable().entrySet())
            {
                String subHeader = entry.getKey();
                WikiItem[] items = entry.getValue();

                // Skip a category header that merely repeats the variant header (flat tables).
                if (!(showVariantHeaders && subHeader.equals(variant)))
                {
                    contentPanel.add(createSectionHeader(subHeader));
                }

                // Items
                for (int i = 0; i < items.length; i++)
                {
                    JPanel itemRow = createItemRow(items[i], i % 2 == 1);
                    contentPanel.add(itemRow);
                }

                contentPanel.add(Box.createRigidArea(new Dimension(0, 4)));
            }
        }
    }

    /**
     * Top-level header naming a drop-table variant (combat level, world type, location, ...).
     * Styled more prominently than {@link #createSectionHeader} so the category sub-headers
     * beneath it read as belonging to this variant.
     */
    private JPanel createVariantHeader(String text)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(5, 8, 5, 4)));

        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);
        panel.add(label, BorderLayout.WEST);
        panel.setToolTipText(text);

        return panel;
    }

    private JPanel createSectionHeader(String text)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SECTION_HEADER_BG);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        panel.setBorder(new EmptyBorder(4, 8, 4, 4));

        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeBoldFont());
        label.setForeground(ColorScheme.BRAND_ORANGE);
        panel.add(label, BorderLayout.WEST);

        return panel;
    }

    private JPanel createItemRow(WikiItem item, boolean alt)
    {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(alt ? ITEM_BG_ALT : ITEM_BG);
        row.setBorder(new EmptyBorder(3, 8, 3, 4));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        // Left: name + quantity
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);

        String displayName = item.getName();
        if (displayName.length() > 22)
        {
            displayName = displayName.substring(0, 20) + "...";
        }

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        if (!displayName.equals(item.getName()))
        {
            nameLabel.setToolTipText(item.getName());
        }
        leftPanel.add(nameLabel);

        String qtyText = item.getQuantityLabelText();
        if (!qtyText.isEmpty())
        {
            JLabel qtyLabel = new JLabel(qtyText);
            qtyLabel.setFont(FontManager.getRunescapeSmallFont());
            qtyLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            leftPanel.add(qtyLabel);
        }

        // Right: rarity + price
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setOpaque(false);

        JLabel rarityLabel = new JLabel(item.getRarityLabelText());
        rarityLabel.setFont(FontManager.getRunescapeSmallFont());
        rarityLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        rarityLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        rarityLabel.setForeground(getRarityColor(item));
        rightPanel.add(rarityLabel);

        String priceText = item.getPriceLabelText();
        if (!priceText.isEmpty())
        {
            JLabel priceLabel = new JLabel(priceText);
            priceLabel.setFont(FontManager.getRunescapeSmallFont());
            priceLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            priceLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
            priceLabel.setForeground(PRICE_COLOR);
            rightPanel.add(priceLabel);
        }

        row.add(leftPanel, BorderLayout.CENTER);
        row.add(rightPanel, BorderLayout.EAST);

        // Tooltip with full info
        row.setToolTipText(String.format("%s — %s — %s",
                item.getName(), item.getQuantityLabelText(), item.getRarityLabelText()));

        return row;
    }

    private Color getRarityColor(WikiItem item)
    {
        if (item.getRarity() > 0)
        {
            if (item.getRarity() <= 0.001) return RARITY_SUPER_RARE;
            if (item.getRarity() <= 0.01) return RARITY_RARE;
        }
        return RARITY_COMMON;
    }

    /**
     * A JPanel that implements Scrollable to always match the viewport width,
     * preventing content from extending under the scrollbar.
     */
    private static class ScrollablePanel extends JPanel implements Scrollable
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
            return visibleRect.height;
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
