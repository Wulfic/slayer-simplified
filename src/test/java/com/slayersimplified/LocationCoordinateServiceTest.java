/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.slayersimplified.services.LocationCoordinateService;
import net.runelite.api.coords.WorldPoint;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for LocationCoordinateService — verifies that location_coordinates.json
 * is parsed correctly and specific locations can be looked up.
 */
public class LocationCoordinateServiceTest
{
    private final LocationCoordinateService service =
            new LocationCoordinateService(new Gson(), "/data/location_coordinates.json");

    @Test
    public void testZanarisHasCoordinates()
    {
        WorldPoint coords = service.getCoordinates("Zanaris");
        Assert.assertNotNull("Zanaris should have coordinates mapped", coords);
    }

    @Test
    public void testSlayerTowerHasCoordinates()
    {
        WorldPoint coords = service.getCoordinates("Slayer Tower");
        Assert.assertNotNull("Slayer Tower should have coordinates mapped", coords);
    }

    @Test
    public void testLumbridgeCowFieldHasCoordinates()
    {
        WorldPoint coords = service.getCoordinates("Lumbridge Cow Field");
        Assert.assertNotNull("Lumbridge Cow Field should have coordinates mapped", coords);
    }

    @Test
    public void testCaseSensitivity()
    {
        WorldPoint lower = service.getCoordinates("zanaris");
        WorldPoint mixed = service.getCoordinates("Zanaris");
        WorldPoint upper = service.getCoordinates("ZANARIS");
        Assert.assertNotNull("Lowercase lookup should work", lower);
        Assert.assertNotNull("Mixed-case lookup should work", mixed);
        Assert.assertNotNull("Uppercase lookup should work", upper);
    }

    @Test
    public void testAllLocationCountIsReasonable()
    {
        // Should have at least 150 locations loaded (canonical + aliases included)
        Assert.assertTrue(
                "Should have loaded at least 150 locations, got: " + service.getAll().size(),
                service.getAll().size() >= 150
        );
    }

    @Test
    public void testAliasResolvesToSameCoordinates()
    {
        // "Mourner Tunnels" is canonical; the older, longer name is an alias for it.
        WorldPoint canonical = service.getCoordinates("Mourner Tunnels");
        WorldPoint alias = service.getCoordinates("Path to Temple of Light (Mourner tunnels)");
        Assert.assertNotNull("Canonical name should resolve", canonical);
        Assert.assertNotNull("Alias should resolve to same WorldPoint", alias);
        Assert.assertEquals("Alias should give identical coordinates to canonical entry", canonical, alias);
    }

    @Test
    public void testResolveCanonical()
    {
        // "Stronhold of Security" (typo) is an alias for "stronghold of security"
        String canonical = service.resolveCanonical("Stronhold of Security");
        Assert.assertEquals("stronghold of security", canonical);

        // A canonical name should return itself lower-cased
        String self = service.resolveCanonical("Zanaris");
        Assert.assertEquals("zanaris", self);
    }
}
