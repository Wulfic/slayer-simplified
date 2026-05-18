/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.services;

import com.slayersimplified.domain.LocationRequirement;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;

import javax.inject.Inject;
import javax.inject.Singleton;
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
 * Locations not listed here are treated as having no special requirements.
 * Keys are stored lower-cased to match the convention used by
 * {@link LocationCoordinateService}.
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

    /** Lower-cased location name → its requirement. */
    private final Map<String, LocationRequirement> requirements;

    /** Lower-cased location name → items suggested for access (e.g. light sources, climbing boots). */
    private final Map<String, List<String>> locationSuggestedItems;

    /** Quests we need to query (union of every requirement's quests). */
    private final Set<Quest> trackedQuests;

    /** Skills we need to query (union of every requirement's skills). */
    private final Set<Skill> trackedSkills;

    private volatile Set<Quest> completedQuests = Collections.emptySet();
    private volatile Map<Skill, Integer> skillLevels = Collections.emptyMap();

    /** Callbacks invoked (on the client thread) after each successful refresh. */
    private final List<Runnable> refreshListeners = new ArrayList<>();

    @Inject
    public LocationRequirementService(Client client)
    {
        this.client = client;
        Map<String, LocationRequirement> reqs = new HashMap<>();
        defineRequirements(reqs);
        this.requirements = Collections.unmodifiableMap(reqs);

        Map<String, List<String>> items = new HashMap<>();
        defineLocationSuggestedItems(items);
        this.locationSuggestedItems = Collections.unmodifiableMap(items);

        Set<Quest> qs = EnumSet.noneOf(Quest.class);
        Set<Skill> ss = EnumSet.noneOf(Skill.class);
        for (LocationRequirement r : reqs.values())
        {
            qs.addAll(r.getQuests());
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
        log.info("LocationRequirementService refreshed: {} / {} quests completed, {} skills tracked",
                completed.size(), trackedQuests.size(), levels.size());
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
        LocationRequirement r = requirements.get(locationName.toLowerCase());
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
        LocationRequirement r = requirements.get(locationName.toLowerCase());
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
        LocationRequirement r = requirements.get(locationName.toLowerCase());
        return r == null ? "" : r.getDescription();
    }

    /**
     * Returns items suggested for accessing the given location (e.g. a light
     * source for dark caves, climbing boots for mountain paths). Returns an
     * empty list when no suggestions are recorded for this location.
     */
    public List<String> getSuggestedItems(String locationName)
    {
        if (locationName == null)
        {
            return Collections.emptyList();
        }
        List<String> items = locationSuggestedItems.get(locationName.toLowerCase());
        return items == null ? Collections.emptyList() : items;
    }

    /**
     * Aggregates suggested access items across all provided locations,
     * removing duplicates while preserving insertion order.
     */
    public List<String> getSuggestedItemsForLocations(String[] locations)
    {
        if (locations == null || locations.length == 0)
        {
            return Collections.emptyList();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String loc : locations)
        {
            seen.addAll(getSuggestedItems(loc));
        }
        return new ArrayList<>(seen);
    }

    /** Visible for the verification doc — every location name we gate. */
    public Set<String> getGatedLocations()
    {
        return Collections.unmodifiableSet(new LinkedHashSet<>(requirements.keySet()));
    }

    /**
     * Populates the location → requirement map. All access requirements are
     * sourced from the OSRS Wiki and concern entering the area, not killing
     * the monsters within (slayer level reqs are intentionally omitted as
     * they don't block reaching the dungeon).
     */
    private static void defineRequirements(Map<String, LocationRequirement> m)
    {
        // === Morytania (PRIEST_IN_PERIL gates the whole region) ===
        put(m, "Morytania", q(Quest.PRIEST_IN_PERIL));
        put(m, "Canifis", q(Quest.PRIEST_IN_PERIL));
        put(m, "Mort'ton", q(Quest.PRIEST_IN_PERIL));
        put(m, "Haunted Woods", q(Quest.PRIEST_IN_PERIL));
        put(m, "Paterdomus", q(Quest.PRIEST_IN_PERIL));
        put(m, "Mort Myre", q(Quest.PRIEST_IN_PERIL));
        put(m, "Mort Myre Swamp", q(Quest.PRIEST_IN_PERIL));
        put(m, "Barrows", q(Quest.PRIEST_IN_PERIL));
        put(m, "Shade Catacombs", LocationRequirement.builder()
                .quest(Quest.PRIEST_IN_PERIL).quest(Quest.SHADES_OF_MORTTON).build());
        put(m, "Haunted Mine", LocationRequirement.builder()
                .quest(Quest.PRIEST_IN_PERIL).quest(Quest.HAUNTED_MINE).build());
        put(m, "Tarn's Lair", LocationRequirement.builder()
                .quest(Quest.PRIEST_IN_PERIL).quest(Quest.HAUNTED_MINE).build());
        put(m, "Lair of Tarn Razorlor", LocationRequirement.builder()
                .quest(Quest.PRIEST_IN_PERIL).quest(Quest.HAUNTED_MINE).build());
        put(m, "Meiyerditch", LocationRequirement.builder()
                .quest(Quest.PRIEST_IN_PERIL).quest(Quest.IN_AID_OF_THE_MYREQUE).build());
        put(m, "Meiyerditch Dungeon", LocationRequirement.builder()
                .quest(Quest.PRIEST_IN_PERIL).quest(Quest.IN_AID_OF_THE_MYREQUE).build());
        put(m, "Slepe", q(Quest.SINS_OF_THE_FATHER));

        // === Tirannwn (elf lands) ===
        put(m, "Lletya", q(Quest.MOURNINGS_END_PART_I));
        put(m, "Mourner Tunnels", q(Quest.MOURNINGS_END_PART_II));
        put(m, "Temple of Light", q(Quest.MOURNINGS_END_PART_II));
        put(m, "Prifddinas", q(Quest.SONG_OF_THE_ELVES));
        put(m, "Iorwerth Dungeon", q(Quest.SONG_OF_THE_ELVES));
        put(m, "Underground Pass", q(Quest.UNDERGROUND_PASS));
        put(m, "Tirannwn", q(Quest.REGICIDE));

        // === Gnome / monkey lands ===
        put(m, "Ape Atoll", q(Quest.MONKEY_MADNESS_I));
        put(m, "Ape Atoll Dungeon", q(Quest.MONKEY_MADNESS_I));
        put(m, "Ape Atoll temple", q(Quest.MONKEY_MADNESS_I));
        put(m, "Tree Gnome Village dungeon", q(Quest.TREE_GNOME_VILLAGE));

        // === Fremennik / Lunar ===
        put(m, "Fremennik Slayer Dungeon", q(Quest.THE_FREMENNIK_TRIALS));
        put(m, "Lighthouse", q(Quest.HORROR_FROM_THE_DEEP));
        put(m, "Lunar Isle", q(Quest.LUNAR_DIPLOMACY));
        put(m, "Brine Rat Cavern", q(Quest.OLAFS_QUEST));
        put(m, "Jatizso", q(Quest.THE_FREMENNIK_ISLES));
        put(m, "Neitiznot", q(Quest.THE_FREMENNIK_ISLES));

        // === Troll country ===
        put(m, "Trollheim", q(Quest.EADGARS_RUSE));
        put(m, "Death Plateau", q(Quest.DEATH_PLATEAU));
        put(m, "Troll Stronghold", q(Quest.TROLL_STRONGHOLD));
        // GWD is accessible via boulder push (60 Str) or agility shortcut etc.;
        // the universally-reliable approach is Trollheim teleport after Eadgar's Ruse.
        put(m, "God Wars Dungeon", q(Quest.EADGARS_RUSE));

        // === Misc quest-gated ===
        put(m, "Temple of Ikov", q(Quest.TEMPLE_OF_IKOV));
        put(m, "Witchaven Dungeon", q(Quest.THE_SLUG_MENACE));
        put(m, "Witchaven Shrine Dungeon", q(Quest.THE_SLUG_MENACE));
        put(m, "Heroes' Guild", q(Quest.HEROES_QUEST));
        put(m, "Legends' Guild basement", q(Quest.LEGENDS_QUEST));
        put(m, "Mos Le'Harmless", q(Quest.CABIN_FEVER));
        put(m, "Mos Le'Harmless Cave", q(Quest.CABIN_FEVER));
        put(m, "Crandor", q(Quest.DRAGON_SLAYER_I));
        put(m, "Lithkren", q(Quest.DRAGON_SLAYER_II));
        put(m, "Lithkren Vault", q(Quest.DRAGON_SLAYER_II));
        put(m, "Ancient Cavern", q(Quest.WATERFALL_QUEST));
        put(m, "Waterfall Dungeon", q(Quest.WATERFALL_QUEST));
        put(m, "Glarial's Tomb", q(Quest.WATERFALL_QUEST));
        put(m, "Zanaris", q(Quest.LOST_CITY));
        put(m, "Keldagrim", q(Quest.THE_GIANT_DWARF));
        put(m, "Dorgesh-Kaan", LocationRequirement.builder()
                .quest(Quest.THE_LOST_TRIBE).quest(Quest.DEATH_TO_THE_DORGESHUUN).build());
        put(m, "Dorgesh-Kaan South Dungeon", LocationRequirement.builder()
                .quest(Quest.THE_LOST_TRIBE).quest(Quest.DEATH_TO_THE_DORGESHUUN).build());

        // === Fossil Island ===
        put(m, "Fossil Island", q(Quest.BONE_VOYAGE));
        put(m, "Wyvern Cave", q(Quest.BONE_VOYAGE));
        put(m, "Wyvern Tasks", q(Quest.BONE_VOYAGE));

        // === Varlamore (recent content) ===
        put(m, "Varlamore", q(Quest.CHILDREN_OF_THE_SUN));
        put(m, "Aldarin", q(Quest.CHILDREN_OF_THE_SUN));
        put(m, "Ruins of Tapoyauik", q(Quest.CHILDREN_OF_THE_SUN));
        put(m, "Neypotzli", q(Quest.PERILOUS_MOONS));

        // === Skill-only gates ===
        put(m, "Mining Guild", LocationRequirement.builder()
                .skill(Skill.MINING, 60).build());
    }

    /**
     * Populates the location → suggested access items map.
     * These are items a player should bring to enter the area, independent of
     * which monster they are assigned (e.g. a light source for dark caves).
     */
    private static void defineLocationSuggestedItems(Map<String, List<String>> m)
    {
        // ── Dark caves ────────────────────────────────────────────────────────
        // A light source prevents the periodic fire damage dealt in unlit areas.
        List<String> lightSource = Collections.singletonList("Light source (e.g. Bullseye lantern)");
        m.put("lumbridge swamp caves", lightSource);
        m.put("dorgesh-kaan south dungeon", lightSource);
        m.put("mos le'harmless cave", lightSource);
        m.put("haunted mine", lightSource);

        // ── Climbing boots ────────────────────────────────────────────────────
        // Required to traverse the rocky mountain paths to Trollheim and beyond.
        List<String> climbingBoots = Collections.singletonList("Climbing boots");
        m.put("trollheim", climbingBoots);
        m.put("troll stronghold", climbingBoots);
        m.put("death plateau (mountain troll)", climbingBoots);
        m.put("ice path north of trollheim (ice trolls)", climbingBoots);

        // ── Smoke dungeons ────────────────────────────────────────────────────
        // The smoky atmosphere deals continuous damage without protection.
        List<String> smokeProt = Collections.singletonList("Facemask or Slayer helmet (smoke damage)");
        m.put("smoke dungeon", smokeProt);
        m.put("smoke devil dungeon", smokeProt);

        // ── Brimhaven Dungeon ─────────────────────────────────────────────────
        // Entry requires a fee to Murcaily or a Dragon axe to chop the vines.
        m.put("brimhaven dungeon", Collections.singletonList("875 coins (entry fee) or Dragon axe"));

        // ── Evil Chicken's Lair ───────────────────────────────────────────────
        // The portal in Zanaris only opens when carrying a raw chicken.
        m.put("evil chicken's lair", Collections.singletonList("Raw chicken (required to enter the lair)"));

        // ── Ape Atoll ─────────────────────────────────────────────────────────
        // Without a Greegree, every guard on the island is aggressive.
        List<String> greegree = Collections.singletonList("Greegree (to navigate Ape Atoll without constant attacks)");
        m.put("ape atoll dungeon", greegree);
        m.put("ape atoll temple", greegree);

        // ── Waterbirth Island Dungeon ─────────────────────────────────────────
        // Double doors deeper in the dungeon require two people or a pet rock.
        m.put("waterbirth island dungeon", Collections.singletonList(
                "Pet rock + rope (to open double doors solo) or bring a partner"));

        // ── Entrana Dungeon ───────────────────────────────────────────────────
        // Monks on the docks refuse to ferry players carrying any weapon/armour.
        m.put("entrana dungeon", Collections.singletonList(
                "Warning: no weapons or armour allowed on Entrana — pick up gear inside the dungeon"));
    }

    private static LocationRequirement q(Quest quest)
    {
        return LocationRequirement.builder().quest(quest).build();
    }

    private static void put(Map<String, LocationRequirement> m, String name, LocationRequirement r)
    {
        m.put(name.toLowerCase(), r);
    }
}
