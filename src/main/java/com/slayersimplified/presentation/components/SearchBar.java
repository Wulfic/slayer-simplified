/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation.components;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.Consumer;
/**
 * Search bar component with a text field and search icon.
 * Fires the onChange callback on every keystroke for live filtering.
 */
public class SearchBar extends JPanel
{
    private final IconTextField searchBar = new IconTextField();
    private final DocumentListener onChangeListener;

    public SearchBar(Consumer<String> onChange)
    {
        onChangeListener = createDocumentListener(onChange);
        searchBar.getDocument().addDocumentListener(onChangeListener);

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        initialiseStyles();

        add(searchBar, BorderLayout.CENTER);
    }

    public void shutDown()
    {
        searchBar.getDocument().removeDocumentListener(onChangeListener);
    }

    private DocumentListener createDocumentListener(Consumer<String> handler)
    {
        return new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e) { handler.accept(searchBar.getText()); }
            @Override
            public void removeUpdate(DocumentEvent e) { handler.accept(searchBar.getText()); }
            @Override
            public void changedUpdate(DocumentEvent e) { handler.accept(searchBar.getText()); }
        };
    }

    private void initialiseStyles()
    {
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setFont(FontManager.getRunescapeSmallFont());
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARKER_GRAY_HOVER_COLOR);
        searchBar.setMinimumSize(new Dimension(0, 30));

        // Recursively fix text color and clear button styling after the
        // component tree is fully constructed on the EDT.
        SwingUtilities.invokeLater(() -> applyTheme(searchBar));
    }

    /**
     * Walks the full component tree of the given container and:
     *   - sets JTextField foreground to white so typed text is readable
     *   - removes the opaque white background from any JButton (clear / suggest buttons)
     */
    private static void applyTheme(Container container)
    {
        for (Component c : container.getComponents())
        {
            if (c instanceof JTextField)
            {
                ((JTextField) c).setForeground(Color.WHITE);
            }
            else if (c instanceof AbstractButton)
            {
                AbstractButton btn = (AbstractButton) c;
                btn.setContentAreaFilled(false);
                btn.setBorderPainted(false);
                btn.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }

            if (c instanceof Container)
            {
                applyTheme((Container) c);
            }
        }
    }
}
