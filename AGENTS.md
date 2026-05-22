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


## Tool use
## 1. The MCP stack at a glance

All MCP servers in this list live in Docker on the home server
(`192.168.86.186`, see [mcp-stack/](mcp-stack/docker-compose.yml)). Your
client connects to them through `mcp-compressor`, which shrinks each
backend's tool schema (often 90 %+) so the LLM context stays cheap.

| Tool family    | When to use                                            | Compression |
| -------------- | ------------------------------------------------------ | ----------- |
| `github`       | Issues, PRs, code search, releases, repo metadata       | high        |
| `gitnexus`     | Cross-repo code intelligence; "where is X defined / called?" | high  |
| `context-mode` | Strict-fetch web reads with provenance                  | medium      |
| `context7`     | Pulling up-to-date library/API documentation             | high        |
| `playwright`   | Driving a real browser (forms, screenshots, scraping)    | high        |
| `mem0`         | Long-term memory between sessions                        | medium      |
| `think`        | Structured reasoning (`think`, `plan`, `criticize`)       | medium      |

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
Before writing code, call `think.invoke_tool("think", {"thought": "..."})`
to outline the approach. For multi-step problems, use `plan`; to stress-
test a draft, use `criticize`. Cheap, no side effects, and the trace is
visible to the user.

### 3.2 `context7` — current library docs
When the user asks about an external package, framework, or API,
**call `context7` before answering from memory**. Training data ages
fast; `context7` is live. Example: "How do I use the new Tailwind v4
config?" → `context7.invoke_tool("resolve-library-id", ...)` then
`context7.invoke_tool("get-library-docs", ...)`.

### 3.3 `github` — GitHub state of the world
Issues, PRs, releases, code search across public repos, branch protection,
workflow runs. Prefer this over the `gh` CLI inside scripts because the
results come back as structured JSON and the auth is already attached.

### 3.4 `gitnexus` — semantic code intelligence over local repos
Indexes everything under `GITNEXUS_WORKSPACE` (set in
[mcp-stack/.env](mcp-stack/.env.example)). Use it to:
- Find every caller of a function across repos
- List symbols defined in a directory
- Build a dependency-aware view of a refactor

If the answer requires structural code understanding rather than a
keyword grep, this is the right tool.

### 3.5 `playwright` — only when a real browser is needed
Page interaction, login flows, screenshots, dynamic-JS scraping. Costs
real CPU and a browser context — do **not** use it for static pages
(`fetch` / `curl` is fine for those). Always close pages you opened.

### 3.6 `mem0` — durable memory across sessions
- `add_memory` after the user shares a long-lived fact ("I always use
  pnpm, never npm", "deploy target is Cloudflare Workers").
- `search_memory` at the start of a new session to recall preferences.
- Tag memories with a `user_id` so households share the same backend
  but get separate stores.

### 3.7 `context-mode` — strict, provenance-aware fetch
Use when you need a web fetch that refuses to follow surprise redirects
and keeps a verifiable trail. Slower than `playwright` for static text
but gives a citation block you can paste back to the user.

---
