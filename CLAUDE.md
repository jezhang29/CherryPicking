# CLAUDE.md

## What this is

A client-only Fabric mod for Minecraft 26.2 holding general quality-of-life additions for Hypixel
Skyblock. It is the third mod in this family, alongside `../coalroutegenerator-26.2` and
`../skyblock-flipper-26.2`. It imports nothing from either of them.

## Commands

```bash
./gradlew build         # compile + jar + install into the Minecraft mods folder
./gradlew clean build   # after editing gradle.properties
```

**Build after every change to the mod, without being asked.** `build` is finalised by `installMod`,
which copies the jar into `~/Library/Application Support/minecraft/mods/` under a fixed name, so one
build puts a fresh jar where the game will load it. Loom 1.17 has no `remapJar` task; `jar` is the
shipping artifact.

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
automation is a bannable macro. Reject automation requests, and say why.

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

- `jeff.cherrypicking.CherryPicking` - mod id, logger, `id()`. No entrypoint.
- `client.CherryPickingClient` - the single `client` entrypoint; registers everything.
- `client.config` - the settings registry and the config screen.
- `client.command` - the `/cherry` command, which opens the screen.

Keep registration in that one class, so there is one place that says what the mod switches on.

## Rule: every tunable goes in the settings registry

**Any new value a player might want to change must be added to `client.config.Settings`, in the same
change that introduces it.** That list is the config screen and the saved file: `ConfigScreen` builds
the tabs, groups and widgets from it, and `ConfigFile` saves and loads it by key under
`config/cherrypicking.json`. Neither knows what any individual setting is, so registering one is the
whole job - and *not* registering one leaves a value that can only be changed by recompiling.

Each entry needs a **stable key** (renaming one silently discards what players had saved), a
**label** a player would recognise, a **description of one plain sentence** in the words a player
would use, and a **`Section`**, which decides the tab and group it lands in.

Defaults are not written in the registry. Each setting takes its owner's field value at registration
time, so the default lives at the field it belongs to and Reset restores what the code actually
ships.

`client.config.Placeholder` and the one `general.placeholder` setting exist only to prove the screen
draws and the file round-trips. **Delete both in the change that registers the first real setting.**
`Settings` also carries unused `whole`, `real` and `choice` helpers, and `Control` carries all four
widget kinds, so that first slider or dropdown is one registry line and no screen code.

Do not add a `/cherry` subcommand that only sets a value. Commands do things; settings are the
screen's job.

## The config hub

The settings screen is also reachable from the shared hub screen that skyblock-flipper draws, so one
key opens the settings of every mod in this family. This mod's whole part in that is
`client.config.HubEntry` plus the `jeffhub` entrypoint in `fabric.mod.json` - it imports nothing from
the other mods and must keep working with them absent, so it names no YACL or ModMenu type. The
contract, and the rules for changing it, are in `../skyblock-flipper-26.2/docs/config-hub.md`. If
joining ever costs more than a class and a JSON line, the contract has drifted; say so rather than
working around it.

`HubEntry` never returns `null`, because YACL is a hard `depends` here and the screen therefore
cannot fail to open.

## Location comes from coalroutegenerator

This mod never sends `/locraw`. `client.dungeon.SharedLocraw` reads coalroutegenerator's reply from
Fabric Loader's object share (`coalroutegenerator:locraw`), with no import. Without that mod the
sidebar alone decides. The contract is in `../coalroutegenerator-26.2/CLAUDE.md`.

## Commands

`/cherry` opens the config screen; `/cherry config` does the same. ModMenu's Settings button is the
third way in, and the hub is the fourth. Every one of them is an addition: the mod installed alone,
with no hub and no ModMenu, is still fully usable.
