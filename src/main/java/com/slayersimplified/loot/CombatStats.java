/*
 * BSD 2-Clause License
 * Copyright (c) 2026, Slayer Simplified contributors
 * See LICENSE for details.
 */
package com.slayersimplified.loot;

import lombok.Value;

import java.util.Collections;
import java.util.List;

/**
 * Combat statistics scraped from the OSRS Wiki infobox for a monster.
 * <p>
 * A wiki page often describes several stat blocks in one {@code {{Infobox Monster}}} —
 * the "versions" its switch tabs expose (e.g. Black dragon level 227 vs 247). Each such
 * block becomes a {@link Variant}; monsters with a single stat block have exactly one
 * variant with an empty {@link Variant#getName() name}.
 */
@Value
public class CombatStats
{
    List<Variant> variants;

    public static CombatStats empty()
    {
        return new CombatStats(Collections.emptyList());
    }

    public boolean isEmpty()
    {
        return variants == null || variants.isEmpty();
    }

    /** A single stat block. */
    @Value
    public static class Variant
    {
        /** Version label from the infobox ("Level 227"), or "" when the page has one block. */
        String name;
        String combatLevel;
        String hitpoints;
        String maxHit;
        String attackStyle;
        String attribute;
        String elementalWeakness;
        String elementalWeaknessPercent;
        String immunePoison;
        String immuneVenom;
        String immuneCannon;
        String immuneThrall;
        String immuneBurn;

        /** True when the block carries no stat worth rendering. */
        public boolean hasNoStats()
        {
            return combatLevel.isEmpty() && hitpoints.isEmpty()
                    && maxHit.isEmpty() && attackStyle.isEmpty();
        }

        /**
         * Identity of the stats themselves, ignoring the version label. Pages routinely
         * repeat one stat block across many versions that differ only by NPC id or
         * location (Zombie has 13, Werewolf 22); collapsing on this key keeps the panel
         * to the blocks that actually differ.
         */
        String statsKey()
        {
            return String.join("|", combatLevel, hitpoints, maxHit, attackStyle, attribute,
                    elementalWeakness, elementalWeaknessPercent,
                    immunePoison, immuneVenom, immuneCannon, immuneThrall, immuneBurn);
        }

        Variant withName(String newName)
        {
            return new Variant(newName, combatLevel, hitpoints, maxHit, attackStyle, attribute,
                    elementalWeakness, elementalWeaknessPercent,
                    immunePoison, immuneVenom, immuneCannon, immuneThrall, immuneBurn);
        }
    }
}
