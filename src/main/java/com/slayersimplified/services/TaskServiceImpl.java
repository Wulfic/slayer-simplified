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
import com.slayersimplified.domain.WikiLink;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
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
            @Named("baseWikiUrl") String baseWikiUrl,
            @Named("baseImagesPath") String baseImagesPath)
    {
        this.baseWikiUrl = baseWikiUrl;
        this.baseImagesPath = baseImagesPath;
        loadDirectory(gson, dataPath);
        loadDirectory(gson, nonSlayerDataPath);
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

        try
        {
            BufferedImage image = ImageUtil.loadImageResource(getClass(), path);
            if (image == null)
            {
                return PLACEHOLDER_IMAGE;
            }
            // First halve the source resolution, then cap at 160px to prevent
            // large downloaded images from overflowing the panel and hiding the tabs.
            int w = image.getWidth() / 2;
            int h = image.getHeight() / 2;
            final int MAX_DIM = 160;
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
