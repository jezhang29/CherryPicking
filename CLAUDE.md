# CLAUDE.md

## What this is

A client-only Fabric mod for Minecraft 26.2 holding general quality-of-life additions for Hypixel
Skyblock. It is the third mod in this family, alongside `../coalroutegenerator-26.2` and
`../skyblock-flipper-26.2`. It imports nothing from either of them.

`AGENTS.md` is a symlink to this file, so Codex and Claude Code read the same rules. Edit this file,
never the link.

## Commands

```bash
./gradlew build                 # compile + jar + install into the Minecraft mods folder
./gradlew build -x installMod   # verify without touching the live mods folder
./gradlew clean build           # after editing gradle.properties
```

**Build after every change to the mod, without being asked.** `build` is finalised by `installMod`,
which copies the jar into `~/Library/Application Support/minecraft/mods/` as
`cherrypicking-<version>.jar`. The name carries the version, so a version bump leaves the old jar
beside the new one; say so when you bump it. Loom 1.17 has no `remapJar` task; `jar` is the shipping
artifact.

**There is no dev client.** `./gradlew runClient` is not used - don't suggest it, and don't describe
testing in terms of it. `run/` is leftover scaffolding. The mod is tested by launching the real
client and joining Hypixel, which is the user's job. Live state lives under
`~/Library/Application Support/minecraft/`, not under `run/`.

**In-game checks live in `docs/in-game-checks.md`.** Read it before every review: a `FAIL` or
`PARTLY` verdict there is a confirmed bug. Add the review's in-game checks to it, in the format and
by the rules at the top of that file. Never edit the player's verdicts or notes.

## Hard constraint: display-only

The mod may show, highlight, and suggest. **It must never move the player, click, mine, or
synthesize input.** Detection, rendering, and suggestion are accepted QOL on Hypixel; input
automation is a bannable macro. Reject automation requests, and say why. Messages go to the
player's own chat through `client.dungeon.Chat`; the mod never sends chat or commands to the server.

## Code quality and verification

The general rules - one behavior per change, one owner per piece of state, no speculative code,
self-review of the diff, tests, reporting, git - are global, in `~/.claude/CLAUDE.md`
(`~/.codex/AGENTS.md` for Codex). This section adds only what is specific to this mod.

- **Existing patterns to follow** for a setting, puzzle, mark, theme or integration are in
  `docs/architecture.md`, "Build and extension workflow".
- **The edges** where input is validated are server text (sidebar, nametags), the shared locraw
  reply, the saved config and the bundled resource files under `assets/cherrypicking/`.
- **Testable without the game:** config parsing, swatches, room coordinates and solver decisions.
  `src/test` does not exist yet; until it does, say in your report that no test covers the change.
- **Manual checks** are the in-game checks above. Never describe a change as working in game before
  the player marks it `PASS`.
- **Docs to keep true:** this file and `docs/architecture.md`. Planned work, its evidence and the
  next task are in `docs/modernization-plan.md`; update its progress table when a milestone
  finishes.

## Target versions

Versions are pinned in `gradle.properties`, and the Java toolchain in `build.gradle`.

**26.2 mapping names differ from tutorials and older Fabric code** - e.g.
`net.minecraft.resources.Identifier`, `net.minecraft.network.chat.Component`,
`net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper` (not `keybinding.v1`),
`Minecraft.getInstance().setScreenAndShow(...)`. Copy imports from the sibling mods; if one can't be
confirmed there, verify against the deobfuscated jar before writing code around it:

```bash
javap -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar <class>
```

## Architecture

The full map, with each component's limits, is `docs/architecture.md`. In short:

- `jeff.cherrypicking.CherryPicking` - mod id, logger, `id()`. No entrypoint.
- `client.CherryPickingClient` - the single `client` entrypoint. Loads the config first, then
  registers the screen opener, the command and `Dungeons.register()`.
- `client.dungeon.Dungeons` - registers every dungeon tick callback, in the order the features
  depend on: `DungeonState`, `RoomWatch`, `Puzzles`, `Livid`, `StarMobWatch`. Then the puzzle
  signatures, the two renderers and the Livid title.
- `client.config` - the settings registry, the saved file, and the doors into the screen.
- `client.screen`, `client.theme` - the mod's own settings screen and its colour themes.
- `client.command` - the `/cherry` command.
- `client.CleanExit` + `mixin.ShutdownWatchdogMixin` - ends the JVM after a normal quit.
- `CherryPickingDataGenerator` - empty scaffolding.

Top-level registration stays in `CherryPickingClient`; dungeon registration stays in `Dungeons`.
Nothing registers itself, so those two classes say everything the mod switches on.

## Rule: every tunable goes in the settings registry

**Any new value a player might want to change must be added to `client.config.Settings`, in the same
change that introduces it.** That list is the config screen and the saved file: `SettingsScreen`
builds the tabs, cards and widgets from it, and `ConfigFile` saves and loads it by key under
`config/cherrypicking.json`. Neither knows what any individual setting is, so registering one is the
whole job - and *not* registering one leaves a value that can only be changed by recompiling.

Each entry needs a **stable key** (renaming one silently discards what players had saved), a
**label** a player would recognise, a **description of one plain sentence** in the words a player
would use, and a **`Section`**, which decides the tab and group it lands in. `Control` has five
kinds - flag, whole, real, choice, colour - so a new setting is one registry line and no screen code.
Buttons such as reset are `Action` entries: they show in the screen and are never saved.

Choice settings save the **enum constant's name**. Renaming a constant discards saved values the same
way renaming a key does.

Defaults are not written in the registry. Each setting takes its owner's field value at registration
time, so the default lives at the field it belongs to and Reset restores what the code actually
ships.

Do not add a `/cherry` subcommand that only sets a value. Commands do things; settings are the
screen's job.

## The config hub

The settings screen is also reachable from the shared hub screen that skyblock-flipper draws, so one
key opens the settings of every mod in this family. This mod's whole part in that is
`client.config.HubEntry` plus the `jeffhub` entrypoint in `fabric.mod.json` - it imports nothing from
the other mods and must keep working with them absent, so it names no ModMenu type. The contract,
and the rules for changing it, are in `../skyblock-flipper-26.2/docs/config-hub.md`. If joining ever
costs more than a class and a JSON line, the contract has drifted; say so rather than working
around it.

`HubEntry` never returns `null`: the screen is this mod's own, with no optional library behind it,
so it cannot fail to open.

## Location comes from coalroutegenerator

This mod never sends `/locraw`. `client.dungeon.SharedLocraw` reads coalroutegenerator's reply from
Fabric Loader's object share (`coalroutegenerator:locraw`), with no import. Without that mod the
sidebar alone decides. The contract is in `../coalroutegenerator-26.2/CLAUDE.md`.

## Ways into the screen

`/cherry` opens the config screen; `/cherry config` does the same. ModMenu's Settings button is the
third way in, and the hub is the fourth. All four call `ConfigScreen.build`. Every one of them is an
addition: the mod installed alone, with no hub and no ModMenu, is still fully usable.
