/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.slayersimplified.domain.LocationRequirement;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks the player's quest completion and skill levels and exposes whether
 * each Slayer location's in-game access requirements are met.
 *
 * Requirements are loaded data-driven from {@code /data/requirements.json}.
 * Alias resolution is delegated to {@link LocationCoordinateService}, so a
 * task file can use any registered alias and the correct requirement is found.
 *
 * State is refreshed from the client by {@link #refresh()}, which MUST be
 * called on the client thread (it invokes {@link Quest#getState(Client)},
 * which runs a script). The plugin schedules refresh on login.
 */
@Slf4j
@Singleton
public class LocationRequirementService
{
    private final Client client;
    private final LocationCoordinateService locationCoordinateService;

    /** Canonical lower-case location name → its requirement. */
    private final Map<String, LocationRequirement> requirements;

    /** Quests we need to query (union of every requirement's quests). */
    private final Set<Quest> trackedQuests;

    /** Skills we need to query (union of every requirement's skills). */
    private final Set<Skill> trackedSkills;

    private volatile Set<Quest> completedQuests = Collections.emptySet();
    private volatile Map<Skill, Integer> skillLevels = Collections.emptyMap();

    /** Callbacks invoked (on the client thread) after each successful refresh. */
    private final List<Runnable> refreshListeners = new ArrayList<>();

    @Inject
    public LocationRequirementService(Client client, LocationCoordinateService locationCoordinateService, Gson gson)
    {
        this.client = client;
        this.locationCoordinateService = locationCoordinateService;

        Map<String, LocationRequirement> reqs = new HashMap<>();
        loadFromRequirementsFile(gson, reqs);
        this.requirements = Collections.unmodifiableMap(reqs);

        Set<Quest> qs = EnumSet.noneOf(Quest.class);
        Set<Skill> ss = EnumSet.noneOf(Skill.class);
        for (LocationRequirement r : reqs.values())
        {
            qs.addAll(r.getQuests());
            qs.addAll(r.getQuestsAny());
            ss.addAll(r.getSkills().keySet());
        }
        this.trackedQuests = Collections.unmodifiableSet(qs);
        this.trackedSkills = Collections.unmodifiableSet(ss);
    }

    /**
     * Registers a callback that will be invoked on the client thread after
     * every successful {@link #refresh()}. Intended for triggering Swing
     * redraws via {@code SwingUtilities.invokeLater(…)}.
     */
    public void addRefreshListener(Runnable listener)
    {
        refreshListeners.add(listener);
    }

    /**
     * Re-reads quest completion and skill levels from the client.
     * MUST be called on the client thread.
     */
    public void refresh()
    {
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            return;
        }
        Set<Quest> completed = EnumSet.noneOf(Quest.class);
        for (Quest q : trackedQuests)
        {
            try
            {
                if (q.getState(client) == QuestState.FINISHED)
                {
                    completed.add(q);
                }
            }
            catch (Exception ex)
            {
                log.warn("Failed to read quest state for {}", q.name(), ex);
            }
        }
        Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
        for (Skill s : trackedSkills)
        {
            levels.put(s, client.getRealSkillLevel(s));
        }
        this.completedQuests = Collections.unmodifiableSet(completed);
        this.skillLevels = Collections.unmodifiableMap(levels);
        log.info("LocationRequirementService refreshed: {}/{} quests completed ({}), {} skills tracked",
                completed.size(), trackedQuests.size(),
                completed.isEmpty() ? "none" : completed.stream().map(Quest::name).collect(java.util.stream.Collectors.joining(", ")),
                levels.size());
        for (Runnable listener : refreshListeners)
        {
            listener.run();
        }
    }

    /**
     * @return true when the location has no recorded requirement or the
     *         player meets every recorded requirement.
     */
    public boolean isAvailable(String locationName)
    {
        if (locationName == null)
        {
            return true;
        }
        String canonical = locationCoordinateService.resolveCanonical(locationName);
        LocationRequirement r = requirements.get(canonical);
        if (r == null)
        {
            return true;
        }
        return r.isMet(completedQuests, skillLevels);
    }

    /**
     * @return human-readable list of unmet requirements for the location,
     *         or empty string if the location is fully available / unknown.
     */
    public String getMissingText(String locationName)
    {
        if (locationName == null)
        {
            return "";
        }
        String canonical = locationCoordinateService.resolveCanonical(locationName);
        LocationRequirement r = requirements.get(canonical);
        if (r == null)
        {
            return "";
        }
        return r.getMissingText(completedQuests, skillLevels);
    }

    /**
     * @return the full requirement description for the location, or empty
     *         string if no requirement is recorded.
     */
    public String getRequirementDescription(String locationName)
    {
        if (locationName == null)
        {
            return "";
        }
        String canonical = locationCoordinateService.resolveCanonical(locationName);
        LocationRequirement r = requirements.get(canonical);
        return r == null ? "" : r.getDescription();
    }

    /**
     * Returns items suggested for accessing the given location (e.g. a light
     * source for dark caves, climbing boots for mountain paths). Delegates to
    /** Returns all canonical gated location names (lower-case). */
    public Set<String> getGatedLocations()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(requirements.keySet()));
    }

    /**
     * Loads location requirements from {@code /data/requirements.json}.
     * Quest and Skill enum names must match their {@link Quest} / {@link Skill}
     * Java enum constants exactly. Unknown names are logged and skipped.
     */
    private void loadFromRequirementsFile(Gson gson, Map<String, LocationRequirement> m)
    {
        InputStream stream = getClass().getResourceAsStream("/data/requirements.json");
        if (stream == null)
        {
            log.warn("requirements.json not found — skipping dedicated requirements load");
            return;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
        {
            RequirementsFile reqFile = gson.fromJson(reader, RequirementsFile.class);
            if (reqFile == null || reqFile.locations == null)
            {
                return;
            }
            int loaded = 0;
            for (Map.Entry<String, LocationEntry> entry : reqFile.locations.entrySet())
            {
                String canonical = locationCoordinateService.resolveCanonical(entry.getKey());
                if (canonical == null) continue;

                LocationEntry locEntry = entry.getValue();
                LocationRequirement.Builder builder = LocationRequirement.builder();

                if (locEntry.quests != null)
                {
                    for (String questName : locEntry.quests)
                    {
                        try { builder.quest(Quest.valueOf(questName)); }
                        catch (IllegalArgumentException e)
                        {
                            log.warn("Unknown Quest '{}' for '{}' in requirements.json", questName, entry.getKey());
                        }
                    }
                }
                if (locEntry.questsAny != null)
                {
                    for (String questName : locEntry.questsAny)
                    {
                        try { builder.questAny(Quest.valueOf(questName)); }
                        catch (IllegalArgumentException e)
                        {
                            log.warn("Unknown Quest '{}' for '{}' (questsAny) in requirements.json", questName, entry.getKey());
                        }
                    }
                }
                if (locEntry.skills != null)
                {
                    for (Map.Entry<String, Integer> sk : locEntry.skills.entrySet())
                    {
                        try { builder.skill(Skill.valueOf(sk.getKey()), sk.getValue()); }
                        catch (IllegalArgumentException e)
                        {
                            log.warn("Unknown Skill '{}' for '{}' in requirements.json", sk.getKey(), entry.getKey());
                        }
                    }
                }
                m.put(canonical, builder.build());
                loaded++;
            }
            log.debug("Loaded {} location requirements from requirements.json", loaded);
        }
        catch (IOException e)
        {
            log.error("Failed to read requirements.json", e);
        }
    }

    /** Shape of the top-level requirements.json object. */
    private static final class RequirementsFile
    {
        Map<String, LocationEntry> locations;
    }

    /** One location entry in requirements.json. */
    private static final class LocationEntry
    {
        String[] quests;
        String[] questsAny;
        Map<String, Integer> skills;
    }

}
