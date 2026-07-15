/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import com.slayersimplified.domain.Task;
import com.slayersimplified.domain.TaskSearchResult;
import com.slayersimplified.domain.WikiLink;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.util.LinkedHashSet;
import javax.inject.Named;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads all Slayer tasks from the bundled per-task JSON files under
 * {@code /data/tasks/} (enumerated by {@code _index.json}) and provides
 * lookup/search capabilities. Task images and wiki links are generated
 * at load time from the resource data.
 */
@Slf4j
@Singleton
public class TaskServiceImpl implements TaskService
{
    /** Name of the manifest file inside the tasks directory. */
    private static final String INDEX_FILE = "_index.json";

    /**
     * Placeholder image returned for tasks that have no monster image on disk.
     * Generated once at class-load time; shared (read-only) across all tasks.
     */
    private static final BufferedImage PLACEHOLDER_IMAGE;
    static
    {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 40, 40));
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(100, 100, 100));
        g.drawRect(1, 1, size - 3, size - 3);
        g.setFont(new Font("Dialog", Font.BOLD, 30));
        FontMetrics fm = g.getFontMetrics();
        String text = "?";
        g.drawString(text,
                (size - fm.stringWidth(text)) / 2,
                fm.getAscent() + (size - fm.getHeight()) / 2);
        g.dispose();
        PLACEHOLDER_IMAGE = img;
    }

    private final Map<String, Task> tasks = new HashMap<>();
    private final String baseWikiUrl;
    private final String baseImagesPath;

    /** Shape of a single entry in {@code _index.json}. */
    private static final class IndexEntry
    {
        String key;
        String file;
    }

    @Inject
    public TaskServiceImpl(
            Gson gson,
            @Named("dataPath") String dataPath,
            @Named("nonSlayerDataPath") String nonSlayerDataPath,
            @Named("bossDataPath") String bossDataPath,
            @Named("animalDataPath") String animalDataPath,
            @Named("baseWikiUrl") String baseWikiUrl,
            @Named("baseImagesPath") String baseImagesPath)
    {
        this.baseWikiUrl = baseWikiUrl;
        this.baseImagesPath = baseImagesPath;
        loadDirectory(gson, dataPath);
        loadDirectory(gson, nonSlayerDataPath);
        loadDirectory(gson, bossDataPath);
        loadDirectory(gson, animalDataPath);
    }

    private void loadDirectory(Gson gson, String dataPath)
    {
        String indexPath = dataPath + "/" + INDEX_FILE;
        InputStream indexStream = this.getClass().getResourceAsStream(indexPath);

        if (indexStream == null)
        {
            throw new RuntimeException("Could not find task index at " + indexPath);
        }

        List<IndexEntry> entries;
        try (Reader reader = new InputStreamReader(indexStream, StandardCharsets.UTF_8))
        {
            Type type = new TypeToken<List<IndexEntry>>() {}.getType();
            entries = gson.fromJson(reader, type);
        }
        catch (JsonSyntaxException e)
        {
            log.error("JSON syntax error in task index {}", indexPath, e);
            return;
        }
        catch (IOException e)
        {
            log.error("Could not read task index {}", indexPath, e);
            return;
        }

        if (entries == null)
        {
            log.error("Task index at {} was empty", indexPath);
            return;
        }

        for (IndexEntry entry : entries)
        {
            loadTask(gson, dataPath, entry);
        }
    }

    private void loadTask(Gson gson, String dataPath, IndexEntry entry)
    {
        String taskPath = dataPath + "/" + entry.file;
        InputStream taskStream = this.getClass().getResourceAsStream(taskPath);
        if (taskStream == null)
        {
            log.error("Could not find task JSON for key '{}' at {}", entry.key, taskPath);
            return;
        }

        try (Reader reader = new InputStreamReader(taskStream, StandardCharsets.UTF_8))
        {
            Task value = gson.fromJson(reader, Task.class);
            if (value == null)
            {
                log.error("Task JSON {} parsed to null", taskPath);
                return;
            }
            value.wikiLinks = createWikiLinks(value);
            value.image = getImage(value.name);
            tasks.put(entry.key.toLowerCase(), value);
        }
        catch (JsonSyntaxException e)
        {
            log.error("JSON syntax error in {}", taskPath, e);
        }
        catch (IOException e)
        {
            log.error("Could not read {}", taskPath, e);
        }
    }

    @Override
    public Task get(String name)
    {
        return tasks.get(name.toLowerCase());
    }

    @Override
    public Task[] getAll()
    {
        return getAll(null);
    }

    @Override
    public Task[] getAll(Comparator<Task> comparator)
    {
        if (comparator == null)
        {
            return tasks.values().toArray(new Task[0]);
        }

        return tasks.values().stream()
                .sorted(comparator)
                .toArray(Task[]::new);
    }

    @Override
    public Task[] searchPartialName(String text)
    {
        if (text == null || text.isEmpty())
        {
            return new Task[0];
        }

        String searchTerm = text.toLowerCase();
        return tasks
                .values()
                .stream()
                .filter(m -> m.name.toLowerCase().contains(searchTerm))
                .toArray(Task[]::new);
    }

    @Override
    public TaskSearchResult[] searchWithVariants(String text)
    {
        if (text == null || text.isEmpty())
        {
            return new TaskSearchResult[0];
        }

        String term = text.toLowerCase();
        List<TaskSearchResult> results = new ArrayList<>();

        for (Task task : tasks.values())
        {
            boolean hasVariants = task.variants != null && task.variants.length > 0;

            // The task name is a "real" monster entry only when there are no
            // variants at all, or when at least one variant's stripped display
            // name equals the task name (e.g. "Guard dog" in its own variant list).
            // If every variant has a different name the task name is just a
            // grouping label (e.g. "Sea snake") and should not appear as a
            // standalone search result.
            boolean taskNameIsRealMonster = !hasVariants;
            if (hasVariants)
            {
                for (String v : task.variants)
                {
                    int fi = v.indexOf("--lvl ");
                    String dv = fi >= 0 ? v.substring(0, fi).trim() : v;
                    if (dv.equalsIgnoreCase(task.name))
                    {
                        taskNameIsRealMonster = true;
                        break;
                    }
                }
            }

            boolean nameMatched = task.name.toLowerCase().contains(term);
            if (nameMatched && taskNameIsRealMonster)
            {
                results.add(new TaskSearchResult(task, task.name));
            }

            if (hasVariants)
            {
                for (String variant : task.variants)
                {
                    // Strip the --lvl flag for the display name.
                    int flagIdx = variant.indexOf("--lvl ");
                    String displayVariant = flagIdx >= 0 ? variant.substring(0, flagIdx).trim() : variant;

                    // Skip if the stripped display name equals the task name —
                    // that entry is handled by the task-name result above.
                    if (displayVariant.equalsIgnoreCase(task.name))
                    {
                        continue;
                    }

                    // Match against the full variant string (including any --lvl flag).
                    if (variant.toLowerCase().contains(term))
                    {
                        results.add(new TaskSearchResult(task, displayVariant));
                    }
                }
            }
        }

        // Sort: parent task name first, then display name so variants cluster
        // below their parent in the results list.
        results.sort(Comparator
                .comparing((TaskSearchResult r) -> r.parentTask.name)
                .thenComparing(r -> r.displayName));

        return results.toArray(new TaskSearchResult[0]);
    }

    @Override
    public TaskSearchResult[] searchByLocation(String location)
    {
        if (location == null || location.isBlank())
        {
            return new TaskSearchResult[0];
        }
        String term = location.trim().toLowerCase();
        List<TaskSearchResult> results = new ArrayList<>();

        for (Task task : tasks.values())
        {
            if (task.variantLocations == null || task.variantLocations.isEmpty())
            {
                continue;
            }

            // Collect unique display names whose variant has the matching location.
            Set<String> addedDisplayNames = new LinkedHashSet<>();
            for (Map.Entry<String, String[]> entry : task.variantLocations.entrySet())
            {
                String[] locs = entry.getValue();
                if (locs == null) continue;
                boolean matches = false;
                for (String loc : locs)
                {
                    if (loc.toLowerCase().contains(term))
                    {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;

                String variantKey = entry.getKey();
                int flagIdx = variantKey.indexOf("--lvl ");
                String displayName = flagIdx >= 0 ? variantKey.substring(0, flagIdx).trim() : variantKey;
                addedDisplayNames.add(displayName);
            }

            for (String dn : addedDisplayNames)
            {
                results.add(new TaskSearchResult(task, dn));
            }
        }

        results.sort(Comparator
                .comparing((TaskSearchResult r) -> r.parentTask.name)
                .thenComparing(r -> r.displayName));
        return results.toArray(new TaskSearchResult[0]);
    }

    private WikiLink[] createWikiLinks(Task task)
    {
        List<WikiLink> links = new ArrayList<>();

        if (task.variants != null && task.variants.length > 0)
        {
            // Variants own the full list; strip any --lvl flag for display/URL.
            for (String variant : task.variants)
            {
                links.add(createWikiLink(variant));
            }
        }
        else
        {
            links.add(createWikiLink(task.name));
        }

        return links.toArray(new WikiLink[0]);
    }

    private WikiLink createWikiLink(String name)
    {
        // Strip --lvl flag if present (e.g. "Aberrant spectre --lvl 96" → "Aberrant spectre")
        int flagIdx = name.indexOf("--lvl ");
        String displayName = flagIdx >= 0 ? name.substring(0, flagIdx).trim() : name;
        String url = baseWikiUrl + displayName.replace(' ', '_');
        return new WikiLink(displayName, url);
    }

    private BufferedImage getImage(String name)
    {
        String normalizedName = name.replace(' ', '_').toLowerCase();
        String path = baseImagesPath + normalizedName + ".png";

        // Some tasks (e.g. newer content or level variants) have no bundled
        // monster image. Check for the resource first so ImageUtil does not log
        // a warning for the expected miss; fall back to the shared placeholder.
        if (getClass().getResource(path) == null)
        {
            return PLACEHOLDER_IMAGE;
        }

        try
        {
            BufferedImage image = ImageUtil.loadImageResource(getClass(), path);
            if (image == null)
            {
                return PLACEHOLDER_IMAGE;
            }
            // Cap at 120px to match the pre-resized source images.
            int w = image.getWidth();
            int h = image.getHeight();
            final int MAX_DIM = 120;
            if (w > MAX_DIM || h > MAX_DIM)
            {
                double scale = Math.min((double) MAX_DIM / w, (double) MAX_DIM / h);
                w = Math.max(1, (int) (w * scale));
                h = Math.max(1, (int) (h * scale));
            }
            return ImageUtil.resizeImage(image, w, h);
        }
        catch (Exception e)
        {
            log.debug("No image resource for task '{}' at {}", name, path);
            return PLACEHOLDER_IMAGE;
        }
    }
}
