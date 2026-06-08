# Slayer Simplified

A RuneLite plugin that makes slayer less painful. It keeps track of your current task, lets you browse monsters and their drop tables, and hooks into the [Shortest Path](https://github.com/Skretzo/shortest-path) plugin so you can navigate to any location with one click.

> **Shortest Path is required** for the navigation buttons to work. If you don't have it installed, everything else still works fine.

---

## What it does

Type `!task` to start auto-navigating, it will select your favorited location for that monster and route you to the location. Navigation is limited to where shortest path can navigate to, and tiles notes are used to fill in the rest. Check out the features below for a comprehensive list of what it can do!
---

## Features

**Task search**
Searchable list of over 100+ monsters.

**Locations tab**
Every location has a Nav button and a favorite star (★). Your favorite is pinned to the top and remembered between sessions. There are over 200+ mapped locations.

**Navigation Filtering**
Automatically checks you meet the requirements to go to a location. If you dont it greys out.(Debug Location setting disables this)

**Loot tab**
Pulls the drop table from the OSRS Wiki when you open the tab. Shows item name, quantity, rarity, and GE price.

**Info tab**
Shows required items, slayer master assignments, and combat stats scraped from the Wiki — combat level, HP, max hit, attack style, elemental weakness, and immunities.

**Notes tab**
Free-text notes per monster. Auto-saved as you type!

**NPC highlighting**
Highlight your target NPCs with a configurable color of your choice!

**Task history**
Logs every task you complete — monster name, kill count, which master assigned it, task number in your streak, and the date.

**Kill counter**
Tracks how many kills you've done for a particular monster.

**Slayer cape reminder**
Optional setting — shows a reminder when you get a new task if you have 99 Slayer but didn't bring your cape. Off by default.

**Suggested & Required Items**
Reminders to bring both Required Slayer Items, and Suggested items, that may be needed to access a certain area.

**Slayer masters**
All 9 masters are supported: Turael, Spria, Mazchna, Vannaka, Chaeldar, Konar, Nieve / Steve, Duradel, and Krystilia.

**Slayer Streak Optimizer!**
Automatically assigns you the best Slayer Master for optimizing your task streak, to get the most points possible!

**Tile Notes**
Notes on specific tiles such as safe spots, or help traveling to certain areas by using tile notes/hints.

---

## Bug reports & support

Found a bug? [Open an issue](https://github.com/Wulfic/slayer-simplified/issues) on GitHub.

If you enjoy the plugin, [buy me a Ko-fi](https://ko-fi.com/wulfic) ☕

On a side note, I have not unlocked every single area yet, as such I cannot verify cords for every location. So please open bug reports with correct cordinates using the debug option in settings!

---

## Building

```
./gradlew build
./gradlew run
```
or Live test the plugin on windows
```
./dev-run.ps1
```

---

## Credits

Built on top of some great existing work:
- [Slayer Assistant](https://github.com/LeeOkeefe/slayer-assistant-plugin) by LeeOKeefe — original task data and UI foundation
- [Loot Lookup](https://github.com/donth77/loot-lookup-plugin) by donth77 — OSRS Wiki drop table scraping
- [Shortest Path](https://github.com/Skretzo/shortest-path) by Skretzo — pathfinding and PluginMessage API

## License

BSD 2-Clause — see [LICENSE](LICENSE) for details.
