# Slayer Simplified

A RuneLite plugin that makes slayer less painful. It keeps track of your current task, lets you browse monsters and their drop tables, and hooks into the [Shortest Path](https://github.com/Skretzo/shortest-path) plugin so you can navigate to any location with one click.

> **Shortest Path is required** for the navigation buttons to work. If you don't have it installed, everything else still works fine.

---

## What it does

When you get assigned a task, the plugin picks it up from the chat and shows a small banner at the top of the panel with a **Nav** button. Hit that and Shortest Path will draw a route to the monster's location automatically. If you haven't started a task yet, the same button routes you to your preferred slayer master instead.

Typing `!task` in public chat also triggers the same navigation — handy if you've logged back in and want to get back to your task without clicking through menus.

---

## Features

**Task search**
Searchable list of 100+ monsters with icons. Click any monster to open its detail view with tabs for locations, loot, info, and notes.

**Locations tab**
Every location has a Nav button and a favorite star (★). Your favorite is pinned to the top and remembered between sessions. There are 194 mapped locations — dungeons use the overworld entrance so Shortest Path handles the routing underground.

**Loot tab**
Pulls the drop table from the OSRS Wiki when you open the tab. Shows item name, quantity, rarity (color-coded), and GE price. Cached per monster so it's not fetching every time you switch back.

**Info tab**
Shows required items, slayer master assignments, and combat stats scraped from the Wiki — combat level, HP, max hit, attack style, elemental weakness, and immunities. Loads async in the background so the tab is instant to open.

**Notes tab**
Free-text notes per monster. Auto-saved as you type, stored in RuneLite's config so they persist across sessions.

**NPC highlighting**
Draws a colored outline on task NPCs in the scene. Updated from spawn/despawn events so there's no per-frame scan. Color is configurable.

**Task history**
Logs every task you complete — monster name, kill count, which master assigned it, task number in your streak, and the date. Viewable from the History button in the panel.

**Kill counter**
Tracks how many kills you've done for a particular monster.

**Slayer cape reminder**
Optional setting — shows a reminder when you get a new task if you have 99 Slayer but didn't bring your cape. Off by default.

**Suggested & Required Items**
Reminders to bring both Required Slayer Items, and Suggested items, that may be needed to access a certain area.

**Navigation masters**
All 8 masters are supported: Turael, Mazchna, Vannaka, Chaeldar, Konar, Nieve / Steve, Duradel, and Krystilia.

---

## Bug reports & support

Found a bug? [Open an issue](https://github.com/Wulfic/slayer-simplified/issues) on GitHub.

If you enjoy the plugin, [buy me a Ko-fi](https://ko-fi.com/wulfic) ☕

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
