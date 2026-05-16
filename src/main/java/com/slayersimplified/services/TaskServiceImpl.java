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
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Loads all Slayer tasks from the bundled tasks.json and provides
 * lookup/search capabilities. Task images and wiki links are
 * generated at load time from the resource data.
 */
@Slf4j
@Singleton
public class TaskServiceImpl implements TaskService
{
    private final Map<String, Task> tasks = new HashMap<>();
    private final String baseWikiUrl;
    private final String baseImagesPath;

    @Inject
    public TaskServiceImpl(
            Gson gson,
            @Named("dataPath") String dataPath,
            @Named("baseWikiUrl") String baseWikiUrl,
            @Named("baseImagesPath") String baseImagesPath)
    {
        this.baseWikiUrl = baseWikiUrl;
        this.baseImagesPath = baseImagesPath;

        InputStream inputStream = this.getClass().getResourceAsStream(dataPath);

        if (inputStream == null)
        {
            throw new RuntimeException("Could not find JSON at path " + dataPath);
        }

        try (Reader reader = new InputStreamReader(inputStream))
        {
            Type type = new TypeToken<Map<String, Task>>() {}.getType();
            Map<String, Task> data = gson.fromJson(reader, type);

            data.forEach((key, value) ->
            {
                value.wikiLinks = createWikiLinks(value);
                value.image = getImage(value.name);
                tasks.put(key.toLowerCase(), value);
            });
        }
        catch (JsonSyntaxException e)
        {
            log.error("JSON syntax error in file {}", dataPath, e);
        }
        catch (IOException e)
        {
            log.error("Could not read JSON from file {}", dataPath, e);
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

        links.add(createWikiLink(task.name));

        for (String variant : task.variants)
        {
            links.add(createWikiLink(variant));
        }

        return links.toArray(new WikiLink[0]);
    }

    private WikiLink createWikiLink(String name)
    {
        String url = baseWikiUrl + name.replace(' ', '_');

        return new WikiLink(name, url);
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
                return null;
            }
            return ImageUtil.resizeImage(image, image.getWidth() / 2, image.getHeight() / 2);
        }
        catch (Exception e)
        {
            log.debug("No image resource for task '{}' at {}", name, path);
            return null;
        }
    }
}
