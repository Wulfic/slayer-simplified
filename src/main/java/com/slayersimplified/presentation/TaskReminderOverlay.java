/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * On-canvas overlay that shows the current task's required items and
 * personal notes in a panel box — similar to the Location Debug overlay.
 * Automatically hides when there is nothing to display.
 */
package com.slayersimplified.presentation;

import com.slayersimplified.domain.Task;
import com.slayersimplified.services.MonsterNotesService;
import com.slayersimplified.services.SlayerTaskTracker;
import com.slayersimplified.services.TaskService;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class TaskReminderOverlay extends Overlay
{
    private static final int WRAP_CHARS = 30;

    private final Client client;
    private final SlayerTaskTracker taskTracker;
    private final TaskService taskService;
    private final MonsterNotesService notesService;
    private final PanelComponent panelComponent = new PanelComponent();

    // --- Cached panel state. The PanelComponent is only rebuilt when one of
    // these inputs changes, so the per-frame render() is essentially free.
    private String cachedTaskName;
    private String cachedNotes;
    private boolean cachedHasContent;

    @Inject
    public TaskReminderOverlay(
            Client client,
            SlayerTaskTracker taskTracker,
            TaskService taskService,
            MonsterNotesService notesService)
    {
        this.client = client;
        this.taskTracker = taskTracker;
        this.taskService = taskService;
        this.notesService = notesService;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return null;
        }

        String taskName = taskTracker.getCurrentTaskName();
        if (taskName == null || taskName.isEmpty())
        {
            return null;
        }

        // Look up notes (cheap map get) so we can detect changes since the last frame.
        Task task = taskService.get(taskName);
        if (task == null)
        {
            Task[] matches = taskService.searchPartialName(taskName);
            if (matches.length > 0)
            {
                task = matches[0];
            }
        }
        if (task == null)
        {
            return null;
        }

        String notes = notesService.getNotes(task.name);

        // Rebuild the PanelComponent only when one of the inputs changes.
        if (!task.name.equals(cachedTaskName) || !notes.equals(cachedNotes))
        {
            cachedTaskName = task.name;
            cachedNotes = notes;
            cachedHasContent = rebuildPanel(task, notes);
        }

        if (!cachedHasContent)
        {
            return null;
        }

        return panelComponent.render(graphics);
    }

    /**
     * Rebuilds the cached PanelComponent for the given task / notes.
     *
     * @return true if the panel has any content worth rendering.
     */
    private boolean rebuildPanel(Task task, String notes)
    {
        final boolean hasItems = task.itemsRequired != null
                && Arrays.stream(task.itemsRequired)
                         .anyMatch(s -> s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("none"));
        final boolean hasNotes = !notes.isEmpty();

        panelComponent.getChildren().clear();

        if (!hasItems && !hasNotes)
        {
            return false;
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(task.name)
                .build());

        if (hasItems)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Required Items")
                    .leftColor(Color.ORANGE)
                    .build());
            for (String item : task.itemsRequired)
            {
                if (item != null && !item.trim().isEmpty() && !item.equalsIgnoreCase("none"))
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("\u2022 " + item)
                            .build());
                }
            }
        }

        if (hasItems && hasNotes)
        {
            panelComponent.getChildren().add(LineComponent.builder().left("").build());
        }

        if (hasNotes)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Your Notes")
                    .leftColor(Color.ORANGE)
                    .build());
            for (String line : wrapText(notes, WRAP_CHARS))
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(line)
                        .build());
            }
        }

        return true;
    }

    /** Splits text into lines no longer than {@code maxLen} characters, preserving newlines. */
    private static List<String> wrapText(String text, int maxLen)
    {
        List<String> result = new ArrayList<>();
        for (String para : text.split("\n", -1))
        {
            if (para.isEmpty())
            {
                continue;
            }
            while (para.length() > maxLen)
            {
                int wrapAt = para.lastIndexOf(' ', maxLen);
                if (wrapAt <= 0)
                {
                    wrapAt = maxLen;
                }
                result.add(para.substring(0, wrapAt));
                para = para.substring(wrapAt).stripLeading();
            }
            if (!para.isEmpty())
            {
                result.add(para);
            }
        }
        return result;
    }
}
