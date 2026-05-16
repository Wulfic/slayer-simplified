/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Combined Info tab — merges Items Required, Combat, and Masters
 * into a single scrollable panel with distinct sections.
 */
package com.slayersimplified.presentation.components.tabs;

import com.slayersimplified.domain.Tab;
import com.slayersimplified.loot.CombatStats;
import com.slayersimplified.loot.WikiScraper;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Scrollable tab combining Items Required, Wiki Combat Stats, and Slayer Masters
 * into one panel with labelled sections. Combat stats are fetched asynchronously
 * from the OSRS Wiki.
 */
@Slf4j
public class InfoTab extends JScrollPane implements Tab<InfoTab.InfoData>
{
    private final JPanel contentPanel = new JPanel();
    private final OkHttpClient okHttpClient;

    /** Collapsible body panel for Wiki Combat Stats (populated asynchronously). */
    private JPanel wikiBodyPanel;

    private static final Color SECTION_HEADER_BG = ColorScheme.DARKER_GRAY_COLOR.darker();
    private static final Color STAT_VALUE_COLOR = Color.WHITE;
    private static final Font SECTION_FONT = FontManager.getRunescapeSmallFont().deriveFont(Font.BOLD);
    private static final Font BODY_FONT = FontManager.getRunescapeSmallFont();

    public InfoTab(OkHttpClient okHttpClient)
    {
        this.okHttpClient = okHttpClient;

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        setViewportView(contentPanel);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setBorder(null);
        getVerticalScrollBar().setUnitIncrement(16);
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_AS_NEEDED);
        ScrollBarStyling.apply(this);
    }

    @Override
    public void update(InfoData data)
    {
        contentPanel.removeAll();

        // -- Items Required section (collapsible) --
        JPanel itemsBody = addCollapsibleSection(contentPanel, "Items Required");
        if (data.items == null || data.items.length == 0)
        {
            addTextRowTo(itemsBody, "None");
        }
        else
        {
            for (String item : data.items)
            {
                addTextRowTo(itemsBody, StringUtils.capitalize(item));
            }
        }

        contentPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // -- Combat section (collapsible) --
        JPanel combatBody = addCollapsibleSection(contentPanel, "Combat");
        if (data.combat != null && data.combat.length == 2)
        {
            Object[] attackStyles = data.combat[0] != null ? data.combat[0] : new Object[0];
            Object[] attributes = data.combat[1] != null ? data.combat[1] : new Object[0];

            if (attackStyles.length > 0)
            {
                addSubHeaderTo(combatBody, "Attack Styles");
                for (Object style : attackStyles)
                {
                    addTextRowTo(combatBody, style.toString());
                }
            }

            if (attributes.length > 0)
            {
                addSubHeaderTo(combatBody, "Attributes");
                for (Object attr : attributes)
                {
                    addTextRowTo(combatBody, attr.toString());
                }
            }

            if (attackStyles.length == 0 && attributes.length == 0)
            {
                addTextRowTo(combatBody, "None");
            }
        }
        else
        {
            addTextRowTo(combatBody, "None");
        }

        contentPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // -- Wiki Combat Stats (collapsible, async) --
        wikiBodyPanel = addCollapsibleSection(contentPanel, "Wiki Combat Stats");
        addTextRowTo(wikiBodyPanel, "Loading...");

        contentPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        // -- Masters section (collapsible) --
        JPanel mastersBody = addCollapsibleSection(contentPanel, "Slayer Masters");
        if (data.masters == null || data.masters.length == 0)
        {
            addTextRowTo(mastersBody, "None");
        }
        else
        {
            for (String master : data.masters)
            {
                addTextRowTo(mastersBody, StringUtils.capitalize(master));
            }
        }

        contentPanel.revalidate();
        contentPanel.repaint();

        // Scroll to top
        SwingUtilities.invokeLater(() -> getVerticalScrollBar().setValue(0));

        // Fetch wiki combat stats asynchronously (cached after first lookup)
        if (data.monsterName != null && !data.monsterName.isEmpty())
        {
            fetchCombatStats(data.monsterName);
        }
    }

    private void fetchCombatStats(String monsterName)
    {
        WikiScraper.getCombatStats(okHttpClient, monsterName)
                .whenCompleteAsync((stats, ex) ->
                        SwingUtilities.invokeLater(() -> populateWikiCombatStats(stats)));
    }

    private void populateWikiCombatStats(CombatStats stats)
    {
        if (wikiBodyPanel == null)
        {
            return;
        }

        wikiBodyPanel.removeAll();

        if (stats == null || stats.getCombatLevel().isEmpty())
        {
            addTextRowTo(wikiBodyPanel, "No data found.");
            wikiBodyPanel.revalidate();
            wikiBodyPanel.repaint();
            contentPanel.revalidate();
            revalidate();
            return;
        }

        // Core stats
        addStatRow(wikiBodyPanel, "Combat Level", stats.getCombatLevel());
        addStatRow(wikiBodyPanel, "Hitpoints", stats.getHitpoints());
        addStatRow(wikiBodyPanel, "Max Hit", stats.getMaxHit());
        addStatRow(wikiBodyPanel, "Attack Style", stats.getAttackStyle());

        if (!stats.getAttribute().isEmpty())
        {
            addStatRow(wikiBodyPanel, "Attribute", stats.getAttribute());
        }

        // Weakness
        if (!stats.getElementalWeakness().isEmpty())
        {
            String weakness = stats.getElementalWeakness();
            if (!stats.getElementalWeaknessPercent().isEmpty())
            {
                weakness += " (" + stats.getElementalWeaknessPercent() + ")";
            }
            addStatRow(wikiBodyPanel, "Weakness", weakness);
        }

        // Immunities
        addSubHeaderTo(wikiBodyPanel, "Immunities");
        addImmunityRow(wikiBodyPanel, "Poison", stats.getImmunePoison());
        addImmunityRow(wikiBodyPanel, "Venom", stats.getImmuneVenom());
        addImmunityRow(wikiBodyPanel, "Cannons", stats.getImmuneCannon());
        addImmunityRow(wikiBodyPanel, "Thralls", stats.getImmuneThrall());
        addImmunityRow(wikiBodyPanel, "Burn", stats.getImmuneBurn());

        wikiBodyPanel.revalidate();
        wikiBodyPanel.repaint();
        contentPanel.revalidate();
        revalidate();
    }

    @Override
    public void shutDown()
    {
        contentPanel.removeAll();
    }

    // ---- Collapsible section helper ----

    /**
     * Creates a collapsible section with a clickable header and returns the body panel.
     * Clicking the header toggles the visibility of the body content.
     */
    private JPanel addCollapsibleSection(JPanel target, String title)
    {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(SECTION_HEADER_BG);
        header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        header.setBorder(new EmptyBorder(4, 8, 4, 4));
        header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel label = new JLabel("\u25BC " + title);
        label.setFont(SECTION_FONT);
        label.setForeground(ColorScheme.BRAND_ORANGE);
        header.add(label, BorderLayout.WEST);

        // Body
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        header.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                boolean visible = !body.isVisible();
                body.setVisible(visible);
                label.setText((visible ? "\u25BC " : "\u25B6 ") + title);
                contentPanel.revalidate();
                contentPanel.repaint();
            }
        });

        wrapper.add(header);
        wrapper.add(body);
        target.add(wrapper);

        return body;
    }

    // ---- Helpers that add to an arbitrary panel ----

    private void addSubHeaderTo(JPanel target, String text)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        panel.setBorder(new EmptyBorder(3, 12, 2, 4));

        JLabel label = new JLabel(text);
        label.setFont(SECTION_FONT);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        panel.add(label, BorderLayout.WEST);

        target.add(panel);
    }

    private void addTextRowTo(JPanel target, String text)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        row.setBorder(new EmptyBorder(2, 16, 2, 4));

        JLabel label = new JLabel(text);
        label.setFont(BODY_FONT);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(label, BorderLayout.WEST);

        target.add(row);
    }

    private void addStatRow(JPanel target, String label, String value)
    {
        if (value == null || value.isEmpty())
        {
            return;
        }

        // Name row
        JPanel nameRow = new JPanel(new BorderLayout());
        nameRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        nameRow.setBorder(new EmptyBorder(2, 16, 0, 8));

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(BODY_FONT);
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        nameRow.add(nameLabel, BorderLayout.WEST);
        target.add(nameRow);

        // Value row — split multi-entry values (e.g. "29 (Melee) 20 (Ranged)") onto separate lines
        JPanel valueRow = new JPanel(new BorderLayout());
        valueRow.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        valueRow.setBorder(new EmptyBorder(0, 24, 2, 8));

        String displayValue = value.contains(") ")
                ? "<html>" + value.replace(") ", ")<br>") + "</html>"
                : value;

        JLabel valueLabel = new JLabel(displayValue);
        valueLabel.setFont(SECTION_FONT);
        valueLabel.setForeground(STAT_VALUE_COLOR);
        valueRow.add(valueLabel, BorderLayout.WEST);
        target.add(valueRow);
    }

    private void addImmunityRow(JPanel target, String label, String value)
    {
        if (value == null || value.isEmpty())
        {
            return;
        }

        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        row.setBorder(new EmptyBorder(2, 20, 2, 8));

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(BODY_FONT);
        nameLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        row.add(nameLabel, BorderLayout.CENTER);

        // Color-code: green for immune, red-ish for not immune
        boolean isImmune = value.toLowerCase().startsWith("immune");
        boolean isNotImmune = value.toLowerCase().startsWith("not immune");
        Color valueColor = isImmune ? new Color(220, 60, 60)
                : isNotImmune ? new Color(100, 200, 100) : ColorScheme.LIGHT_GRAY_COLOR;

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(BODY_FONT);
        valueLabel.setForeground(valueColor);
        row.add(valueLabel, BorderLayout.EAST);

        target.add(row);
    }

    /**
     * Data holder for the combined info tab.
     */
    public static class InfoData
    {
        public final String monsterName;
        public final String[] items;
        public final Object[][] combat;
        public final String[] masters;

        public InfoData(String monsterName, String[] items, Object[][] combat, String[] masters)
        {
            this.monsterName = monsterName;
            this.items = items;
            this.combat = combat;
            this.masters = masters;
        }
    }
}
