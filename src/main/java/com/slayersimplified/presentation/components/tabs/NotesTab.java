/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components.tabs;

import com.slayersimplified.domain.Tab;
import com.slayersimplified.presentation.components.ScrollBarStyling;
import com.slayersimplified.services.MonsterNotesService;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * Tab that allows players to write and save notes for a specific monster.
 * Notes are auto-saved when the text changes and persisted via ConfigManager.
 *
 * When location-specific suggested items are available (e.g. a light source
 * for dark caves, climbing boots for mountain paths), they are shown in a
 * read-only "Suggested Items" section above the player's note text area.
 */
@Slf4j
public class NotesTab extends JPanel implements Tab<NotesTab.NotesData>
{
    /**
     * Data passed to {@link #update} on each task selection.
     *
     * <p>Items in {@code requiredItems} and {@code taskSuggestedItems} may optionally end with
     * a variant tag of the form {@code --VariantName} (e.g. {@code "Darklight --Shadow hound"}).
     * Untagged items apply to all variants; tagged items are labelled accordingly in the display.
     */
    public static class NotesData
    {
        public final String monsterName;
        /** Raw task.itemsRequired — may contain trailing {@code --VariantName} tags. */
        public final String[] requiredItems;
        /** Raw task.itemsSuggested — may contain trailing {@code --VariantName} tags. */
        public final String[] taskSuggestedItems;
        /** Suggested items derived from location requirements (no variant tags). */
        public final List<String> locationSuggestedItems;

        public NotesData(String monsterName,
                         String[] requiredItems,
                         String[] taskSuggestedItems,
                         List<String> locationSuggestedItems)
        {
            this.monsterName = monsterName;
            this.requiredItems = requiredItems != null ? requiredItems : new String[0];
            this.taskSuggestedItems = taskSuggestedItems != null ? taskSuggestedItems : new String[0];
            this.locationSuggestedItems = locationSuggestedItems != null
                    ? locationSuggestedItems : Collections.emptyList();
        }
    }

    // ── Tag parsing ───────────────────────────────────────────────────────────

    /** Separator between an item name and its optional variant tag: {@code " --"}. */
    private static final String TAG_SEPARATOR = " --";

    /**
     * Returns the variant name embedded in {@code raw} as a trailing {@code --VariantName} tag
     * (e.g. {@code "Darklight --Shadow hound"} → {@code "Shadow hound"}), or {@code null} when
     * there is no tag (item applies to all variants).
     */
    private static String parseTag(String raw)
    {
        if (raw == null) return null;
        int idx = raw.lastIndexOf(TAG_SEPARATOR);
        if (idx < 0) return null;
        String tag = raw.substring(idx + TAG_SEPARATOR.length()).trim();
        return tag.isEmpty() ? null : tag;
    }

    /**
     * Returns the display text for an item, stripping any trailing {@code --VariantName} suffix.
     */
    private static String stripTag(String raw)
    {
        if (raw == null) return null;
        int idx = raw.lastIndexOf(TAG_SEPARATOR);
        return idx >= 0 ? raw.substring(0, idx).trim() : raw;
    }

    private final MonsterNotesService notesService;
    private final Runnable onNotesChanged;
    private final JTextArea textArea;
    /** Dynamic panel rebuilt on each update to reflect the current task's suggested items. */
    private final JPanel northWrapper;
    private String currentMonster;
    private boolean suppressSave = false;

    public NotesTab(MonsterNotesService notesService, Runnable onNotesChanged)
    {
        this.notesService = notesService;
        this.onNotesChanged = onNotesChanged;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);

        northWrapper = new JPanel();
        northWrapper.setLayout(new BoxLayout(northWrapper, BoxLayout.Y_AXIS));
        northWrapper.setBackground(ColorScheme.DARKER_GRAY_COLOR);

        // "Player Notes" sub-header (always visible)
        JPanel playerNotesHeader = new JPanel(new BorderLayout());
        playerNotesHeader.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        playerNotesHeader.setBorder(new EmptyBorder(5, 8, 5, 8));
        JLabel playerNotesLabel = new JLabel("Player Notes");
        playerNotesLabel.setFont(FontManager.getRunescapeBoldFont());
        playerNotesLabel.setForeground(ColorScheme.BRAND_ORANGE);
        playerNotesHeader.add(playerNotesLabel, BorderLayout.WEST);
        northWrapper.add(playerNotesHeader);

        add(northWrapper, BorderLayout.NORTH);

        // Text area for notes
        textArea = new JTextArea();
        textArea.setBackground(ColorScheme.DARKER_GRAY_COLOR.brighter());
        textArea.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        textArea.setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        textArea.setFont(FontManager.getRunescapeSmallFont());
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Auto-save on text change
        textArea.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e) { saveNotes(); }

            @Override
            public void removeUpdate(DocumentEvent e) { saveNotes(); }

            @Override
            public void changedUpdate(DocumentEvent e) { saveNotes(); }
        });

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(new EmptyBorder(0, 4, 4, 4));
        scrollPane.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        ScrollBarStyling.apply(scrollPane);
        // Prevent scroll events from propagating to the game canvas (would cause camera zoom).
        scrollPane.addMouseWheelListener(e -> e.consume());
        add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public void update(NotesData data)
    {
        if (data == null || data.monsterName == null || data.monsterName.isEmpty())
        {
            return;
        }

        // Rebuild the north wrapper, keeping only the "Player Notes" header at the end.
        while (northWrapper.getComponentCount() > 1)
        {
            northWrapper.remove(0);
        }

        int insertIdx = 0;

        // ── Required Items ────────────────────────────────────────────────
        if (data.requiredItems.length > 0)
        {
            JPanel section = buildItemSection("Required Items", data.requiredItems);
            if (section != null)
            {
                northWrapper.add(section, insertIdx++);
            }
        }

        // ── Suggested Items (task-level + location-level) ─────────────────
        // Merge: location-derived items have no tag and go last.
        boolean hasSuggested = data.taskSuggestedItems.length > 0
                || !data.locationSuggestedItems.isEmpty();
        if (hasSuggested)
        {
            JPanel section = new JPanel();
            section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
            section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            section.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                    new EmptyBorder(5, 8, 5, 8)));
            section.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel header = new JLabel("Suggested Items");
            header.setFont(FontManager.getRunescapeBoldFont());
            header.setForeground(ColorScheme.BRAND_ORANGE);
            header.setAlignmentX(Component.LEFT_ALIGNMENT);
            section.add(header);
            section.add(Box.createVerticalStrut(3));

            // Task-level suggested items (may have --VariantName tags)
            for (String raw : data.taskSuggestedItems)
            {
                section.add(buildItemLabel(raw));
            }
            // Location-derived suggested items (no tags)
            for (String item : data.locationSuggestedItems)
            {
                section.add(buildUntaggedItemLabel(item));
            }

            northWrapper.add(section, insertIdx);
        }

        northWrapper.revalidate();
        northWrapper.repaint();

        currentMonster = data.monsterName;
        suppressSave = true;
        String notes = notesService.getNotes(data.monsterName);
        textArea.setText(notes);
        textArea.setCaretPosition(0);
        suppressSave = false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a section panel for an items array (required or suggested).
     * Returns {@code null} if all items would produce empty labels.
     */
    private JPanel buildItemSection(String title, String[] rawItems)
    {
        if (rawItems == null || rawItems.length == 0)
        {
            return null;
        }

        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
                new EmptyBorder(5, 8, 5, 8)));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel header = new JLabel(title);
        header.setFont(FontManager.getRunescapeBoldFont());
        header.setForeground(ColorScheme.BRAND_ORANGE);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(header);
        section.add(Box.createVerticalStrut(3));

        for (String raw : rawItems)
        {
            section.add(buildItemLabel(raw));
        }
        return section;
    }

    /**
     * Builds a label for a potentially-tagged item.
     * Tagged items ({@code --VariantName text}) are rendered with a
     * coloured variant label: {@code • text  [VariantName]}.
     * Untagged items are rendered as {@code • text}.
     */
    private JLabel buildItemLabel(String raw)
    {
        String tag = parseTag(raw);
        String text = stripTag(raw);

        JLabel label;
        if (tag != null)
        {
            label = new JLabel("<html>\u2022 " + text
                    + " <font color='#8888cc'><i>(" + tag + ")</i></font></html>");
        }
        else
        {
            label = new JLabel("\u2022 " + text);
        }
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Builds a plain bullet label with no tag processing. */
    private JLabel buildUntaggedItemLabel(String text)
    {
        JLabel label = new JLabel("\u2022 " + text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    @Override
    public void shutDown()
    {
        saveNotes();
        currentMonster = null;
        suppressSave = true;
        textArea.setText("");
        suppressSave = false;
    }

    private void saveNotes()
    {
        if (suppressSave || currentMonster == null)
        {
            return;
        }
        notesService.setNotes(currentMonster, textArea.getText());
        if (onNotesChanged != null)
        {
            onNotesChanged.run();
        }
    }
}
