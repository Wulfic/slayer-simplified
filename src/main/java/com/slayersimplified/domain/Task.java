/*
 * BSD 2-Clause License
 * Copyright (c) 2022, Lee (original Slayer Assistant plugin)
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

import java.awt.image.BufferedImage;

/**
 * Represents a Slayer task/monster with all associated metadata.
 * Deserialized from tasks.json; transient fields are populated at load time.
 */
public class Task
{
    public String name;
    public int levelRequired;
    public String[] itemsRequired;
    public String[] itemsSuggested;
    public String[] locations;
    public String[] attributes;
    public String[] attackStyles;
    public String[] variants;
    public String[] masters;

    /** Monster image — populated at load time, not serialized. */
    public transient BufferedImage image;

    /** Wiki links — generated at load time, not serialized. */
    public transient WikiLink[] wikiLinks;

    public Task(
            String name,
            int levelRequired,
            String[] itemsRequired,
            String[] itemsSuggested,
            String[] locations,
            String[] attributes,
            String[] attackStyles,
            String[] variants,
            String[] masters,
            BufferedImage image,
            WikiLink[] wikiLinks)
    {
        this.name = name;
        this.levelRequired = levelRequired;
        this.itemsRequired = itemsRequired;
        this.itemsSuggested = itemsSuggested;
        this.locations = locations;
        this.attributes = attributes;
        this.attackStyles = attackStyles;
        this.variants = variants;
        this.masters = masters;
        this.image = image;
        this.wikiLinks = wikiLinks;
    }
}
