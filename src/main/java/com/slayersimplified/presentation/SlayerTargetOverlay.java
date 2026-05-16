/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.presentation;

import com.slayersimplified.SlayerSimplifiedConfig;
import com.slayersimplified.domain.Task;
import com.slayersimplified.services.SlayerTaskTracker;
import com.slayersimplified.services.TaskService;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class SlayerTargetOverlay extends Overlay
{
    private final Client client;
    private final SlayerSimplifiedConfig config;
    private final SlayerTaskTracker taskTracker;
    private final TaskService taskService;
    private final ModelOutlineRenderer modelOutlineRenderer;

    /** Names of NPCs that match the current task (lower-cased). */
    private final Set<String> targetNames = new HashSet<>();

    /**
     * The live set of NPCs to highlight. Populated via onTaskChanged() and
     * kept up-to-date by onNpcSpawned() / onNpcDespawned() so we never
     * scan the entire NPC list inside render().
     */
    private final Set<NPC> trackedNpcs = new HashSet<>();

    @Inject
    public SlayerTargetOverlay(
            Client client,
            SlayerSimplifiedConfig config,
            SlayerTaskTracker taskTracker,
            TaskService taskService,
            ModelOutlineRenderer modelOutlineRenderer)
    {
        this.client = client;
        this.config = config;
        this.taskTracker = taskTracker;
        this.taskService = taskService;
        this.modelOutlineRenderer = modelOutlineRenderer;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(PRIORITY_MED);
    }

    /**
     * Called by the plugin whenever the active slayer task may have changed
     * (task assigned, completed, or on plugin start). Rebuilds targetNames and
     * re-populates trackedNpcs from the current scene in one pass.
     * Must be called on the client thread.
     */
    public void onTaskChanged()
    {
        String currentTask = taskTracker.getCurrentTaskName();
        targetNames.clear();
        trackedNpcs.clear();

        if (currentTask == null || currentTask.isEmpty())
        {
            return;
        }

        rebuildTargetNames(currentTask);

        // Scan existing NPCs once so we highlight any already in the scene.
        for (NPC npc : client.getTopLevelWorldView().npcs())
        {
            if (npc.getName() != null && targetNames.contains(npc.getName().toLowerCase()))
            {
                trackedNpcs.add(npc);
            }
        }
    }

    /**
     * Called by the plugin's NpcSpawned subscriber. Adds the NPC to the
     * tracked set if it matches the current task.
     */
    public void onNpcSpawned(NPC npc)
    {
        if (npc.getName() != null && targetNames.contains(npc.getName().toLowerCase()))
        {
            trackedNpcs.add(npc);
        }
    }

    /**
     * Called by the plugin's NpcDespawned subscriber. Removes the NPC from
     * the tracked set.
     */
    public void onNpcDespawned(NPC npc)
    {
        trackedNpcs.remove(npc);
    }

    /** Returns true if the given NPC is currently being tracked for this task. */
    public boolean isTracked(NPC npc)
    {
        return trackedNpcs.contains(npc);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightTarget() || trackedNpcs.isEmpty())
        {
            return null;
        }

        Color color = config.highlightColor();

        // Copy to avoid ConcurrentModificationException if the set is updated
        // between ticks while we iterate.
        for (NPC npc : trackedNpcs.toArray(new NPC[0]))
        {
            modelOutlineRenderer.drawOutline(npc, 2, color, 4);
        }

        return null;
    }

    private void rebuildTargetNames(String taskName)
    {
        targetNames.clear();

        // Add the task name itself
        targetNames.add(taskName.toLowerCase());

        // Look up the task to get variants
        Task task = taskService.get(taskName);
        if (task != null)
        {
            if (task.variants != null)
            {
                for (String variant : task.variants)
                {
                    targetNames.add(variant.toLowerCase());
                }
            }
        }
    }
}
