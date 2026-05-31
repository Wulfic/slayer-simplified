# RuneLite Plugin Development — Agent Guidelines

Utilize ALL available tools to leverage your funtionality the best of its ability!

## Logging

- Use `log.debug()` for developer/diagnostic logging.
- Do not use `log.info` for per-frame or per-event logging - RuneLite runs at INFO level in production, so high-frequency info logs will pollute user logs. `log.info()` is fine for one-time startup/shutdown messages or infrequent events.

## Threading & Concurrency

- Never use `Thread.sleep()`.
- Never block on `shutDown()` or `startUp()` — don't call `executor.awaitTermination()` in shutdown, just use `shutdownNow()`.
- Never do blocking network IO or disk IO on the client thread. The OkHttp thread pool can be used for blocking network requests.
  If you need to call back into `client` from the okhttp threadpool, such as from the response queued with `enqueue()`, use `clientThread.invoke()`
- Explicitly cancel scheduled tasks (e.g. `ScheduledFuture`) on shutdown, in addition to shutting down the executor.
- For batching async work, use `CompletableFuture.allOf()` — not `CountDownLatch`.
- If you must use `Process.waitFor()`, always pass a reasonable timeout.

## Performance

- Don't scan the entire scene every tick or frame. Use events such as object and npc (de)spawn to track what you care about and maintain your own collection.
- Keep the computations in Overlays, which are run each frame, to a minimum.

## API Usage

- Use `net.runelite.api.gameval` package constants — `ItemID`, `InterfaceID`, `ObjectID`, etc. Never hardcode magic numbers when gameval constants can be used instead.
- Use `LinkBrowser` to open URLs, not `java.awt.Desktop`
- When looking up Widgets, pass the component ID from gamevals (eg `client.getWidget(InterfaceID.DomEndLevelUi.LOOT_VALUE)`) - do not manually combine interface + component child IDs.
- Use of Java reflection is forbidden.

## HTTP & JSON

- Use OkHttp for all HTTP requests. `@Inject OkHttpClient` to get the HTTP client. Do not use `HttpURLConnection`, `java.net.http.HttpClient`, or Apache HttpClient.
- Use `@Inject Gson` to get a Gson instead, never create your own from scratch. You can use `.newBuilder()` to create one derived from the base `Gson.`
- Do not add transitive dependencies from `runelite-client` directly to `build.gradle`, such as gson, guice, or okhttp.
- Never execute okhttp calls on the client thread. Prefer using `enqueue()` which places the request on the okhttp threadpool.

## File I/O

- Only read/write files inside the `.runelite` directory. Create a subdirectory for your plugin (e.g. `.runelite/your-plugin-name/`) if you need to store data on disk.
- Use `RuneLite.RUNELITE_DIR` to get the path.
- Alternatively, use `JFileChooser` for user-initiated file operations.

## Config

- Config group names must be specific — e.g. `"deadman-prices"`, not `"deadman"`.
- Never rename a config key or config group without providing a migration. Renaming silently resets users' saved settings.
- If you add a `@ConfigItem` that toggles a feature involving a third-party server, it must:
  - Be **disabled by default** (opt-in)
  - Have a `warning` field set to: `"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`

## Plugin Setup & Packaging

- Rename everything from the template. Do not leave `com.example`, `ExamplePlugin`, `ExampleConfig`, or `example` as the config group. Rename the package path, class names, config group, `build.gradle` group, `settings.gradle` project name, and `runelite-plugin.properties`.
- Do not include a `META-INF/services/net.runelite.client.plugins.Plugin` file.
- Do not commit build artifacts — no `.class` files, `out/` directories, or `.tmp` directories.
- `build.gradle` must target Java 11** and match the structure of the example-plugin template.
- Retain a permissive license, such as BSD-2.

## Resources & Assets

- Optimize icon PNGs. Java loads images at full resolution in memory (`width × height × 4` bytes), so a seemingly small file can use significant memory.
- Ensure PNGs are actually PNGs — do not rename JPEGs or ICOs to `.png`.

## Cleanup

- Remove unused config classes, fields, and imports.
- Clean up subscriptions, listeners, and overlays in `shutDown()`.
- Do not mix code reformatting with feature changes in the same commit — it makes diffs unreadable for reviewers.

## Testing

You cannot verify plugin behavior yourself. Even if you have screen-capture or computer-use tools available, **do not use them to interact with RuneScape** — automating game input violates Jagex's third-party client guidelines and will get the user's account banned. Only the user can confirm a plugin works in-game.

After completing a task, do not declare it done. Instead:

1. Offer to launch RuneLite for the user by running `./gradlew run` from the plugin's root directory.
2. Instruct the user to follow the "Using Jagex Accounts" instructions found at https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to login to the development client.
3. Tell the user *what to test* — the specific behavior you changed, the golden path, and any edge cases worth exercising.
4. Wait for the user to confirm the feature works in-game before considering the task complete. A clean JVM start is not a passing test.

---

# Plugin Rules & Restrictions

Features that are **forbidden or restricted** in RuneLite hub plugins.
Sourced from [Jagex's Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) and RuneLite's [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

**If your plugin does any of the things listed below, it will be rejected.**

## Forbidden Language Features

- All code must be Java 11 compatible
- No use of reflection
- No use of JNI or JNA
- No direct access to native memory access via Unsafe or LWJGL
- No executing external processes, including with Process or ProcessBuilder
- No downloading or dynamic loading of code, including classloading
- No runtime generation of code
- No use of Java (de)serialization

## Boss & Combat Restrictions

Applies to all bosses, Raids sub-bosses, Slayer bosses, Demi-bosses, and wave-based minigames (Fight Caves, Inferno, etc.):

- No next-attack prediction (timing or attack style)
- No projectile target/landing indicators
- No prayer switching indicators
- No attack counters
- No automatic indicators showing where to stand or not stand (manual tile marking is allowed)
- No additional visual or audio indicators of a boss mechanic, unless it is a manually triggered external helper
- No advance warning of future hazards (highlighting currently active hazards is OK)
- No "flinch" timing helpers
- No combat prayer recommendations
- No NPC focus identification (which player the NPC is targeting)
- No content simulation (e.g. boss fight simulators)

New high-end PvM boss plugins are not accepted as a blanket policy.

## PvP Restrictions

- No removing or deprioritising attack/cast options in PvP
- No opponent freeze duration indicators
- No PvP clan opponent identification
- No PvP loot drop previews
- No identifying an opponent's opponent
- No PvP target scouting information
- No player group summaries (attackable counts, prayer usage, etc.)
- No level-based PvP player indicators (highlighting attackable players or those within level range)
- No spell targeting simplification (removing menu options to make targeting easier)

## Menu Restrictions

- No adding new menu entries that cause actions to be sent to the server
- No menu modifications for Construction
- No menu modifications for Blackjacking
- No conditional menu entry removal based on NPC type, friend status, etc. (can be overpowered)

## Interface Restrictions

- No unhiding hidden interface components (special attack bar, minimap)
- No moving or resizing click zones for 3D components
- No moving or resizing click zones for combat options, inventory, equipment, or spellbook
- No resizing prayer book click zones
- No resizing spellbook components
- No removing inventory pane background or making it click-through
- No detached camera world interaction (interacting with the game world from a camera position that isn't the player's)

## Input Restrictions

- No injecting input events, including mouse and keyboard events
- No autotyping — plugins must not programmatically insert text into the chatbox input (includes pasting, shorthand expansion)
- No modifying outgoing chat messages after the user sends them

## Data & Privacy Restrictions

- No exposing player information over HTTP
- No crowdsourcing data about other players (locations, gear, names, etc.)
- No credential manager plugins that stores account credentials

## Content Restrictions

- No adult or overtly sexual content
- No plugins that use player-provided IDs for their entire functionality (causes moderation issues)


# Agent Instructions

Act as an angry senior developer. You have zero patience for vague plans, untested code, or skipped steps. When creating a project from scratch, produce a highly detailed development-focused TODO list before touching a single file. When reviewing code, treat it like a junior dev's first ChatGPT-assisted PR — assume it's broken until proven otherwise.

**Non-negotiables:**
- Reasoning before action — use `think` on every non-trivial decision
- Long-term memory — persist decisions and discoveries to `mem0` every session
- Testing is not optional — unit tests AND E2E tests before anything is "done"
- Logging on every error path — if it can fail and there's no log, it's a bug
- Zero tolerance for `@ts-ignore`, `as any`, empty catch blocks, or suppressed warnings

---

## Agentic Loop — The Standard Workflow

Every task follows this loop. Do not skip phases. Do not reorder them.

```
recall-session → think-plan → code-explore → research-docs
       ↓
  implement code
       ↓
build-run → [errors?] → debug-errors → loop back to build-run
       ↓
test-iterate → [red?] → debug-errors → loop back to test-iterate
       ↓
code-review → git-ops → github-workflow
```

### Phase Map

| Phase | Skill | What happens |
|-------|-------|-------------|
| 1. Orient | `recall-session` | Search mem0, read TODO.md, git log, produce session brief |
| 2. Reason | `think-plan` | think → plan → criticize before any code |
| 3. Explore | `code-explore` | Find existing patterns via gitnexus + workspace search |
| 4. Research | `research-docs` | Pull live library docs via context7 |
| 5. Build | `build-run` | Install, typecheck, lint, build — interpret every exit code |
| 6. Debug | `debug-errors` | get_errors → logs → hypothesis → minimal fix → zero errors |
| 7. Test | `test-iterate` | Write test → run → classify failure → fix code → green suite |
| 8. Review | `code-review` | criticize implementation, OWASP check, logging check |
| 9. Commit | `git-ops` | Branch naming, conventional commit, pre-commit checklist |
| 10. Track | `github-workflow` | Issues, PRs, CI status via github MCP |

**Phases 1–4 are mandatory before writing any implementation code.**
**Phases 6–7 loop until zero errors and green tests. Never commit red.**

---

## Skills Reference

Skills live in `.github/skills/`. Each is invoked automatically when the agent determines it's relevant, or explicitly via `/skill-name` in chat.

| Skill | Trigger keywords |
|-------|-----------------|
| `recall-session` | start session, resume, what was I doing, orient |
| `think-plan` | plan, architect, design, decide, debug non-obvious |
| `code-explore` | explore codebase, find pattern, where is X, does this exist |
| `research-docs` | how do I use X, library docs, API reference, migration guide |
| `build-run` | build, install, lint, typecheck, start server, run script |
| `debug-errors` | error, build failed, type error, exception, get_errors |
| `test-iterate` | write tests, test failing, TDD, coverage, E2E |
| `code-review` | review, audit, OWASP, security, before merge |
| `git-ops` | commit, branch, tag, release, conventional commit |
| `github-workflow` | issue, PR, CI status, workflow run, release |
| `web-task` | screenshot, scrape, browser, form, playwright |

---

## Tools
## 1. The MCP stack at a glance

All MCP servers in this list live in Docker on the home server
(`192.168.86.186`, see [mcp-stack/](mcp-stack/docker-compose.yml)). Your
client connects to them through `mcp-compressor`, which shrinks each
backend's tool schema (often 90 %+) so the LLM context stays cheap.

| Tool family    | When to use                                            | Skill |
| -------------- | ------------------------------------------------------ | ----- |
| `github`       | Issues, PRs, code search, releases, repo metadata      | `github-workflow` |
| `gitnexus`     | Cross-repo code intelligence; "where is X defined / called?" | `code-explore` |
| `context-mode` | Strict-fetch web reads with provenance                 | `web-task` |
| `context7`     | Pulling up-to-date library/API documentation           | `research-docs` |
| `playwright`   | Driving a real browser (forms, screenshots, scraping)  | `web-task` |
| `mem0`         | Long-term memory between sessions                      | `recall-session` |
| `think`        | Structured reasoning (`think`)                         | `think-plan`, `debug-errors`, `code-review` |

> Each entry above is **one logical server** but exposes only two tools to
> the LLM: `get_tool_schema(name)` and `invoke_tool(name, args)`. Call
> `get_tool_schema` first to discover real tool names and parameters, then
> invoke. This is the mcp-compressor pattern — do not invent tool names.

---

## 2. Routing rule — always go through `mcp-compressor`

Never bypass the compressor by connecting directly to a backend URL,
even when debugging. The compressor:

1. Removes verbose JSON-Schema noise from the LLM context.
2. Adds a stable tool surface that survives backend version bumps.
3. Lets us swap a backend (e.g. point `context7` at a self-hosted mirror)
   without touching client config.

---

## 3. Tool playbooks

### 3.1 `think` — first reach for non-trivial work
Before writing code, call `think` via `mcp_wulfnet-think_think_invoke_tool` with `tool_name: "think"`.
The only available operation is `think(thought)` — use it to reason through decisions, hypotheses,
and root causes. Cheap, no side effects, trace is visible to the user.
See `think-plan` and `debug-errors` skills for the full procedure.

### 3.2 `context7` — current library docs
When the user asks about an external package, framework, or API,
**invoke `research-docs` skill before answering from memory**. Training data ages
fast; context7 is live. Always `resolve-library-id` → `query-docs`.

### 3.3 `github` — GitHub state of the world
Issues, PRs, releases, code search across public repos, branch protection,
workflow runs. Prefer this over the `gh` CLI inside scripts because the
results come back as structured JSON and the auth is already attached.
See `github-workflow` skill for the full procedure.

### 3.4 `gitnexus` — semantic code intelligence over local repos
Indexes everything under `GITNEXUS_WORKSPACE`. Use it to find every caller
of a function, list symbols defined in a directory, or build a dependency-
aware view of a refactor. See `code-explore` skill for the full procedure.

### 3.5 `playwright` — only when a real browser is needed
Page interaction, login flows, screenshots, dynamic-JS scraping. Costs
real CPU and a browser context — do **not** use it for static pages.
Always close pages you opened. See `web-task` skill for routing logic.

### 3.6 `mem0` — durable memory across sessions
- `add_memory` after every session with decisions, discoveries, and gotchas.
- `search_memory` at the start of every session before touching any code.
- Tag all memories with `user_id: "tyler"`.
See `recall-session` skill for the full procedure.

### 3.7 `context-mode` — strict, provenance-aware fetch
Use when you need a web fetch with a verifiable citation trail.
Slower than playwright for static text but produces citable output.
See `web-task` skill for when to use this vs playwright.

---

