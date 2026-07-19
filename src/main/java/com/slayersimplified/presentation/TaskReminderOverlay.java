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

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.MonsterNotesService;
import com.slayersimplified.services.SlayerTaskTracker;
import com.slayersimplified.services.TaskEngagementService;
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
    /**
     * Maximum characters per wrapped line. At ~9 px/char for the RuneScape
     * small font (worst-case mixed-case), 16 chars ≈ 144 px — a comfortable
     * overlay width that stays well on-screen.
     */
    private static final int WRAP_CHARS = 16;

    private final Client client;
    private final SlayerTaskTracker taskTracker;
    private final TaskService taskService;
    private final MonsterNotesService notesService;
    private final TaskEngagementService engagement;
    private final SlayerSimplifiedConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    // --- Cached panel state. The PanelComponent is only rebuilt when one of
    // these inputs changes, so the per-frame render() is essentially free.
    private String  cachedTaskName;
    private String  cachedNotes;
    private int     cachedStreak           = -1;
    private boolean cachedHasContent;

    @Inject
    public TaskReminderOverlay(
            Client client,
            SlayerTaskTracker taskTracker,
            TaskService taskService,
            MonsterNotesService notesService,
            TaskEngagementService engagement,
            SlayerSimplifiedConfig config)
    {
        this.client = client;
        this.taskTracker = taskTracker;
        this.taskService = taskService;
        this.notesService = notesService;
        this.engagement = engagement;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showReminderOverlay() || !engagement.shouldShowOverlays())
        {
            return null;
        }

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
        int streak = taskTracker.getCurrentAssignmentNumber();

        // Rebuild the PanelComponent only when one of the inputs changes.
        if (!task.name.equals(cachedTaskName) || !notes.equals(cachedNotes)
                || streak != cachedStreak)
        {
            cachedTaskName = task.name;
            cachedNotes    = notes;
            cachedStreak   = streak;
            cachedHasContent = rebuildPanel(task, notes, streak);
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
    private boolean rebuildPanel(Task task, String notes, int streak)
    {
        final boolean hasRequired = task.itemsRequired != null
                && Arrays.stream(task.itemsRequired)
                         .anyMatch(s -> s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("none"));
        final boolean hasSuggested = task.itemsSuggested != null
                && Arrays.stream(task.itemsSuggested)
                         .anyMatch(s -> s != null && !s.trim().isEmpty() && !s.equalsIgnoreCase("none"));
        final boolean hasNotes = !notes.isEmpty();
        final boolean hasStreak = streak > 0;

        panelComponent.getChildren().clear();

        if (!hasRequired && !hasSuggested && !hasNotes && !hasStreak)
        {
            return false;
        }

        if (hasStreak)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Streak #" + streak)
                    .leftColor(new Color(100, 180, 255))
                    .build());
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(task.name)
                .build());

        if (hasRequired)
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
                            .left("\u2022 " + stripItemTag(item))
                            .build());
                }
            }
        }

        if (hasSuggested)
        {
            if (hasRequired)
            {
                panelComponent.getChildren().add(LineComponent.builder().left("").build());
            }
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Suggested Items")
                    .leftColor(Color.YELLOW)
                    .build());
            for (String item : task.itemsSuggested)
            {
                if (item != null && !item.trim().isEmpty() && !item.equalsIgnoreCase("none"))
                {
                    panelComponent.getChildren().add(LineComponent.builder()
                            .left("\u2022 " + stripItemTag(item))
                            .build());
                }
            }
        }

        if ((hasRequired || hasSuggested) && hasNotes)
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

    /**
     * Strips the trailing {@code --VariantName} tag from an item string, if present.
     * E.g. {@code "Darklight --Shadow hound"} → {@code "Darklight"}.
     */
    private static String stripItemTag(String item)
    {
        int idx = item.lastIndexOf(" --");
        return idx >= 0 ? item.substring(0, idx).trim() : item;
    }

    /**
     * Splits {@code text} into lines no longer than {@code maxLen} characters,
     * preserving explicit newlines and preferring word boundaries.
     */
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
