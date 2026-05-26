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
/**
 * Tab that allows players to write and save notes for a specific monster.
 * Notes are auto-saved when the text changes and persisted via ConfigManager.
 */
@Slf4j
public class NotesTab extends JPanel implements Tab<NotesTab.NotesData>
{
    /** Data passed to {@link #update} on each task selection. */
    public static class NotesData
    {
        public final String monsterName;

        public NotesData(String monsterName)
        {
            this.monsterName = monsterName;
        }
    }

    private final MonsterNotesService notesService;
    private final Runnable onNotesChanged;
    private final JTextArea textArea;
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
