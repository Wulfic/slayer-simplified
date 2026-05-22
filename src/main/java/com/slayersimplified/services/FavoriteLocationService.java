/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 *
 * Persists favorite location per monster using RuneLite's ConfigManager.
 * Each monster maps to at most one favorite location name.
 */
package com.slayersimplified.services;

import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Stores and retrieves the user's favorite location for each Slayer monster.
 * Favorites are persisted via RuneLite's ConfigManager so they survive
 * client restarts. Each monster can have exactly one favorite location.
 */
@Singleton
public class FavoriteLocationService
{
    private static final String CONFIG_GROUP = "slayersimplified";
    private static final String FAV_PREFIX = "fav_";

    private final ConfigManager configManager;

    @Inject
    public FavoriteLocationService(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    /**
     * Get the favorite location name for a monster.
     *
     * @param monsterName the monster name (e.g. "Abyssal demon")
     * @return the favorite location name, or null if none set
     */
    public String getFavorite(String monsterName)
    {
        return configManager.getConfiguration(CONFIG_GROUP, FAV_PREFIX + normalize(monsterName));
    }

    /**
     * Set the favorite location for a monster (replaces any existing favorite).
     */
    public void setFavorite(String monsterName, String locationName)
    {
        configManager.setConfiguration(CONFIG_GROUP, FAV_PREFIX + normalize(monsterName), locationName);
    }

    /**
     * Clear the favorite location for a monster.
     */
    public void clearFavorite(String monsterName)
    {
        configManager.unsetConfiguration(CONFIG_GROUP, FAV_PREFIX + normalize(monsterName));
    }

    /**
     * Check if a specific location is the favorite for a monster.
     */
    public boolean isFavorite(String monsterName, String locationName)
    {
        String fav = getFavorite(monsterName);
        return locationName != null && locationName.equals(fav);
    }

    /**
     * Toggle favorite: if this location is already the favorite, clear it;
     * otherwise set it as the new favorite.
     *
     * @return true if the location is now the favorite, false if it was cleared
     */
    public boolean toggleFavorite(String monsterName, String locationName)
    {
        if (isFavorite(monsterName, locationName))
        {
            clearFavorite(monsterName);
            return false;
        }
        else
        {
            setFavorite(monsterName, locationName);
            return true;
        }
    }

    // ── Variant-aware favorites ───────────────────────────────────────────────

    /**
     * Get the favorite location for a specific variant of a monster.
     * Key format: fav_&lt;monster&gt;__&lt;variant&gt;
     */
    public String getFavoriteForVariant(String monsterName, String variantName)
    {
        return configManager.getConfiguration(CONFIG_GROUP, variantKey(monsterName, variantName));
    }

    /**
     * Set the favorite location for a specific variant.
     * Also updates the global monster favorite so quick-nav still works.
     */
    public void setFavoriteForVariant(String monsterName, String variantName, String locationName)
    {
        configManager.setConfiguration(CONFIG_GROUP, variantKey(monsterName, variantName), locationName);
        // Keep the global monster favorite in sync for quick-nav
        configManager.setConfiguration(CONFIG_GROUP, FAV_PREFIX + normalize(monsterName), locationName);
    }

    /**
     * Clear the favorite location for a specific variant.
     */
    public void clearFavoriteForVariant(String monsterName, String variantName)
    {
        configManager.unsetConfiguration(CONFIG_GROUP, variantKey(monsterName, variantName));
    }

    /**
     * Check if a specific location is the favorite for a given variant.
     */
    public boolean isFavoriteForVariant(String monsterName, String variantName, String locationName)
    {
        String fav = getFavoriteForVariant(monsterName, variantName);
        return locationName != null && locationName.equals(fav);
    }

    /**
     * Toggle the variant-level favorite.
     *
     * @return true if the location is now the favorite, false if it was cleared
     */
    public boolean toggleFavoriteForVariant(String monsterName, String variantName, String locationName)
    {
        if (isFavoriteForVariant(monsterName, variantName, locationName))
        {
            clearFavoriteForVariant(monsterName, variantName);
            return false;
        }
        else
        {
            setFavoriteForVariant(monsterName, variantName, locationName);
            return true;
        }
    }

    private String variantKey(String monsterName, String variantName)
    {
        return FAV_PREFIX + normalize(monsterName) + "__" + normalize(variantName);
    }

    private String normalize(String name)
    {
        return name.toLowerCase().replace(' ', '_').replace("'", "");
    }
}
