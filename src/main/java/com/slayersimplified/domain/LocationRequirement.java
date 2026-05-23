/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.domain;

import net.runelite.api.Quest;
import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable description of the in-game requirements that gate access to a
 * Slayer location (quest completion and/or minimum skill levels).
 *
 * Built via {@link Builder} so call-sites stay readable when defining many
 * locations.
 */
public final class LocationRequirement
{
    /** Quests that must ALL be {@code FINISHED} for the location to be accessible (AND). */
    private final List<Quest> quests;
    /** At least ONE of these quests must be {@code FINISHED} (OR). Empty means no OR requirement. */
    private final List<Quest> questsAny;
    /** Minimum real (un-boosted) skill levels keyed by skill. */
    private final Map<Skill, Integer> skills;

    private LocationRequirement(List<Quest> quests, List<Quest> questsAny, Map<Skill, Integer> skills)
    {
        this.quests = Collections.unmodifiableList(new ArrayList<>(quests));
        this.questsAny = Collections.unmodifiableList(new ArrayList<>(questsAny));
        this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills));
    }

    public List<Quest> getQuests()
    {
        return quests;
    }

    /** Returns the OR-group quests; at least one must be finished when non-empty. */
    public List<Quest> getQuestsAny()
    {
        return questsAny;
    }

    public Map<Skill, Integer> getSkills()
    {
        return skills;
    }

    /**
     * @return true when every quest is in {@code completedQuests} and every
     *         skill in {@code skillLevels} meets or exceeds the requirement.
     */
    public boolean isMet(Set<Quest> completedQuests, Map<Skill, Integer> skillLevels)
    {
        for (Quest q : quests)
        {
            if (!completedQuests.contains(q))
            {
                return false;
            }
        }
        if (!questsAny.isEmpty())
        {
            boolean anyDone = false;
            for (Quest q : questsAny)
            {
                if (completedQuests.contains(q))
                {
                    anyDone = true;
                    break;
                }
            }
            if (!anyDone)
            {
                return false;
            }
        }
        for (Map.Entry<Skill, Integer> e : skills.entrySet())
        {
            Integer have = skillLevels.get(e.getKey());
            if (have == null || have < e.getValue())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Human-readable list of requirements not yet met. Empty string if all are met.
     */
    public String getMissingText(Set<Quest> completedQuests, Map<Skill, Integer> skillLevels)
    {
        StringBuilder sb = new StringBuilder();
        for (Quest q : quests)
        {
            if (!completedQuests.contains(q))
            {
                if (sb.length() > 0)
                {
                    sb.append(", ");
                }
                sb.append(q.getName());
            }
        }
        if (!questsAny.isEmpty())
        {
            boolean anyDone = false;
            for (Quest q : questsAny)
            {
                if (completedQuests.contains(q))
                {
                    anyDone = true;
                    break;
                }
            }
            if (!anyDone)
            {
                if (sb.length() > 0)
                {
                    sb.append(", ");
                }
                sb.append("one of: ");
                for (int i = 0; i < questsAny.size(); i++)
                {
                    if (i > 0)
                    {
                        sb.append(" or ");
                    }
                    sb.append(questsAny.get(i).getName());
                }
            }
        }
        for (Map.Entry<Skill, Integer> e : skills.entrySet())
        {
            Integer have = skillLevels.get(e.getKey());
            if (have == null || have < e.getValue())
            {
                if (sb.length() > 0)
                {
                    sb.append(", ");
                }
                sb.append(e.getValue()).append(' ').append(capitalize(e.getKey().getName()));
            }
        }
        return sb.toString();
    }

    /**
     * Full requirement summary, regardless of player state. Used for tooltips.
     */
    public String getDescription()
    {
        StringBuilder sb = new StringBuilder();
        for (Quest q : quests)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(q.getName());
        }
        if (!questsAny.isEmpty())
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            for (int i = 0; i < questsAny.size(); i++)
            {
                if (i > 0)
                {
                    sb.append(" or ");
                }
                sb.append(questsAny.get(i).getName());
            }
        }
        for (Map.Entry<Skill, Integer> e : skills.entrySet())
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(e.getValue()).append(' ').append(capitalize(e.getKey().getName()));
        }
        return sb.toString();
    }

    private static String capitalize(String s)
    {
        if (s == null || s.isEmpty())
        {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

    public static Builder builder()
    {
        return new Builder();
    }

    /** Fluent builder for {@link LocationRequirement}. */
    public static final class Builder
    {
        private final List<Quest> quests = new ArrayList<>();
        private final List<Quest> questsAny = new ArrayList<>();
        private final Map<Skill, Integer> skills = new LinkedHashMap<>();

        public Builder quest(Quest q)
        {
            quests.add(q);
            return this;
        }

        /** Adds a quest to the OR-group; at least one quest in the group must be finished. */
        public Builder questAny(Quest q)
        {
            questsAny.add(q);
            return this;
        }

        public Builder skill(Skill s, int level)
        {
            skills.put(s, level);
            return this;
        }

        public LocationRequirement build()
        {
            return new LocationRequirement(quests, questsAny, skills);
        }
    }
}
