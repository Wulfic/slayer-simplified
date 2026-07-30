# Wyrmscraig / Mortimer Update Plan

**Status:** Code + data + tests complete, plus an edge-case hardening pass (§8);
`./gradlew clean test` green (74 tests, 0 failures, 0 skips), every new assertion
mutation-verified.
**Awaiting:** in-game verification of §4.3 by the user. Not committed.
**Researched:** 2026-07-29 (release day)
**Sources:** OSRS Wiki MediaWiki API (`action=parse&prop=wikitext`) — `Mortimer`, `Wyrmscraig`,
`Wyrmscraig Cavern`, `Venator`, `Blood-starved venator`, `Slayer task/Vampyres`,
`Apsul Hunting Ground`, `Virer Hunting Ground`.

---

## 1. Context

Jagex shipped **Wyrmscraig** on **29 July 2026** ("Wyrmscraig Is Out Today!"), which adds
**Mortimer** — the first new Slayer master since Krystilia. The plugin currently knows about
9 masters and 122 slayer tasks; it has zero knowledge of Mortimer, of Wyrmscraig, or of
**venators** (a Slayer monster that actually shipped a month earlier, 30 June 2026, in
*The Blood Moon Rises*, and was already missed).

Consequence today: a player who takes a task from Mortimer gets **no panel entry, no
location routing, no requirement gating, and no master attribution** in task history. The
master-grouped task list silently drops him.

Intended outcome: Mortimer is a first-class master in the panel, all 29 of his assignable
tasks are tagged to him, venator exists as a task with correct requirements and locations,
and the new Wyrmscraig / Vampyrium locations resolve to real coordinates.

---

## 2. Research findings

### 2.1 Mortimer

| Field | Value |
|---|---|
| Release | 29 July 2026 |
| Location | Wyrmscraig Cavern (dungeon under Wyrmscraig) |
| NPC coords | `x=2589, y=8614, plane=0` |
| NPC IDs | 16175, 16294 |
| Menu options | Talk-to, **Assignment**, Trade, Rewards |
| Requirement | Combat 100 **and** Slayer 70 — *or* any combat level with 99 Slayer + Slayer cape shown |
| Quest | Partial completion of **Fallen From Grace** |
| Access | Slayer ring teleport, Astral Contact, Teleport to Boat (greater teleport focus), Necklace of passage, Ardeaglais teleport |

**Three things make him structurally different from every other master:**

1. **No standard Slayer points by default.** He uses "Mortifiers" — per-task rolled modifiers.
   Points are a *modifier* (5–40 depending on task), not a fixed per-task award.
2. **Superior-only assignments.** Every task he gives has a superior variant — he's the
   imbued heart / eternal gem master.
3. **Task choice.** He offers 2 options per assignment, 3 after 50 Mortimer tasks. No
   duplicate options in a single prompt.

**Economy differences:** skip costs **100** points (not 30). Only **2 block slots**, at
**120** points each (not 6–7 at 100).

**Streak:** counted **separately** from normal tasks, exactly like Krystilia. Task *storage*
is shared with the other masters. While Mortimer has an unpicked task choice pending, no
other master will assign — the player gets "Hmm... It appears as though Mortimer has
provided you with a task choice, you must go to them in order to pick."

**Mortifier unlock thresholds (live values, post-feedback):**

| Tasks | Modifier |
|---|---|
| 0 | Task awards X Slayer Points on completion |
| 0 | Task quantity modified by X |
| 15 | Clue scroll drop rates +X% |
| 25 | Superior slayer creatures hit the superior unique table X% more often |
| 40 | Slayer creatures award X% more Slayer XP |
| 50 | Adds a third task choice |

> Jagex's *initial proposal* was 0/0/25/50/75/100 — the wiki documents both. Use the live
> values above. Modifier values step in increments of 5.

**Exclusivity note:** Mortimer is the only master who assigns **venators** directly, and he
shares tasks that were previously single-master exclusives — **hydras** (Konar),
**infernal mages** (Vannaka), **rockslugs** (Mazchna). This does **not** change those other
masters' own lists; nothing existing needs to be un-tagged.

### 2.2 Mortimer's full assignment table (29 tasks)

Modifier ranges are min–max rolls, stepping by 5. `—` = not applicable.

| # | Monster | Amount | Extended | Weight | Qty mod | Pts mod | Clue mod | XP mod | Sup. unique mod | Plugin task key |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Crawling hands | 35–50 | — | 10 | −15–30 | 5–15 | — | 25–100% | 50–100% | `Crawling Hand` |
| 2 | Cave crawlers | 35–50 | — | 10 | −15–30 | 5–15 | — | 25–100% | 50–100% | `Cave crawler` |
| 3 | Banshees | 35–50 | — | 10 | −15–30 | 5–15 | 75–150% | 25–100% | 50–100% | `Banshee` |
| 4 | Rockslugs | 35–50 | — | 10 | −15–30 | 5–15 | — | 25–100% | 50–100% | `Rockslug` |
| 5 | Cockatrice | 35–50 | — | 10 | −15–30 | 5–15 | 75–150% | 25–100% | 50–100% | `Cockatrice` |
| 6 | Pyrefiends | 35–50 | — | 10 | −15–30 | 5–15 | 75–150% | 25–100% | 50–100% | `Pyrefiend` |
| 7 | Infernal mages | 35–50 | — | 10 | −15–30 | 5–15 | — | 25–100% | 50–100% | `Infernal Mage` |
| 8 | Bloodvelds | 120–180 | 200–250 | 8 | 50–100 | 10–15 | 35–75% | 5–15% | 100–150% | `Bloodveld` |
| 9 | Gryphons | 80–120 | 160–200 | 10 | 50–100 | 10–25 | 75–150% | 20–50% | 100–150% | `Gryphon` |
| 10 | Jellies | 80–120 | — | 10 | 50–100 | 10–15 | 35–75% | 10–25% | 100–150% | `Jelly` |
| 11 | Custodian stalkers | 80–120 | 200–250 | 8 | 50–100 | 10–15 | — | 10–25% | 100–150% | `Custodian stalker` |
| 12 | Turoth | 80–120 | — | 10 | 30–80 | 15–30 | 75–150% | 35–75% | 200–300% | `Turoth` |
| 13 | Warped creatures | 80–120 | — | 10 | 50–100 | 15–30 | 75–150% | 35–75% | 200–300% | `Warped creature` |
| 14 | Cave horrors | 80–120 | 200–250 | 10 | 30–80 | 15–30 | 75–150% | 35–75% | 200–300% | `Cave horror` |
| 15 | Aberrant spectres | 80–120 | 200–250 | 10 | 30–80 | 15–30 | 75–150% | 35–75% | 200–300% | `Aberrant spectre` |
| 16 | Basilisks | 40–60 | 200–250 | 10 | 50–100 | 25–40 | 75–150% | 35–75% | 200–300% | `Basilisk` |
| 17 | Wyrms | 80–120 | 200–250 | 10 | 50–100 | 25–40 | 75–150% | 35–75% | 200–300% | `Wyrm` |
| 18 | Dust devils | 120–180 | 200–250 | 8 | 50–150 | 10–15 | — | 10–20% | 100–150% | `Dust devil` |
| 19 | Kurask | 40–60 | — | 10 | 30–80 | 25–40 | 75–150% | 35–75% | 200–300% | `Kurask` |
| 20 | **Venators** | 120–180 | — | 10 | 50–100 | 10–25 | 75–150% | 35–75% | 200–300% | `Venator` ⚠️ **NEW** |
| 21 | Gargoyles | 120–180 | 200–250 | 10 | 50–100 | 15–30 | 75–150% | 35–75% | 200–300% | `Gargoyle` |
| 22 | Aquanites | 40–60 | 150–200 | 10 | 50–100 | 25–40 | 75–150% | 35–75% | 200–300% | `Aquanite` |
| 23 | Nechryael | 150–200 | 200–250 | 8 | 50–100 | 10–20 | 35–75% | 15–30% | 70–120% | `Nechryael` |
| 24 | Drakes | 40–60 | — | 10 | 30–80 | 25–40 | 75–150% | 25–75% | 75–150% | `Drake` |
| 25 | Abyssal demons | 120–180 | 200–250 | 8 | 50–100 | 10–25 | 35–75% | 10–25% | 30–60% | `Abyssal demon` |
| 26 | Dark beasts | 40–60 | 110–135 | 10 | 30–80 | 10–25 | 75–150% | 35–75% | 200–300% | `Dark beast` |
| 27 | Araxytes | 120–180 | 200–250 | 8 | 50–100 | 10–20 | 35–75% | 5–15% | 10–25% | `Araxyte` |
| 28 | Smoke devils | 80–120 | — | 8 | 50–100 | 10–20 | 35–75% | 5–15% | 10–25% | `Smoke devil` |
| 29 | Hydras | 150–200 | — | 10 | 50–100 | 15–30 | 75–150% | 10–30% | 75–150% | `Hydra` |

**Coverage check (already run against `src/main/resources/data/tasks/`):**
28 of 29 task files already exist. **Only `venator.json` is missing.**

### 2.3 Venator — the monster the plugin already missed

Released **30 June 2026** (*The Blood Moon Rises*), not part of this update, but it is a hard
prerequisite for Mortimer task #20.

| Field | Value |
|---|---|
| Slayer level | **74** |
| Quest | **The Blood Moon Rises** |
| Combat / HP | 246 / 345 |
| Attribute | Vampyre (tier 3) — requires **vampyrebane** weapons, Ivandis flail minimum |
| Attack style | Stab (melee adjacent, ranged otherwise), speed 5, max hit 21 |
| Immunities | Cannon ✅ immune, thrall ✅ immune, burn strongly resistant, poison/venom immune |
| Superior | **Blood-starved venator** (lvl 246) — the only superior that can spawn **off-task** |
| Locations | Apsul Hunting Ground, Virer Hunting Ground (both in Vampyrium) |
| Assigned by | Mazchna, Vannaka, Chaeldar, Konar, Nieve, Duradel (as a **Vampyres** task) + **Mortimer** (directly) |
| Off-task | Pay the nearby aranei 30 Stymphike feathers per kill |

Mechanic worth surfacing in notes: the **shriek** attack pierces protection prayers
("The venator's screech disrupts your concentration!"). **Earmuffs or a Slayer helmet**
significantly reduce shriek damage and shorten it by one tick — that belongs in
`itemsSuggested`. Melee hits from a venator **heal it** for the damage dealt.

### 2.4 Stale data discovered while researching

`src/main/resources/data/tasks/vampyre.json` lists `masters: ["Mazchna", "Vannaka"]`.
The wiki's `Slayer task/Vampyres` infobox says **mazchna, vannaka, chaeldar, konar, nieve,
duradel** — the higher masters unlock after the *Actual Vampyre Slayer* reward. It is also
missing the `Vyrewatch Sentinel`-era additions of `Venator` and `Blood-starved venator`
as variants. Fix it while we're in here.

### 2.5 New locations

| Location | Coords | Gate | Contents relevant to us |
|---|---|---|---|
| Wyrmscraig (island) | `2571, 2267, 0` | Sailing 62 | 6 Wyrmling, 1 Yak, 5 Bunny, 3 Chicken, 1 Seagull |
| Wyrmscraig Cavern | `2590, 8615, 0` | Sailing 62 + Fallen From Grace (partial) | **Mortimer**, 3 Bat, 7 Mountain troll, 5 Wyrm, 3 Lava Strykewyrm |
| Apsul Hunting Ground | `2583, 7773, 0` | The Blood Moon Rises | Venators, bloodwood trees |
| Virer Hunting Ground | `2525, 7762, 0` | The Blood Moon Rises | 12 Venators |

`location_coordinates.json` currently has **no Vampyrium-region entries at all** — Apsul and
Virer are both new.

`wyrm.json` already carries `Lava Strykewyrm` and `Magma Strykewyrm` variants (mapped to
Charred Dungeon) and `Wyrmling` (Neypotzli) — those just need Wyrmscraig locations added,
not new variants.

### 2.6 ⚠️ Blocker: `Quest.FALLEN_FROM_GRACE` does not exist

Verified directly against the resolved API jar
(`~/.gradle/caches/.../runelite-api-1.12.33.jar`, `javap` on `net/runelite/api/Quest.class`,
210 constants):

- `Quest.FALLEN_FROM_GRACE` — **ABSENT** ❌
- `Quest.THE_BLOOD_MOON_RISES` — present ✅
- `Skill.SAILING` — present ✅

`RequirementsJsonValidationTest.allQuestNamesAreValid()`
([RequirementsJsonValidationTest.java:62](src/test/java/com/slayersimplified/RequirementsJsonValidationTest.java#L62))
does `Quest.valueOf(name)` on every entry and fails the build on an unknown name. Adding
`"FALLEN_FROM_GRACE"` to `requirements.json` **will go red**.

**Decision:** gate Wyrmscraig Cavern on `SAILING: 62` only for now. Carry the quest
requirement as human-readable text, and leave a TODO to add the quest gate once RuneLite
ships the constant. Do **not** hack around the validation test.

---

## 3. Implementation plan

### Phase A — Domain / code (4 files)

**A1. `src/main/java/com/slayersimplified/domain/SlayerMaster.java`**

Add after `KRYSTILIA`, before the `NON_SLAYER_ENEMIES` block:

```java
MORTIMER("Mortimer", new WorldPoint(2589, 8614, 0), 0),
```

- `basePoints = 0` is **deliberate and must be commented**: Mortimer awards points via a
  rolled Mortifier (5–40 by task), not a fixed per-task value. A non-zero constant here
  would feed a lie into `SlayerStreakOptimizerService.getRecommendationReason()`.
- Enum **ordinal drives render order** in
  [GroupedTaskList.java:59](src/main/java/com/slayersimplified/presentation/components/GroupedTaskList.java#L59)
  — placing him last among real masters is correct.
- `fromTaskMasterName()` matches on `displayName.startsWith(name)`. `"Mortimer"` collides
  with nothing existing (`Mazchna` shares only `M`). Safe.
- Underground `WorldPoint` is already precedent — `VANNAKA` uses `(3145, 9914, 0)`. No
  navigation changes needed.

**A2. `src/main/java/com/slayersimplified/services/SlayerStreakOptimizerService.java`**

No behavioural change required — `getHighestEligibleMilestoneMaster()`
([:227](src/main/java/com/slayersimplified/services/SlayerStreakOptimizerService.java#L227))
only ever returns Konar/Chaeldar/Vannaka/Mazchna, so Mortimer can never be recommended.
**Update the class javadoc** ([:58](src/main/java/com/slayersimplified/services/SlayerStreakOptimizerService.java#L58))
to state that Mortimer is excluded for the same reason as Krystilia — separate streak
counter — so the next person doesn't "fix" it by adding him.

**A3. `src/main/java/com/slayersimplified/SlayerSimplifiedPlugin.java`**

Verify only — no edit expected. `onMenuOptionClicked`
([:697](src/main/java/com/slayersimplified/SlayerSimplifiedPlugin.java#L697)) already
matches `"Talk-to"` / `"Assignment"` against `displayName` case-insensitively, and
Mortimer's menu exposes `Assignment`. The new enum constant is picked up by the existing
`for (SlayerMaster master : SlayerMaster.values())` loop for free.

**A4. `src/main/java/com/slayersimplified/presentation/panels/SettingsPanel.java`**

Verify only — no edit expected. The preferred-master combo is built from
`SlayerMaster.values()` minus `NON_SLAYER_ENEMIES`
([:128](src/main/java/com/slayersimplified/presentation/panels/SettingsPanel.java#L128)),
so Mortimer appears automatically. Confirm the combo still fits its fixed
`120×22` bounds with the new entry.

### Phase B — Task data

**B1. New `src/main/resources/data/tasks/venator.json`**

Follow the exact shape of `crawling_hand.json` (name / levelRequired / itemsRequired /
itemsSuggested / attributes / attackStyles / variants / masters / variantLocations).

```
name:            "Venator"
levelRequired:   74
itemsRequired:   ["Ivandis flail", "Blisterwood flail", "Sunspear"]   // vampyrebane, any one
itemsSuggested:  ["Earmuffs", "Slayer helmet", "Saradomin godsword", "Ancient godsword"]
attributes:      ["Vampyre"]
attackStyles:    ["Stab", "Ranged"]
variants:        ["Venator --lvl 246", "Blood-starved venator --lvl 246"]
masters:         ["Mazchna","Vannaka","Chaeldar","Konar quo Maten","Nieve","Duradel","Mortimer"]
variantLocations:
  "Venator --lvl 246":               ["Apsul Hunting Ground", "Virer Hunting Ground"]
  "Blood-starved venator --lvl 246": ["Apsul Hunting Ground", "Virer Hunting Ground"]
```

**B2. `src/main/resources/data/tasks/_index.json`**

Append `{ "key": "Venator", "file": "venator.json" }`. The index is not alphabetically
sorted (`Frost dragon` and `Aquanite` sit at the top), so appending is consistent with
existing practice.

**B3. Tag the 29 Mortimer tasks**

Append `"Mortimer"` to the `masters` array in each of the 28 existing files listed in the
§2.2 table (venator.json already has it from B1). Mechanical, one-line-per-file edit —
**append, never reorder or reformat**, per AGENTS.md ("do not mix reformatting with
feature changes").

**B4. Fix `src/main/resources/data/tasks/vampyre.json`**

- `masters` → `["Mazchna","Vannaka","Chaeldar","Konar quo Maten","Nieve","Duradel"]`
- Add variants `Venator --lvl 246` and `Blood-starved venator --lvl 246` with the two
  Vampyrium hunting-ground locations.

**B5. Add Wyrmscraig spawns to existing tasks**

| File | Variant | Add location |
|---|---|---|
| `tasks/wyrm.json` | `Wyrm --lvl 97` | `Wyrmscraig Cavern` |
| `tasks/wyrm.json` | `Lava Strykewyrm --lvl 116` | `Wyrmscraig Cavern` |
| `tasks/wyrm.json` | `Wyrmling --lvl 55` | `Wyrmscraig` |
| `tasks/bat.json` | (bat variant) | `Wyrmscraig Cavern` |
| `tasks/troll.json` | Mountain troll variant | `Wyrmscraig Cavern` |
| `animal_tasks/yak.json`, `bunny.json` | base | `Wyrmscraig` |

Open the target files first — variant key strings must match byte-for-byte.

### Phase C — Location data

**C1. `src/main/resources/data/location_coordinates.json`** (alphabetically sorted — insert in place)

```json
"Apsul Hunting Ground": { "x": 2583, "y": 7773, "plane": 0 },
"Virer Hunting Ground": { "x": 2525, "y": 7762, "plane": 0 },
"Wyrmscraig":           { "x": 2571, "y": 2267, "plane": 0 },
"Wyrmscraig Cavern":    { "x": 2590, "y": 8615, "plane": 0,
                          "aliases": ["Wyrmscraig cavern"] }
```

**C2. `src/main/resources/data/requirements.json`**

Under `"locations"` (matching the existing `Deepfin Mine` / `Ynysdail Cavern` Sailing style):

```json
"Wyrmscraig":            { "skills": { "SAILING": 62 } },
"Wyrmscraig Cavern":     { "skills": { "SAILING": 62 } },
"Apsul Hunting Ground":  { "quests": ["THE_BLOOD_MOON_RISES"] },
"Virer Hunting Ground":  { "quests": ["THE_BLOOD_MOON_RISES"] }
```

Under `"monsters"`:

```json
"Venator": { "quests": ["THE_BLOOD_MOON_RISES"], "skills": { "SLAYER": 74 } }
```

> **TODO (blocked):** add `"quests": ["FALLEN_FROM_GRACE"]` to `Wyrmscraig Cavern` once
> RuneLite exposes the constant. See §2.6. Leave a comment in the commit body, not in the
> JSON — `requirements.json` is strict JSON with no comment support.

---

## 4. Testing

Non-negotiable. Nothing is "done" until §4.1 and §4.2 are green **and** the user has
confirmed §4.3 in-game.

### 4.1 Existing suite must stay green

```
./gradlew test
```

Two tests directly guard this change:

- `RequirementsJsonValidationTest.allQuestNamesAreValid` — catches `FALLEN_FROM_GRACE` if
  anyone sneaks it in.
- `RequirementsJsonValidationTest.everyRequirementLocationResolvesToAKnownLocation` —
  catches a `requirements.json` key that has no `location_coordinates.json` entry. This is
  exactly the trap that orphaned the Mourner Tunnels requirement previously.

Also relevant: `TaskServiceImageTest.loadingTasksLogsNoImageWarnings` — a task with no
bundled PNG must load silently. `venator.png` is **not** bundled
(`src/main/resources/images/monsters/` has 594 files, none of them venator), and the loader
degrades to a placeholder without warning
([SlayerTaskRenderer.java:124](src/main/java/com/slayersimplified/presentation/SlayerTaskRenderer.java#L124)).
Missing art is cosmetic, not a blocker.

### 4.2 New tests to write

1. **`MortimerTaskCoverageTest`** — load every task via `TaskServiceImpl`, assert exactly
   the 29 keys from §2.2 carry `"Mortimer"` in `masters`, and that no others do. This is
   the regression net for the mechanical B3 edit; a typo'd master string is otherwise
   silent (`fromTaskMasterName` returns `null` and `GroupedTaskList` just drops the task).
2. **`SlayerMasterNameResolutionTest`** — for every task in every data directory, assert
   each `masters` entry resolves to a non-null `SlayerMaster.fromTaskMasterName(...)`.
   Catches `"Konar"` vs `"Konar quo Maten"` class of bug across the whole corpus.
3. **Extend `LocationCoordinateServiceTest`** — assert every location string referenced in
   any task's `variantLocations` resolves to a known canonical location. There is currently
   **no such test**, which is why an orphaned location name fails silently. This will
   likely surface pre-existing orphans; triage them separately rather than bundling
   unrelated fixes into this commit.

### 4.3 In-game verification (user only — I cannot test this)

Launch the dev client:

```
./gradlew run
```

Log in per the Jagex-account instructions:
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

**Golden path**
1. Open the Slayer Simplified panel with no active task → Mortimer appears in the
   master-grouped list, last among the real masters, above Non-Slayer Enemies / Animals /
   Bosses.
2. Expand Mortimer → exactly 29 tasks, including **Venator**.
3. Settings → Preferred Master → Mortimer is selectable and the combo box does not clip.
4. With Preferred Master = Mortimer and no active task → "navigate to master" routes to
   Wyrmscraig Cavern `(2589, 8614, 0)`, not to a surface point.
5. Take a task from Mortimer → task history attributes it to **Mortimer** (this exercises
   the `"Assignment"` menu-option capture at
   [SlayerSimplifiedPlugin.java:697](src/main/java/com/slayersimplified/SlayerSimplifiedPlugin.java#L697)).

**Edge cases worth exercising**
- Open the Venator task → Locations tab shows Apsul + Virer Hunting Ground, and the
  requirement gating reads correctly on an account **without** The Blood Moon Rises.
- Wyrmscraig Cavern requirement display on an account **below 62 Sailing**.
- A Wyrm task → Wyrmscraig Cavern now listed alongside Karuulm Slayer Dungeon.
- Streak optimizer with Mortimer selected as Preferred Master → the optimizer must **not**
  recommend Mortimer for a milestone, and the reason string must not claim a points value
  for him.
- A **Vampyres** task from Duradel → panel shows it (previously Duradel was not on
  `vampyre.json`).

---

## 5. Out of scope (deliberately)

Listed so they don't get silently dropped or silently added.

- **Mortifier / task-choice UI.** Rendering the two-or-three choice prompt with modifier
  values is a real feature, not a data update. Needs its own design pass and its own
  varbit research. The §2.2 table is captured here so that work has a data source.
- **Task weight / quantity columns.** The `Task` model has no fields for these and the
  panel has nowhere to show them. Adding them touches the model, `TableTab`, and every one
  of the 122 task files.
- **Mortimer-specific streak tracking** (separate counter, 100pt skip, 2 block slots).
  Depends on varbits that are one day old and not yet documented.
- **Wyrmscraig non-Slayer content** — golem crafting, goat hunting, Mad Angel boss.
- **Bundling `venator.png`.** Wiki images are not BSD-licensed; sourcing art is a separate
  decision. The placeholder path is already handled gracefully.

---

## 6. Execution checklist

- [x] A1 `SlayerMaster.MORTIMER` added with commented `basePoints = 0`
- [x] A2 `SlayerStreakOptimizerService` javadoc notes the Mortimer exclusion
- [x] A3 / A4 verified as no-change (do not edit speculatively)
- [x] B1 `venator.json` created
- [x] B2 `_index.json` entry appended
- [x] B3 all 28 existing task files tagged `"Mortimer"` — count asserted, not eyeballed
- [x] B4 `vampyre.json` masters + variants corrected
- [x] B5 Wyrmscraig spawns added to wyrm / bat / troll / yak / bunny
- [x] C1 four locations added to `location_coordinates.json`
- [x] C2 requirements added; `FALLEN_FROM_GRACE` **omitted** with TODO in commit body
- [x] 4.2.1 `MortimerTaskCoverageTest` written and green
- [x] 4.2.2 `SlayerMasterNameResolutionTest` written and green
- [x] 4.2.3 `LocationCoordinateServiceTest` extended (pre-existing orphans ratcheted, see §7)
- [x] `./gradlew test` fully green — no skips, no ignores (57 tests)
- [x] New tests mutation-checked — each proven to go red on the regression it guards
- [ ] `./gradlew run` offered to the user; **user has confirmed §4.3 in-game**
- [x] Version bump in `build.gradle` (`2.4.26` → `2.5.0`)
- [ ] Conventional commit, no reformatting mixed in, **no Claude co-author trailer**

---

## 7. Deviations from the plan as written

Four places where execution differed from §3. All deliberate.

**7.1 Dropped the `Wyrmscraig Cavern` alias.** §C1 specified
`"aliases": ["Wyrmscraig cavern"]`. That alias is dead code:
[LocationCoordinateService.java:85](src/main/java/com/slayersimplified/services/LocationCoordinateService.java#L85)
lower-cases every canonical name *and* every alias, so a case-only alias registers a
self-mapping and resolves nothing that the canonical name did not already resolve.
Omitted rather than shipped as misleading noise.

**7.2 Fixed 5 pre-existing master-name typos.** §4.2.2's whole purpose is catching
unresolvable master strings, and the corpus scan found 4 live ones, so the test could
not have landed green without them:

| File | Was | Now |
|---|---|---|
| `tasks/kurask.json` | `"Neive"` | `"Nieve"` |
| `tasks/banshee.json` | `"Spira"` | `"Spria"` |
| `tasks/lizard.json` | `"Spira"` | `"Spria"` |
| `tasks/bear.json` | `"Spiria"` | `"Spria"` |
| `tasks/cave_slime.json` | `"Spiria"` | `"Spria"` |

Each was already paired with `Turael`/`Mazchna` in the Turael-tier pattern, so the
intended value is unambiguous. Effect: 5 tasks that were silently dropped from Spria's
and Nieve's panel groups now appear. Whether Spria *actually* assigns banshees is a
separate data-accuracy question and was not guessed at — only the typo was corrected.

**7.3 `A DEBUG TASK` is exempt from both new corpus tests.** It is filtered out of the
panel by name in `MainPanel` and deliberately carries junk (the RS3 master `"Kuradal"`,
and a location string literally reading `"Wilderness Slayer Cave - Find real Cords"`).
Asserting against a fixture designed to hold placeholders would have meant either
corrupting the fixture or weakening the test.

**7.4 Pre-existing orphaned locations are ratcheted, not fixed and not ignored.** §4.2.3
predicted this; the scan found **9 distinct** orphans across ~20 files, none of them
introduced here. Per the plan they are not fixed in this change, but the check is still
enforced today via `KNOWN_UNRESOLVABLE_TASK_LOCATIONS` plus two tests that keep the list
honest: one fails if a *new* orphan appears, the other fails if a listed entry has since
been fixed — so the baseline can never grow silently and cannot rot into a permanent
excuse.

Outstanding debt to triage separately:

```
calvar'ion's den                   enchanted valley           meiyerditch mine
north of venenatis                 spindel's den              wilderness slayer cave
stronghold of security (level 2)   wilderness slayer dungeon  wildnerness slayer cave
```

The last three are near-certainly the same place spelled three ways (note the
transposed `Wildnerness`) and should collapse to one canonical entry with aliases.

---

## 8. Edge-case hardening pass

§4.3 lists what only a human at a game client can check. Everything in it that *does not*
actually need the client was turned into an assertion instead of a request. 17 tests added
across 3 new and 3 extended classes (57 → 74). Every one was mutation-verified: the
invariant was deliberately broken, the run confirmed red with a message naming the exact
task, location or file, and the tree was restored byte-identically (SHA-256 checked).

### 8.1 §4.3 edge cases now covered by tests

| §4.3 item | Test |
|---|---|
| Wyrmscraig Cavern gated below 62 Sailing | `LocationRequirementServiceTest.wyrmscraigIsGatedOnSailing62` |
| Venator hunting grounds gated without *The Blood Moon Rises* | `…venatorHuntingGroundsAreGatedOnTheBloodMoonRises` |
| Wyrm task lists Wyrmscraig Cavern *alongside* Karuulm | `WyrmscraigUpdateDataTest.wyrmTaskListsWyrmscraigAlongsideItsExistingLocations` |
| Venator locations on the Locations tab | `…venatorSpawnsInBothVampyriumHuntingGrounds` |
| Vampyres task appears for Duradel | `…vampyreTaskListsEveryMasterThatAssignsIt` |
| Optimizer never routes a filler through Mortimer | `MortimerTaskCoverageTest.noStreakFillerMasterUsesASeparateStreakCounter` |
| The 5 typo-fixed tasks group under the intended master | `SlayerMasterNameResolutionTest.previouslyMisspelledTasksGroupUnderTheirIntendedMaster` |

Two extra invariants fell out of writing those:

- **Venator drift.** The venators exist twice in the corpus — as the `Venator` task and as
  two variants of the `Vampyre` category task. Nothing links them, so an edit to one
  silently diverges. `venatorVariantsAgreeBetweenTheVenatorAndVampyreTasks` pins them together.
- **`LocationRequirementService` had no test at all.** It now has 7, driven at
  *zero player progress* — no quests finished, no skill levels known — which is both a real
  account state and the state in which gating must be visible.

### 8.2 Requirement keys were silently overwriting each other

`LocationRequirementService` stores each requirement under `resolveCanonical(key)`, so two
keys in `requirements.json` that resolve to the same canonical location collapse to one —
last parsed wins, and *which* one that is depends on map iteration order. Three such pairs
existed:

| Key kept | Key deleted (an alias of the first) |
|---|---|
| `Meiyerditch Dungeon` | `Meiyerditch Laboratories` |
| `Lithkren` | `Lithkren Vault` |
| `Mourner Tunnels` (added; canonical) | `Path to Temple of Light (Mourner tunnels)`, `Temple of Light` |

All three pairs carried **identical** values, so nothing was visibly broken — the next edit
to either half would have been a coin flip. Deduped: 72 keys → 69, zero behaviour change,
verified by replaying the loader's resolution. `noTwoRequirementKeysCollapseToTheSameLocation`
now fails if the count of declared keys ever exceeds the number that survive loading.

### 8.3 Six location names collide with aliases on other entries

`LocationCoordinateService` loads canonical names and aliases into **one flat map** in JSON
document order using plain `put`. When name `X` is both a top-level entry and an alias of
`Y`, whichever appears later wins the coordinate lookup, and `resolveCanonical("X")` returns
`"y"` regardless of order.

Three were provably dead and were deleted — no behaviour change, proven by replaying the
loader:

- `Ice Queen's Lair` → alias `"Ice Queen's lair"` (case-only, same failure mode as §7.1)
- `Wilderness Volcano` → alias `"Wilderness volcano"` (case-only)
- `Karamja Dungeon` → alias `"Mor Ul Rek"`, already losing to `TzHaar City` two tiles away

Three are **real and need someone who knows the place** to adjudicate, so they are ratcheted
in `KNOWN_SHADOWED_LOCATION_NAMES` rather than silently repointed:

| Name | Own entry | Aliased on | Lookup actually returns | Affects |
|---|---|---|---|---|
| `Artio's Den` | (3039, 10266, 0) | `Hunter's End` | **(3115, 3677, 0)** — wrong | `Artio` boss task |
| `Grimstone Dungeon` | (2913, 4067, 0) | `Taverley Dungeon` | **(2884, 3397, 0)** — wrong | `Frost dragon` task |
| `South of Slayer Tower` | (3428, 3517, 0) | `Slayer Tower` | (3428, 3517, 0) — right, by luck of document order, but `resolveCanonical` still redirects so it would inherit the tower's access requirements |

The first two are live "navigate to location" bugs: the plugin sends the player to a
different place than the one named. The fix is one deletion each — remove the duplicate
alias, or remove the dedicated entry — but which coordinate is correct is a game-knowledge
call, and `Frost dragon` / `Grimstone Dungeon` is data of uncertain provenance in the first
place (`Frost dragon` is not an OSRS Slayer assignment). Not guessed at.

`noNewLocationNameIsShadowedByAnAlias` blocks new collisions;
`knownShadowedListHasNoStaleEntries` forces the list to shrink as they are fixed.

### 8.4 The `monsters` section of `requirements.json` has never been read

`LocationRequirementService.RequirementsFile` declares only `locations`. No other production
class reads `requirements.json`, and **no commit in this repository's history contains a
reader for the `monsters` section** — checked by grepping every commit. All 10 entries are
inert, describing real requirements the panel never surfaces (Bloodveld needs Priest in
Peril; Venator needs The Blood Moon Rises and 74 Slayer).

Left in place rather than deleted — the data is correct, and the Info tab has no
"Requirements" section to render it in, so wiring it up is a UI feature, not an edge case.
`monsterRequirementKeysNameARealTaskOrVariant` now validates the keys anyway, so the day
someone does wire it up it is a one-line change rather than a debugging session. (Variant
names are accepted because `Shadow hound` is a variant of `Dog`, not a task.)
