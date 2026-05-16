/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components.tabs;

import com.slayersimplified.domain.Tab;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.Arrays;

/**
 * Simple text-based tab that displays an array of strings as paragraphs.
 * Used for items required, masters, etc.
 */
public class TextTab extends JTextPane implements Tab<String[]>
{
    public TextTab()
    {
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        setFont(FontManager.getRunescapeSmallFont());
        setCaretColor(ColorScheme.LIGHT_GRAY_COLOR);
        setMargin(new Insets(10, 5, 10, 5));
        setEditable(false);
        setLineSpacing();
    }

    @Override
    public void update(String[] data)
    {
        resetParagraphs();

        if (data.length == 0)
        {
            addParagraph("None");
            return;
        }
        Arrays.stream(data).forEach(this::addParagraph);
    }

    @Override
    public void shutDown()
    {
        resetParagraphs();
    }

    private void addParagraph(String text)
    {
        StyledDocument doc = getStyledDocument();
        try
        {
            doc.insertString(doc.getLength(), capitalize(text) + "\n", null);
        }
        catch (BadLocationException e)
        {
            throw new RuntimeException(e);
        }
    }

    public void resetParagraphs()
    {
        setText("");
    }

    private static String capitalize(String s)
    {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void setLineSpacing()
    {
        selectAll();
        MutableAttributeSet set = new SimpleAttributeSet(getParagraphAttributes());
        StyleConstants.setLineSpacing(set, 0.5f);
        setParagraphAttributes(set, true);
    }
}
