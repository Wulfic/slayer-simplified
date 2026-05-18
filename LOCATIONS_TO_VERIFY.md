# Locations Needing Manual Coordinate Verification

These slayer locations should be tested in-game with Shortest Path. For each:

1. Enable **Debug Coordinates** in plugin settings (so the requirement check is bypassed and every Nav button is active).
2. Stand at the location's *intended* entrance.
3. Click **Nav** and watch for "destination unreachable" or a path that ends nowhere useful.
4. If broken, use the in-game tile coordinates display (Dev Tools or RuneLite's tile indicator) to read off a better entry tile and update `src/main/resources/data/location_coordinates.json`.

The current coord is shown next to each entry; the "Try" column lists candidates worth testing first if the current one fails.

## User-reported broken (top priority)

| Location | Current (x, y, plane) | Reason it likely fails | Try |
| --- | --- | --- | --- |
| Iorwerth Dungeon | 3222, 6048, 0 | Inside the Prifddinas instance (y > 6000) — Shortest Path may not route into instanced regions. | Stand at the dungeon entrance ladder inside Prifddinas crystal city before recording. Likely something near 2192, 3360 (non-instance overworld entry) or test if the instance version works after Song of the Elves. |
| Wyvern Cave | 3319, 3402, 0 | These coords are near Varrock — wrong region entirely. Wyvern Cave is on Fossil Island. | Try 3744, 3812, 0 (museum camp area) or the cave mouth proper around 3645, 3815, 0. |
| God Wars Dungeon | 2910, 3763, 0 | Just outside the boulder/rope entrance — may route through unreachable Wilderness tile. | Try 2916, 3745, 0 (top of rope inside the cave) or the entrance boulder at 2882, 3761, 0. |
| Ancient Cavern | 2511, 3508, 0 | Coord is on the surface, not at the whirlpool entry. | Try 2513, 3463, 0 (top of Baxtorian Falls whirlpool) or 2527, 3486, 0 (raft launch). |

## Other quest-locked / instanced dungeons to spot-check

These now grey out automatically in the UI for players without the quest. **In debug mode they're clickable** — please test each.

- Iorwerth Camp (3225, 6082, 0) — Prifddinas instance, same caveat as Iorwerth Dungeon.
- Prifddinas — same instance issue.
- Mourner Tunnels / Temple of Light — may need MEP2 instance.
- Ape Atoll Dungeon — verify entrance tile vs. shrine teleport.
- Underground Pass — multi-stage dungeon, verify entry tile.
- Temple of Ikov — verify ladder tile.
- Brine Rat Cavern — Olaf's Quest gated; verify entry.
- Dorgesh-Kaan South Dungeon — plane 3, common Shortest Path edge case.
- Lithkren / Lithkren Vault — boat-only access; coord likely too far from a walkable tile.
- Fossil Island — verify Bone Voyage barge dropoff (3724, 3805, 0).
- Mos Le'Harmless / Mos Le'Harmless Cave — boat-only.
- Zanaris — fairy ring / shed; verify the inside-shed tile.
- Lunar Isle — verify dock.
- Keldagrim — verify the entrance tunnel (north of trapdoor near Dwarven Mine).
- Crandor — boat / ship hull.
- Mort'ton / Shade Catacombs — verify trapdoor tile.
- Lair of Tarn Razorlor — verify Salve Graveyard tunnel.
- Witchaven Dungeon — verify trapdoor.
- Heroes' Guild / Legends' Guild basement — interior tiles.
- Tree Gnome Village dungeon — maze interior.
- Trollheim / God Wars Dungeon — Trollheim summit boulder.

## Other commonly-tricky locations (no quest gate)

These have no quest req but are known shortest-path edge cases worth a quick test:

- Karuulm Slayer Dungeon — verify entrance crater tile.
- Catacombs of Kourend — verify entry stairs.
- Smoke Devil Dungeon — verify entry.
- Kraken Cove — verify entry rocks.
- Revenant Caves — Wilderness-only; verify both Wilderness ditch routes work.
- Stronghold Slayer Cave — verify ladder.
- Waterbirth Island Dungeon — verify ladder inside Waterbirth.
- Miscellania & Etceteria Dungeon — verify entry.
- Asgarnian Ice Dungeon — verify ladder.
- Taverley Dungeon — verify ladder.
- Karamja Dungeon — verify entrance from Brimhaven.
- Lumbridge Swamp Caves — verify trapdoor.
- Goblin Cave — verify ladder.
- Sourhog Cave — verify entrance.
- Dungeon under Sophanem — verify entry.
- Dungeon east of the Agility Pyramid — verify entry tile.

## Plane / Z-axis sanity check

Locations with `"plane": > 0` in `location_coordinates.json` should each be re-confirmed — Shortest Path can route to upper floors but the coord must be **on** a walkable tile of that plane, not in air.

Currently the only `plane: 3` entry is **Dorgesh-Kaan South Dungeon**.
