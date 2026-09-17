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

Keep registration in that one class, so there is one place that says what the mod switches on.

## Rule: every tunable goes in the settings registry

This mod has no settings registry yet. When the first player-facing tunable arrives, copy the shape
used in `../coalroutegenerator-26.2/src/main/java/jeff/coalroutegenerator/client/config` -
`Setting`, `Section`, `Control`, `Settings`, `ConfigFile`, `ConfigScreen` - where the screen is a
projection of the registry and no setting has per-setting screen code. **Any new value a player
might want to change goes in that registry in the same change that introduces it.** Defaults are not
written in the registry; each setting takes its owner's field value at registration time, so Reset
restores what the code actually ships.

## The config hub

Once this mod has a config screen, it joins the shared hub that skyblock-flipper draws, so one key
opens the settings of every mod in this family. The whole cost of joining is a
`client.config.HubEntry` class plus a `jeffhub` entrypoint line in `fabric.mod.json` - it imports
nothing from the other mods and must keep working with them absent. The contract, and the rules for
changing it, are in `../skyblock-flipper-26.2/docs/config-hub.md`. If joining ever costs more than
that, the contract has drifted; say so rather than working around it.
