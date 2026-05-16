/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * MODIFIED from original: TaskTabs constructor now requires NavigationService
 * and LocationCoordinateService for the LocationsTab integration.
 */
package com.slayersimplified.presentation.panels;

import com.slayersimplified.domain.Task;
import com.slayersimplified.presentation.components.Header;
import com.slayersimplified.presentation.components.TaskTabs;
import com.slayersimplified.services.FavoriteLocationService;
import com.slayersimplified.services.LocationCoordinateService;
import com.slayersimplified.services.MonsterNotesService;
import com.slayersimplified.services.NavigationService;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import okhttp3.OkHttpClient;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Panel showing the selected task's details with a header (name + image),
 * tabbed info sections, and a close button.
 */
public class TaskSelectedPanel extends JPanel
{
    private final Header header = new Header();
    private final TaskTabs taskTabs;
    private final JButton closeButton = new JButton("Close");
    private final JButton backButton = new JButton("\u2190");

    private final ActionListener onClickListener;

    /**
     * @param onClose                   callback to invoke when the close button is clicked
     * @param navigationService         navigation service for path requests
     * @param locationCoordinateService coordinate lookup service
     * @param favoriteService           favorite location persistence service
     * @param okHttpClient              HTTP client for wiki loot lookups
     * @param notesService              monster notes persistence service
     */
    public TaskSelectedPanel(
            Runnable onClose,
            NavigationService navigationService,
            LocationCoordinateService locationCoordinateService,
            FavoriteLocationService favoriteService,
            OkHttpClient okHttpClient,
            MonsterNotesService notesService)
    {
        // Pass all services down to TaskTabs → tabs
        this.taskTabs = new TaskTabs(navigationService, locationCoordinateService, favoriteService,
                okHttpClient, notesService);
        this.onClickListener = e -> onClose.run();
        closeButton.addActionListener(this.onClickListener);
        closeButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        closeButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        closeButton.setFont(FontManager.getRunescapeSmallFont());
        closeButton.setFocusPainted(false);

        backButton.addActionListener(this.onClickListener);
        backButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        backButton.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        backButton.setFont(FontManager.getRunescapeSmallFont());
        backButton.setFocusPainted(false);
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel backRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        backRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        backRow.add(backButton);

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        northPanel.add(backRow, BorderLayout.NORTH);
        northPanel.add(header, BorderLayout.CENTER);

        setLayout(new BorderLayout(0, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(new EmptyBorder(5, 0, 0, 0));

        add(northPanel, BorderLayout.NORTH);
        add(taskTabs, BorderLayout.CENTER);
        add(closeButton, BorderLayout.SOUTH);
    }

    public void shutDown()
    {
        taskTabs.shutDown();
        closeButton.removeActionListener(onClickListener);
        backButton.removeActionListener(onClickListener);
    }

    public void update(Task task)
    {
        header.update(task.name, new ImageIcon(task.image));
        SwingUtilities.invokeLater(() -> taskTabs.update(task));
    }

    /**
     * Programmatically switch to the Locations tab (used by Quick Navigate).
     */
    public void selectLocationsTab()
    {
        taskTabs.selectLocationsTab();
    }
}
