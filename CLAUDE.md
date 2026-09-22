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

## Code quality

The goal is code where a change to one feature cannot break another, and a bug is easy to find.
Every rule here serves that goal. When a rule and a shortcut conflict, the rule wins.

- **One behavior per change.** Do only what the task needs. Do not rename, reformat or "tidy" code
  the task does not touch. Report other problems you see; do not fix them unasked.
- **Follow the existing pattern.** Before adding a setting, puzzle, mark, theme or integration, read
  how the existing ones are built (`docs/architecture.md`, "Build and extension workflow"). A second
  way to do a thing the code already does is a defect.
- **One owner per piece of state.** Each field has one class that writes it. Other classes read it
  through that class's getter or published snapshot. Do not copy or cache state another class owns.
- **No speculative code.** No interface with one implementation, no option nobody asked for, no
  parameter every caller passes the same value, no helper "for later". Add it when the second user
  exists.
- **Duplication beats the wrong abstraction.** Share code only when the callers must change
  together. Two similar blocks that can drift apart stay separate.
- **Validate at the edge, trust inside.** Server text, the saved config and bundled resource files
  are checked where they enter, with a logged fallback. Internal code does not repeat null checks or
  wrap calls in `try`/`catch` "to be safe".
- **Delete, don't disable.** Remove dead code, retired settings and unused scaffolding. Do not
  comment code out or leave it behind a flag.
- **Comments say why.** A comment the code no longer matches is a bug; fix it in the same change.
- **Small diffs.** If a fix needs edits outside the feature it belongs to, stop and explain why
  before you continue. That spread is usually the real problem.

Before you finish, review your own diff. For each added class, method, parameter, setting and
comment, confirm the task needs it and it has a caller today. Remove what fails that test.

## Verification and reporting

- **Test logic that can run without a live game** - config parsing, swatches, room coordinates,
  solver decisions. A test asserts what the player or caller sees, not which private methods run.
  A bug fix comes with a test that fails without the fix. `src/test` does not exist yet; until it
  does, say in your report that no test covers the change.
- **Report three things separately:** what changed, what the build and tests proved, and which
  in-game checks are still open. Never describe a change as working in game before the player says
  so.
- **Keep the docs true.** When a change alters a fact stated here or in `docs/architecture.md`,
  update the doc in the same change. Stale instructions produce wrong code in the next session.
- **Git.** Commit each step that builds, with a body that says what was wrong and why this fix.
  Never commit a broken build. Do not push unless asked.

Planned work, its evidence and the next task are in `docs/modernization-plan.md`. Update its progress
table when a milestone finishes.

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
