/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slayersimplified.services.LocationCoordinateService;
import com.slayersimplified.services.LocationRequirementService;
import net.runelite.api.Quest;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Exercises {@link LocationRequirementService} against a <em>zero-progress
 * account</em> — no quests finished, no skill levels known.
 *
 * <p>That is not an artificial state: it is exactly what a fresh account (or any
 * account before {@code refresh()} has run) looks like, and it is the state in
 * which the Locations tab must show a location as gated rather than clickable.
 * {@code completedQuests} and {@code skillLevels} both start as empty
 * collections, so every recorded requirement is unmet and
 * {@link LocationRequirementService#getMissingText(String)} must name it.</p>
 *
 * <p>The {@link net.runelite.api.Client} argument is {@code null} on purpose.
 * Only {@code refresh()} touches the client, and no test here calls it — the
 * constructor and every read path used below are client-free. If someone later
 * adds a client call to one of them this test will NPE immediately, which is the
 * outcome we want: a loud failure rather than a silent behaviour change.</p>
 */
public class LocationRequirementServiceTest
{
    private final LocationCoordinateService locationService =
            new LocationCoordinateService(new Gson(), "/data/location_coordinates.json");

    private final LocationRequirementService service =
            new LocationRequirementService(null, locationService, new Gson());

    // -------------------------------------------------------------------------
    // Wyrmscraig / Vampyrium gating (Mortimer update, 29 July 2026)
    // -------------------------------------------------------------------------

    /**
     * Wyrmscraig and its cavern sit behind 62 Sailing. A player below that must
     * see the location as unavailable with the level spelled out — the whole
     * point of the requirement is that Mortimer's Wyrm tasks are unreachable
     * without it.
     */
    @Test
    public void wyrmscraigIsGatedOnSailing62()
    {
        for (String location : new String[]{"Wyrmscraig", "Wyrmscraig Cavern"})
        {
            Assert.assertFalse(
                    location + " must be gated for an account with no Sailing level",
                    service.isAvailable(location));

            Assert.assertEquals(
                    "Missing-requirement text for " + location,
                    "62 Sailing",
                    service.getMissingText(location));

            Assert.assertEquals(
                    "Requirement description for " + location,
                    "62 Sailing",
                    service.getRequirementDescription(location));
        }
    }

    /**
     * Both venator hunting grounds are behind The Blood Moon Rises. Venator is
     * one of Mortimer's 29 assignments, so an account without the quest must be
     * told why it cannot go there.
     */
    @Test
    public void venatorHuntingGroundsAreGatedOnTheBloodMoonRises()
    {
        String questName = Quest.THE_BLOOD_MOON_RISES.getName();

        for (String location : new String[]{"Apsul Hunting Ground", "Virer Hunting Ground"})
        {
            Assert.assertFalse(
                    location + " must be gated without The Blood Moon Rises",
                    service.isAvailable(location));

            Assert.assertEquals(
                    "Missing-requirement text for " + location,
                    questName,
                    service.getMissingText(location));
        }
    }

    // -------------------------------------------------------------------------
    // Name resolution into the requirement map
    // -------------------------------------------------------------------------

    /**
     * Task data is hand-written and inconsistently capitalised. Requirements are
     * stored under {@code resolveCanonical(key)}, so a lookup that differs only
     * in case must still find them.
     */
    @Test
    public void gatingIsCaseInsensitive()
    {
        Assert.assertFalse("lower-case lookup must still be gated",
                service.isAvailable("wyrmscraig cavern"));
        Assert.assertFalse("upper-case lookup must still be gated",
                service.isAvailable("WYRMSCRAIG CAVERN"));
        Assert.assertEquals("62 Sailing", service.getMissingText("WyRmScRaIg CaVeRn"));
    }

    /**
     * A task may reference a location by any registered alias, and the
     * requirement must follow. This is the failure that once orphaned the
     * Mourner Tunnels quest requirement: the coordinate entry was renamed, the
     * requirement key was not, and the gating silently disappeared instead of
     * erroring.
     */
    @Test
    public void gatingFollowsAliasesToTheCanonicalEntry()
    {
        String viaCanonical = service.getRequirementDescription("Mourner Tunnels");
        Assert.assertEquals(
                "Mourner Tunnels must be gated on Mourning's End Part II",
                Quest.MOURNINGS_END_PART_II.getName(),
                viaCanonical);

        for (String alias : new String[]{
                "Temple of Light",
                "Mourner HQ",
                "Path to Temple of Light (Mourner tunnels)"})
        {
            Assert.assertEquals(
                    "Alias '" + alias + "' must inherit the canonical requirement",
                    viaCanonical,
                    service.getRequirementDescription(alias));
        }
    }

    /**
     * Requirements are keyed by canonical name, so two keys in requirements.json
     * that resolve to the <em>same</em> canonical location silently overwrite one
     * another — last one parsed wins, and which one that is depends on map
     * iteration order. Three such pairs existed before this test
     * ("Meiyerditch Laboratories" over "Meiyerditch Dungeon", "Lithkren Vault"
     * over "Lithkren", and "Temple of Light" over
     * "Path to Temple of Light (Mourner tunnels)"). They happened to carry
     * identical values, so nothing was visibly broken — but the next edit to
     * either half of a pair would have been a coin flip.
     *
     * <p>Keying an entry by an alias is not itself wrong; keying <em>two</em>
     * entries that collapse to one canonical name is. Fix it by merging them
     * under the canonical name.</p>
     */
    @Test
    public void noTwoRequirementKeysCollapseToTheSameLocation() throws Exception
    {
        InputStream is = getClass().getResourceAsStream("/data/requirements.json");
        Assert.assertNotNull("requirements.json must exist on the classpath", is);

        int declaredKeys;
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8))
        {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            declaredKeys = root.getAsJsonObject("locations").entrySet().size();
        }

        Assert.assertEquals(
                "requirements.json declares " + declaredKeys + " location(s) but only "
                        + service.getGatedLocations().size() + " survived loading."
                        + " Two or more keys resolve to the same canonical location and are"
                        + " overwriting each other - merge them under the canonical name",
                declaredKeys,
                service.getGatedLocations().size());
    }

    // -------------------------------------------------------------------------
    // Negative cases
    // -------------------------------------------------------------------------

    /** An unrecorded location is open, not gated — the service must not fail closed. */
    @Test
    public void locationsWithNoRecordedRequirementAreAvailable()
    {
        Assert.assertTrue("Slayer Tower has no access requirement",
                service.isAvailable("Slayer Tower"));
        Assert.assertEquals("", service.getMissingText("Slayer Tower"));
        Assert.assertEquals("", service.getRequirementDescription("Slayer Tower"));

        Assert.assertTrue("a location string we have never seen must not be gated",
                service.isAvailable("Somewhere That Does Not Exist"));
    }

    /** Null location strings reach this service from task data; they must not throw. */
    @Test
    public void nullLocationIsTreatedAsAvailable()
    {
        Assert.assertTrue(service.isAvailable(null));
        Assert.assertEquals("", service.getMissingText(null));
        Assert.assertEquals("", service.getRequirementDescription(null));
    }
}
