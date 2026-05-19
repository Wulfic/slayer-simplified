/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.modules;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.name.Named;
import com.slayersimplified.services.TaskService;
import com.slayersimplified.services.TaskServiceImpl;

/**
 * Guice module that configures bindings for the task data service
 * and provides named constants (resource paths, URLs) used at injection time.
 */
public class TaskServiceModule extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(TaskService.class).to(TaskServiceImpl.class);
    }

    /**
     * Classpath path to the directory holding the per-task JSON files
     * and the {@code _index.json} manifest that lists them.
     */
    @Provides
    @Named("dataPath")
    String provideJsonDataPath()
    {
        return "/data/tasks";
    }

    @Provides
    @Named("baseWikiUrl")
    String provideBaseWikiUrl()
    {
        return "https://oldschool.runescape.wiki/w/";
    }

    @Provides
    @Named("baseImagesPath")
    String provideBaseImagesPath()
    {
        return "/images/monsters/";
    }

    /** Path to the location coordinates JSON for the NavigationService. */
    @Provides
    @Named("locationDataPath")
    String provideLocationDataPath()
    {
        return "/data/location_coordinates.json";
    }
}
