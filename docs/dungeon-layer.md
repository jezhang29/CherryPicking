# CherryPicking — Dungeon layer, themed config screen

Design document. Detailed enough to implement from cold, in stages, by someone who has not read the
conversation it came out of.

**This file is the source of truth for the work.** Edit it rather than arguing with it: the build
order in §15 is meant to be re-cut, the settings list in §13 is meant to be pruned, and every
constant in §3 carries where it came from so it can be checked. §14 lists what needs testing in the
real client, because there is no dev client.

Written 2026-09-17 against Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.160.0+26.2.

---

## 1. What is being built and why

CherryPicking has a working config core (registry, screen, file, hub entry, `/cherry`) and **no
features**. This change gives it three feature families, a theme, and a new config screen.

**Features**

| Feature | From | What it does |
|---|---|---|
| Water Board solver | [Odin](https://github.com/odtheking/Odin) | Names the lever to flick and counts down to the next one |
| Blaze solver | Odin | Orders the ten blazes and marks the next one |
| Creeper Beams solver | Odin | Pairs the sea lanterns and draws the beam between each pair |
| Teleport Maze solver | Odin | Marks the pads that can still be reached, and the ones already used |
| Boulder solver | Odin | Marks the boulder to push and where to push it from |
| Livid solver | Odin + [Devonian](https://github.com/Synnerz/devonian) | Boxes the real Livid in F5/M5, and times its invulnerability |
| Starred mob boxes | Devonian | Boxes the starred mobs that clear a room |

**Theme and screen**

The config screen becomes CherryPicking's own screen, drawn in **Catppuccin**, defaulting to the
**Latte** flavour, which is what this machine's IntelliJ IDEA 2026.2 and VS Code Insiders are both
set to:

- `~/Library/Application Support/JetBrains/IntelliJIdea2026.2/options/laf.xml` →
  `com.github.catppuccin.latte.jetbrains`
- `.../options/colors.scheme.xml` → `Catppuccin Latte`
- `~/Library/Application Support/Code - Insiders/User/settings.json` →
  `"workbench.colorTheme": "Catppuccin Latte"`

The screen's behaviour is modelled on Meteor Client and NotEnoughUpdates: a left rail of tabs, a
**multi-column grid of collapsible cards**, and a search box that filters every tab at once. The
point is to stop scrolling. A single-column list of sixty settings is four screens tall; the same
sixty settings in three columns of folding cards is one screen.

**The shape that matters more than the features.** A new solver should be one data file and one
class, and its settings a few registry lines. Two existing ideas are extended to make that true:
the settings registry already means a new setting needs no screen code, and a new *mark* kind means
a new solver needs no render code.

---

## 2. Non-negotiables

Carried from `CLAUDE.md`. Any of these broken is a bug, not a trade-off.

1. **Display-only.** The mod shows, highlights and suggests. It never moves the player, clicks,
   mines, shoots or synthesises input. See §12.8 for the one place this shapes a feature.
2. **Every tunable goes in `client.config.Settings`, in the same change that introduces it.** A
   value that can only be changed by recompiling is a defect.
3. **Stable setting keys.** Renaming a key silently discards what players had saved.
4. **Defaults live at the field**, never in the registry. The registry captures the owner's value at
   registration time, so Reset restores what the code ships.
5. **One sentence of plain description per setting**, in the words a player would use.
6. **Registration stays in `CherryPickingClient`**, so one file says what the mod switches on.
7. **`HubEntry` imports nothing from the sibling mods and never returns `null`.** It must stay
   loadable with every optional dependency absent, so it names no screen-library type.
8. **No `/cherry` subcommand that only sets a value.** Commands do things; settings are the screen's
   job.
9. **Build after every change**: `./gradlew build`. There is no dev client; the user tests by
   launching the real client and joining Hypixel.
10. **Delete `client.config.Placeholder` and the `general.placeholder` setting** in the change that
    registers the first real setting.

---

## 3. Upstream research: the facts this design rests on

Read from Odin's and Devonian's sources, not from memory. Constants marked **unverified** need an
in-game check; §14 lists them all in one place.

### 3.1 Dungeon room geometry

- Rooms sit on a **32-block grid**. Map tile `t` has its centre at `t * 32 - 185`.
  (`DungeonRoom.getRealPosition`, `WorldScan`)
- A 1x1 room spans **centre ±15**, so it is 31 blocks across with a one-block seam between rooms.
- The tile the player stands in is `(blockX + 201) >> 5`, valid in `0..5`. (`WorldScan`)
- A room's coordinate frame is **an anchor corner plus one of four rotations**:
  `real(rel) = rotate(rel, rot) + (anchor.x, 0, anchor.z)`, with `y` left absolute.
  (`DungeonRoom.getRealCoords`)
- Anchor corner offsets from the centre: `NORTH (+15,+15)`, `SOUTH (−15,−15)`, `WEST (+15,−15)`,
  `EAST (−15,+15)`. (`RoomRotation`)
- The rotation itself (`VecUtils.rotateAroundNorth`), for a relative `(x, y, z)`:

  | Rotation | → |
  |---|---|
  | `NORTH` | `(−x, y, −z)` |
  | `SOUTH` | `(x, y, z)` |
  | `WEST`  | `(−z, y, x)` |
  | `EAST`  | `(z, y, −x)` |

  The inverse (`rotateToNorth`) swaps the `WEST` and `EAST` rows.
- **Rooms larger than 1x1 span several tiles.** Between two separate rooms the one-block seam
  (`centre + 16`) is empty except at the door; inside one room it is floor and wall along its whole
  length. `RoomWatch` joins two tiles when the seam column holds a block between y 12 and 140, at 8 either side of
  its middle (the heightmap is not used: testing it joined every gap, so the whole dungeon read as
  one room),
  flood-fills from the player's tile, and outlines the result as one shape. Unverified — watch it
  with `developer.drawRoomFrame` on a 1x2, an L and a 2x2.
- **Every one of the five puzzle rooms is 1x1**: every relative coordinate the five Odin solvers use
  falls inside `0..31`.

### 3.2 What Odin does that we deliberately do not

Odin reaches all of the above through a full dungeon-map subsystem: it reads the map item, scans
every room's centre column into a string hash, looks that hash up in a bundled `rooms.json` of every
room in the game, and probes for a blue-terracotta marker block at the room's highest block to find
the rotation — all to learn the room's **name**.

We do not need names. See §11.2: the room frame comes from the player's position, and the puzzle
identifies *itself* by probing its own signature blocks. That removes the map item, the column
hashes, `rooms.json`, the highest-block scan and the terracotta marker.

### 3.3 Water Board

`WaterSolver.kt`. Relative positions, `y` absolute:

- **Wool slots** — three of five are extended in any given run. The identifier is the concatenated
  ordinals of the extended ones, e.g. `"013"`:

  | Ordinal | Colour | Relative pos | Extended when |
  |---|---|---|---|
  | 0 | purple | `(15, 56, 19)` | the block is **not** air |
  | 1 | orange | `(15, 56, 18)` | ” |
  | 2 | blue | `(15, 56, 17)` | ” |
  | 3 | green | `(15, 56, 16)` | ” |
  | 4 | red | `(15, 56, 15)` | ” |

- **Pattern identifier** — first match wins:

  | Id | Probe | Expected block |
  |---|---|---|
  | 0 | `(14, 77, 27)` | `TERRACOTTA` (plain, not dyed) |
  | 1 | `(16, 78, 27)` | `EMERALD_BLOCK` |
  | 2 | `(14, 78, 27)` | `DIAMOND_BLOCK` |
  | 3 | `(14, 78, 27)` | `QUARTZ_BLOCK` |

  No match means the puzzle was already started; say so and stand down.

- **Levers**, each named by the block beside it:

  | Name | Relative pos | JSON key |
  |---|---|---|
  | coal | `(20, 61, 10)` | `coal_block` |
  | gold | `(20, 61, 15)` | `gold_block` |
  | quartz | `(20, 61, 20)` | `quartz_block` |
  | diamond | `(10, 61, 20)` | `diamond_block` |
  | emerald | `(10, 61, 15)` | `emerald_block` |
  | clay | `(10, 61, 10)` | `hardened_clay` |
  | water | `(15, 60, 5)` | `water` |

- **Solution lookup**: `water-solutions.json` is
  `{ "false"|"true" : { "0".."3" : { "013" : { "gold_block": [0.0, 11.0], ... } } } }`.
  The outer key is the "optimised" flag. Each value is a list of **seconds after the water lever was
  opened** at which that lever should be flicked. `0.0` means "flick it now, before the water".
- **Completion**: the block at `(15, 56, 22)` changing to `CHERRY_LOG`.

### 3.4 Blaze

`BlazeSolver.kt`, plus the [Hypixel wiki](https://hypixelskyblock.minecraft.wiki/w/Blaze_(Higher_or_Lower)).

- Ten blazes, each with an armour stand whose name matches
  `^\[Lv+\d+]  Blaze [\d,]+/([\d,]+)❤$` — note the **two spaces** after the level bracket. Group 1
  is max health, with thousands commas.
- Order is by max health. **Which direction is decided by the chest's starting position on the
  iron-bar chain**: chest at the bottom → lowest health first; chest at the top → highest health
  first. *(Wiki, unverified in code.)*
- Odin instead reads the room name (`"Lower Blaze"` / `"Higher Blaze"`) out of `rooms.json`. We use
  the chest, because it needs no room name. §14.1.
- A blaze is done when its entity leaves the level. The puzzle is done when the last one goes.

### 3.5 Creeper Beams

`BeamsSolver.kt` and `creeper-beams-solutions.json`: a flat list of 13 six-integer rows,
`[x1,y1,z1, x2,y2,z2]`, each a candidate pair of relative positions. A pair is live when **both**
ends currently hold `SEA_LANTERN`. Ends flip between `PRISMARINE` and `SEA_LANTERN` as the puzzle is
solved, so the pairs are re-probed. Completion: `(15, 69, 15)` going from air to `CHEST`.

Note row 9 (`[18,81,21, 9,69,3]`) appears twice in the upstream file. Harmless; a set keyed on the
first position de-duplicates it.

### 3.6 Teleport Maze

`TPMazeSolver.kt`. Thirty end-portal-frame pads, all at relative `y = 69`, in **groups of four** by
list order — the grouping is what makes "which pads can I still reach" answerable:

```
( 4,12) ( 4, 6) (10,12) (10, 6)     group 0
( 4,20) ( 4,14) (10,20) (10,14)     group 1
( 4,28) ( 4,22) (10,28) (10,22)     group 2
(12,28) (12,22) (18,28) (18,22)     group 3
(20,28) (20,22) (26,28) (26,22)     group 4
(26,20) (26,14) (20,20) (20,14)     group 5
(26,12) (26, 6) (20,12) (20, 6)     group 6
(15,14) (15,12)                     the exit pair, indices 28 and 29
```

A teleport is detected by the player's position snapping to `y = 69.5` with `x` and `z` both exact
multiples of `0.5`. On each teleport: add every pad the player now overlaps to the visited set,
narrow the reachable set to pads still in line of sight within 32 blocks, then pick the best next
pad from the current pad's group of four — preferring one that is still reachable, else the one
closest to where the player is already looking.

### 3.7 Boulder

`BoulderSolver.kt` and `boulder-solutions.json`.

- **Key**: a 42-character string of `0`/`1`, built by walking `z` from 24 down to 9 step 3 (six
  values) and, inside that, `x` from 24 down to 6 step 3 (seven values), writing `0` when
  `(x, 66, z)` is air and `1` when it is not.
- **Value**: a list of `[renderX, renderZ, clickX, clickZ]`, all at relative `y = 65`. Steps are in
  order; the first is the one to do now.
- Completion: `(15, 66, 29)` becoming `CHERRY_LOG`.

### 3.8 Livid

Odin's `features/impl/boss/LividSolver.kt` and Devonian's `features/dungeons/LividSolver.kt` agree
on the mechanism.

- The arena is at **fixed world coordinates**, so no room frame is involved.
- A wool block names the real Livid by changing colour when the fight starts. Skyblocker reads the
  **ceiling wool at absolute `(5, 110, 42)`**; Odin reacts to a block update at `(5, 108, 43)`.
  Both are watched, and the first to *change* to a wool names it (§12.8):

  | Wool | Livid | Chat colour |
  |---|---|---|
  | white | Vendetta | `f` |
  | magenta *or* pink | Crossed | `d` |
  | yellow | Arcade | `e` |
  | lime | Smile | `a` |
  | gray | Doctor | `7` |
  | purple | Purple | `5` |
  | green | Frog | `2` |
  | blue | Scream | `9` |
  | red | Hockey | `c` |

  Odin says magenta for Crossed, Devonian says pink. Map both. §14.3.
- The real Livid is the **player** entity whose name is `"<Name> Livid"`.
- Fight start: the chat line
  `[BOSS] Livid: Welcome, you've arrived right on time. I am Livid, the Master of Shadows.`
  Invulnerability lasts **390 ticks** from it.
- Odin guesses Hockey before the wool changes. We do not: nothing is boxed until the wool names one.
  Skyblocker's fallback is used instead: two seconds after blindness first lands, the ceiling wool is
  trusted as it reads, which covers a real Livid whose colour is the resting one.
- On M5 the real Livid can change colour later in the fight (Skyblocker), so the entity found first
  is kept by id and later wool changes are not followed.
- Odin suppresses the highlight while the player has `MobEffects.BLINDNESS`, which is what Livid's
  attack applies. We do not by default: blindness is when the box is needed. Vanilla's line shader
  fogs to black under blindness, so through-walls lines use a fog-free fragment shader.

### 3.9 Starred mobs

Devonian's `BoxStarMob.kt`. A starred mob is marked by an armour stand whose name contains `✯`. The
mob is the entity at `standId − 1`, or `standId − 3` for a Withermancer. Colour and box height come
from the stand's name:

| Name contains | Height | Role |
|---|---|---|
| `Shadow Assassin` | 2.0 | shadow assassin |
| `Fels` | 3.0 | fels |
| `Skeleton Master` | 2.0 | skeleton master |
| `Withermancer` | 3.0 | tall |
| `Lord`, `Zombie Commander`, `Super Archer` | 2.0 | tall |
| `Adventurer`, `Angry Archaeologist`, `King Midas` | 2.0 | miniboss |
| anything else | 2.0 | ordinary starred mob |

---

## 4. Licensing

- **Odin is BSD-3-Clause.** Its three solution data files are bundled verbatim under
  `assets/cherrypicking/puzzles/`: `water-solutions.json` (14 KB), `boulder-solutions.json` (830 B),
  `creeper-beams-solutions.json` (385 B). BSD-3 requires the copyright notice be retained: put
  `Copyright (c) 2025, odtheking — BSD 3-Clause` in `README.md` and in the header of
  `puzzle/Solutions.java`.
- **Devonian is GPL-3.0**, which this LGPL-3.0-only mod cannot absorb. **Nothing is copied from it.**
  The Livid and starred-mob features are written from the game facts in §3.8 and §3.9.
- **Catppuccin is MIT.** The palette JSON is bundled with its notice in
  `assets/cherrypicking/theme/themes.json` and in `README.md`. The other themes in that file are
  the mod's own layouts of editor colour schemes into Catppuccin's 26 slots.

---

## 5. Package layout

Every new file, with its one job. `~` marks a file that changes rather than appears.

```
src/main/java/jeff/cherrypicking/client/
  CherryPickingClient.java          ~ one call added: Dungeons.register()
  config/
    Control.java                    ~ + Colour and Action kinds
    Entry.java                        NEW sealed: Setting<?> | Action
    Action.java                       NEW a button in the screen; has no value
    Setting.java                    ~ implements Entry
    Settings.java                   ~ holds Entry, gains settings(), + ~60 registrations
    Section.java                    ~ the new tabs and groups
    ConfigFile.java                 ~ saves Setting only; colour as a swatch string
    ConfigScreen.java               ~ becomes a two-line adapter onto the new screen
    Placeholder.java                  DELETED
  theme/
    Flavour.java                      NEW LATTE | FRAPPE | MACCHIATO | MOCHA
    Palette.java                      NEW the 26 named colours of one flavour
    Palettes.java                     NEW loads catppuccin.json; Flavour -> Palette
    Role.java                         NEW semantic slots: BACKDROP, CARD, TEXT, ACCENT, ...
    Theme.java                        NEW live flavour + accent; Role -> argb; the settings' owner
    Swatch.java                       NEW sealed: Named(paletteName) | Literal(argb)
  screen/
    SettingsScreen.java              NEW the Screen: layout, input, search, scroll
    Rail.java                        NEW the left tab rail
    Card.java                        NEW one Section as a collapsible card
    Grid.java                        NEW flows cards into columns that fit the window
    Search.java                      NEW the query, and matching an Entry against it
    Widgets.java                     NEW draws and hit-tests one row per Control kind
    Swatches.java                    NEW the colour popover: palette grid + hex field
    Tooltip.java                     NEW the hovered row's blurb, drawn last
    Chrome.java                      NEW panel, card, border, divider, focus ring primitives
    Text.java                        NEW fit-to-width and wrap helpers (peer of flipper's Labels)
    Scroll.java                      NEW scroll offset + bar (peer of flipper's Scroller)
  dungeon/
    Dungeons.java                    NEW the one register() the entrypoint calls
    DungeonState.java                NEW the gate: in the Catacombs? which floor? in the boss?
    SharedLocraw.java                NEW reads coalroutegenerator's locraw reply from the object share
    room/
      Rotation.java                  NEW four rotations, corner offsets, rotate()
      RoomFrame.java                 NEW record(anchorX, anchorZ, Rotation) + real()/relative()
      RoomWatch.java                 NEW resolves the frame and names the puzzle; generation counter
    draw/
      CherryRenderTypes.java         NEW lines + quads that draw through walls
      DrawKit.java                   NEW vertex writers: fill, lines, box, label
      BoxMesh.java                   NEW AABB -> quads + edges, for static geometry
      Style.java                     NEW FILLED | OUTLINE | FILLED_OUTLINE
      Mark.java                      NEW sealed: Box | EntityBox | Line | Tracer | Label
      Marks.java                     NEW an immutable published list, plus a builder
      MarkRenderer.java              NEW the single COLLECT_SUBMITS listener
    puzzle/
      Puzzle.java                    NEW the solver interface
      Puzzles.java                   NEW the solver registry and dispatch
      Solutions.java                 NEW loads the bundled JSON  (BSD notice here)
      WaterBoard.java  Blaze.java  CreeperBeams.java  TeleportMaze.java  Boulder.java
    boss/
      Livid.java                     NEW wool probe, entity match, invulnerability countdown
      LividTitle.java                NEW the colour-call HUD element
    mob/
      StarMobWatch.java              NEW armour stand with ✯ -> the mob below it  (CorpseWatch's shape)
      StarMob.java                   NEW one found mob: ids, kind, label name
      StarMobRenderer.java           NEW its own COLLECT_SUBMITS listener  (CorpseRenderer's shape)
      MobKind.java                   NEW the name -> height + role table of §3.9

src/main/resources/assets/cherrypicking/
  theme/catppuccin.json              NEW four flavours x 26 colours
  puzzles/water-solutions.json       NEW from Odin
  puzzles/boulder-solutions.json     NEW from Odin
  puzzles/creeper-beams-solutions.json  NEW from Odin

docs/
  dungeon-layer.md                     this document; already committed

build.gradle                         ~ YACL dependency removed
gradle.properties                    ~ yacl_version removed
src/main/resources/fabric.mod.json   ~ the yet_another_config_lib_v3 depends removed
CLAUDE.md                            ~ see section 15
README.md                            ~ features, theme, licences
```

### 5.1 Dropping YACL

The new screen is drawn by this mod, so **YetAnotherConfigLib is no longer used and the dependency
goes**. This is a net simplification: after the change Fabric API is the only hard dependency, and
the reason `HubEntry` never returns `null` gets stronger — the screen has no external library that
could be missing, so it cannot fail to open. `CLAUDE.md` says YACL is a hard `depends`; §15 lists the
edits.

`ConfigScreen.build(Screen parent)` keeps its name and signature. It becomes
`return new SettingsScreen(parent);`. Everything that reaches the screen — `/cherry`, `ScreenOpener`,
`ModMenuHooks`, `HubEntry` — is untouched.

---

## 6. Config core changes

Five files. The rule that neither the screen nor the file knows what any individual setting *is*
survives all of it.

### 6.1 `Entry` and `Action`

A reset button is not a value, so it cannot be a `Setting`. The registry becomes a list of a sealed
`Entry`:

```java
public sealed interface Entry permits Setting, Action {
    String key();       // unique; an Action's key is for ordering and tests, never saved
    String label();
    String blurb();
    Section section();
}

public record Action(String key, String label, String blurb, Section section, Runnable run)
        implements Entry { }
```

`Setting<T>` gains `implements Entry`; its accessors already match. In `Settings`:

- `ALL` becomes `List<Entry>`.
- `all()` returns `List<Entry>` — the screen's view.
- **new** `settings()` returns `List<Setting<?>>` — `ConfigFile`'s and `resetAll`'s view.
- **new** `action(key, label, section, blurb, run)` registration helper.

`ConfigFile` iterates `settings()`, so an `Action` is never written and never read.

### 6.2 `Control.Colour`

```java
record Colour() implements Control<Swatch> { }
```

The value type is `Swatch` (§7.4), not a raw `int`, so a colour can say *"the flavour's green"*
rather than one fixed hex. That is what lets changing flavour re-tint every colour the player has
not overridden.

`ConfigFile` writes a `Swatch` as a string: `"green"` (or `"green@80"` when it is see-through) for
`Named`, `"#AARRGGBB"` for `Literal`.
Reading is the inverse; anything unrecognised logs and keeps the default, as the rest of the file
already does.

Registration helper:

```java
private static void colour(String key, String label, Section section, String blurb,
        Supplier<Swatch> read, Consumer<Swatch> write)
```

**Every colour carries its own opacity**, so every picker has an opacity slider and no feature ever
needs a separate opacity setting beside its colours. Opacity is the one control in the popover that
does not change which kind of `Swatch` the value is (§7.4): a palette colour dragged down to 40 %
is still that palette colour, and still re-tints with the flavour.

A colour's opacity is its **outline's** opacity. A box colour (`Control.Colour(true)`) also carries
a **fill** opacity, `Swatch.fill()`, on a second bar in its picker, so each box has a bright
outline around a nearly clear fill, or not, on its own (check S4-09). It is saved as a `/aa`
suffix, `"green@80/59"`, and left off at the default. `Theme.resolveFill(swatch)` gives the fill's
ARGB. The old shared `boxes.fillOpacity` is gone; `ConfigFile` reads it once from an old file and
works each colour's fill out from it, so boxes look the same after the update.

Feature classes keep a `volatile Swatch` field and a resolved `int argb()` accessor that calls
`Theme.resolve(swatch)`. Resolving at read time, not at write time, is what makes a flavour change
take effect without touching saved values.

### 6.3 `Control.Action`

Not needed. An `Action` is an `Entry`, not a `Setting`, so it needs no `Control`. Keep `Control`
sealed over the five *value* kinds: `Flag`, `Whole`, `Real`, `Choice`, `Colour`.

### 6.4 `Section`

Declaration order is screen order. `GENERAL` goes with `Placeholder`.

```java
THEME        ("Appearance", "Theme"),
SCREEN       ("Appearance", "Screen"),
PUZZLES_ALL  ("Puzzles",    "All puzzles"),
WATER        ("Puzzles",    "Water Board"),
BLAZE        ("Puzzles",    "Blaze"),
BEAMS        ("Puzzles",    "Creeper Beams"),
MAZE         ("Puzzles",    "Teleport Maze"),
BOULDER      ("Puzzles",    "Boulder"),
LIVID        ("Boss",       "Livid"),
LIVID_HUD    ("Boss",       "Livid timer"),
STAR_MOBS    ("Mobs",       "Starred mobs"),
DEVELOPER    ("Advanced",   "Developer");
```

Groups with nothing filed under them are still not drawn, so sections may be declared ahead of the
settings that will fill them.

---

## 7. The theme kit

`client.theme`. Independent of the screen and of the dungeon layer: the in-world marks use it too.

### 7.1 Palette data

`assets/cherrypicking/theme/themes.json` — the four Catppuccin flavours and 22 editor themes
(GitHub, One Dark, Dracula, Nord, Tokyo Night, Gruvbox, Solarized, Rosé Pine, Everforest,
Kanagawa, IntelliJ, VS Code, Monokai), 26 colours each, hex strings without alpha. The Catppuccin values are the official palette, taken from `catppuccin/palette@main/palette.json`.

**Latte** (the default; matches this machine's editors)

| | | | |
|---|---|---|---|
| rosewater `#dc8a78` | flamingo `#dd7878` | pink `#ea76cb` | mauve `#8839ef` |
| red `#d20f39` | maroon `#e64553` | peach `#fe640b` | yellow `#df8e1d` |
| green `#40a02b` | teal `#179299` | sky `#04a5e5` | sapphire `#209fb5` |
| blue `#1e66f5` | lavender `#7287fd` | text `#4c4f69` | subtext1 `#5c5f77` |
| subtext0 `#6c6f85` | overlay2 `#7c7f93` | overlay1 `#8c8fa1` | overlay0 `#9ca0b0` |
| surface2 `#acb0be` | surface1 `#bcc0cc` | surface0 `#ccd0da` | base `#eff1f5` |
| mantle `#e6e9ef` | crust `#dce0e8` | | |

**Frappé**

| | | | |
|---|---|---|---|
| rosewater `#f2d5cf` | flamingo `#eebebe` | pink `#f4b8e4` | mauve `#ca9ee6` |
| red `#e78284` | maroon `#ea999c` | peach `#ef9f76` | yellow `#e5c890` |
| green `#a6d189` | teal `#81c8be` | sky `#99d1db` | sapphire `#85c1dc` |
| blue `#8caaee` | lavender `#babbf1` | text `#c6d0f5` | subtext1 `#b5bfe2` |
| subtext0 `#a5adce` | overlay2 `#949cbb` | overlay1 `#838ba7` | overlay0 `#737994` |
| surface2 `#626880` | surface1 `#51576d` | surface0 `#414559` | base `#303446` |
| mantle `#292c3c` | crust `#232634` | | |

**Macchiato**

| | | | |
|---|---|---|---|
| rosewater `#f4dbd6` | flamingo `#f0c6c6` | pink `#f5bde6` | mauve `#c6a0f6` |
| red `#ed8796` | maroon `#ee99a0` | peach `#f5a97f` | yellow `#eed49f` |
| green `#a6da95` | teal `#8bd5ca` | sky `#91d7e3` | sapphire `#7dc4e4` |
| blue `#8aadf4` | lavender `#b7bdf8` | text `#cad3f5` | subtext1 `#b8c0e0` |
| subtext0 `#a5adcb` | overlay2 `#939ab7` | overlay1 `#8087a2` | overlay0 `#6e738d` |
| surface2 `#5b6078` | surface1 `#494d64` | surface0 `#363a4f` | base `#24273a` |
| mantle `#1e2030` | crust `#181926` | | |

**Mocha**

| | | | |
|---|---|---|---|
| rosewater `#f5e0dc` | flamingo `#f2cdcd` | pink `#f5c2e7` | mauve `#cba6f7` |
| red `#f38ba8` | maroon `#eba0ac` | peach `#fab387` | yellow `#f9e2af` |
| green `#a6e3a1` | teal `#94e2d5` | sky `#89dceb` | sapphire `#74c7ec` |
| blue `#89b4fa` | lavender `#b4befe` | text `#cdd6f4` | subtext1 `#bac2de` |
| subtext0 `#a6adc8` | overlay2 `#9399b2` | overlay1 `#7f849c` | overlay0 `#6c7086` |
| surface2 `#585b70` | surface1 `#45475a` | surface0 `#313244` | base `#1e1e2e` |
| mantle `#181825` | crust `#11111b` | | |

The first fourteen of each flavour are Catppuccin's **accents**; the last twelve are its neutral
ramp. That split is what §7.3 and §7.5 hang off.

Loading it from a resource rather than hard-coding it means a fifth flavour, or a tweak to a hex, is
a data edit. `Palettes` parses it once, lazily, with Gson — the same `Gson` idiom `ConfigFile`
already uses — and logs and falls back to a built-in Latte if the file is malformed, because a theme
is a convenience and a mod that will not start over one bad hex is worse than one that says so.

### 7.2 `Palette` and `Flavour`

```java
public enum Flavour { LATTE, FRAPPE, MACCHIATO, MOCHA }

/** One flavour's 26 colours, by Catppuccin's own names. */
public record Palette(Map<String, Integer> colours) {
    public int of(String name);                 // opaque argb; unknown name -> TEXT, logged once
    public boolean has(String name);
    public List<String> accents();              // the fourteen, in palette order
    public List<String> neutrals();             // the twelve, base-most first
}
```

### 7.3 `Role`

The screen never names a colour. It names a role, and `Theme` maps the role onto a palette name.
Re-flavouring is then one lookup and no screen edits.

| Role | Latte / Frappé / Macchiato / Mocha palette name | Where it is used |
|---|---|---|
| `BACKDROP` | `crust` at 60% alpha | behind the whole panel, over the world |
| `PANEL` | `base` | the panel fill |
| `HEADER` | `mantle` | the title and search strip |
| `FOOTER` | `mantle` | the count and buttons strip |
| `RAIL` | `crust` | the tab rail |
| `RAIL_ACTIVE` | `surface0` | the selected tab's fill |
| `CARD` | `surface0` | a card's body |
| `CARD_HEADER` | `surface1` | a card's title bar |
| `BORDER` | `surface1` | one-pixel edges |
| `BORDER_STRONG` | `surface2` | the panel's outer edge |
| `HOVER` | `surface2` | the hovered row's fill |
| `TEXT` | `text` | labels, values |
| `TEXT_DIM` | `subtext0` | units, group titles, the search hint |
| `TEXT_FAINT` | `overlay1` | a disabled row |
| `ACCENT` | the chosen accent | the active tab bar, a slider's fill, a focus ring |
| `ACCENT_DIM` | the chosen accent at 40% alpha | a slider's track fill under the knob |
| `ON` | `green` | a flag that is on |
| `OFF` | `overlay0` | a flag that is off |
| `WARNING` | `yellow` | "solver stood down" states |
| `DANGER` | `red` | Reset all |

### 7.4 `Swatch`

```java
public sealed interface Swatch {
    /** A palette name and an opacity: the colour follows the flavour, the opacity does not. */
    record Named(String name, int alpha) implements Swatch { }
    /** A literal ARGB: follows nothing. */
    record Literal(int argb) implements Swatch { }

    static Swatch of(String paletteName);          // opaque
    static Swatch of(String paletteName, int alpha);
    static Swatch of(int argb);

    default int alpha();
    default Swatch withAlpha(int alpha);
}
```

`Theme.resolve(Swatch)` returns an ARGB `int`. `Named` looks the name up in the live flavour and
puts its own alpha on it — the palette is opaque (§7.1), so the name's alpha replaces it.
`Literal` is returned as-is.

**Both kinds carry opacity**, so the picker's opacity slider never has to convert one kind into the
other. `ConfigFile` writes a `Named` as `"green"` when it is opaque and `"green@80"` when it is
not; an older file holding a bare `"green"` still reads, as opaque.

Every feature colour default is a `Named`. So `Blaze`'s first blaze ships as `Swatch.of("green")`,
and switching flavour to Mocha turns it from Latte's `#40a02b` into Mocha's `#a6e3a1` with nothing
saved and nothing reset. A player who picks a hex in the popover gets a `Literal`, and it stays.

### 7.5 `Theme` and its two settings

```java
public final class Theme {
    private static volatile Flavour flavour = Flavour.LATTE;   // the default; matches the editors
    private static volatile String accent = "mauve";           // Catppuccin's own default accent

    public static int of(Role role);
    public static int resolve(Swatch swatch);
    public static Palette palette();
    // getters and setters for the two settings
}
```

Two settings, in `Section.THEME`:

- `theme.flavour` — `Choice<Flavour>`, labelled "Colour theme", "Which editor theme's colours the
  settings screen and the in-world markers use." Its dropdown shows seven of each theme's colours.
- `theme.accent` — `Choice<Accent>` over a fourteen-constant enum mirroring the accent names, "Which
  colour highlights the selected tab and the sliders."

Both take effect on the next frame; nothing caches a resolved colour across frames.

**Rejected: reading the editors' theme at runtime.** A mod that opens
`~/Library/Application Support/Code - Insiders/User/settings.json` to pick its colours reaches into
an unrelated application's private files, breaks the moment either product moves its config, and
cannot work on a machine without that editor. Latte is the default *because* that is what the editors
are set to today; the other three are one click away.

### 7.6 Latte is a light theme, and the world is dark

Two consequences, both deliberate:

1. **The panel is fine.** `base #eff1f5` over a dark dungeon reads well, and the `crust` backdrop at
   60% keeps the world visible without the panel losing contrast.
2. **In-world text is not.** Latte's `text #4c4f69` as a floating label over a dark room is
   unreadable. So every in-world **label** is drawn `#FFFFFFFF` on vanilla's name-tag backdrop,
   regardless of flavour, and only in-world **geometry** — boxes, lines, tracers — takes its colour
   from the palette. Latte's accents are saturated mid-tones and read well against stone; the three
   dark flavours' accents are pastels and read even better.

---

## 8. The config screen

`client.screen`. One `Screen` subclass and eight collaborators. Modelled on Meteor Client's module
panel and NEU's category grid.

### 8.1 Layout

```
┌────────────────────────────────────────────────────────────────────────────┐
│  CherryPicking                    ⌕ [ filter every tab…              ]    │  HEADER 22px
├──────────────┬─────────────────────────────────────────────────────────────┤
│ ▍Puzzles     │ ┌ All puzzles ───────────┐ ┌ Water Board ──────────────┐   │
│  Boss        │ │ Solve puzzles      (o) │ │ Water Board solver    (o) │   │  RAIL 84px
│  Mobs        │ │ Draw distance   64 ▸▸▸ │ │ Optimised solutions   ( ) │   │
│  Appearance  │ └────────────────────────┘ │ Show tracer           (o) │   │
│  Advanced    │ ┌ Blaze ─────────────────┐ │ First tracer        ▉     │   │
│              │ │ Blaze solver       (o) │ │ Second tracer       ▉     │   │
│              │ │ Style   Filled outline │ │ Reset water      [Reset]  │   │
│              │ │ Lines shown       1 ▸  │ └───────────────────────────┘   │
│              │ │ …                      │ ┌ Creeper Beams ──────────┐ ▲   │
│              │ └────────────────────────┘ │ …                       │ █   │
├──────────────┴─────────────────────────────────────────────────────────────┤
│  61 settings · 12 shown            [Reset tab]  [Reset all]      [Done]   │  FOOTER 22px
└────────────────────────────────────────────────────────────────────────────┘
```

Constants, all in unscaled GUI pixels. Unlike the flipper's `FlipScreen`, this screen draws at GUI
scale with vanilla widgets, so there is no zoom factor and no divided mouse coordinates.

| | |
|---|---|
| panel width | `min(width − 32, 560)`, centred |
| panel height | `min(height − 32, 320)`, centred |
| header / footer height | 22 |
| rail width | 84 |
| card min width | 160 |
| card gap | 6 |
| row height | 14 |
| card header height | 13 |
| body padding | 6 |

### 8.2 The five things that stop the scrolling

1. **A flat left rail of tabs.** `Appearance`, `Puzzles`, `Boss`, `Mobs`, `Advanced` — five entries,
   no nesting, no scrolling. The active tab gets a two-pixel `ACCENT` bar on its left edge and a
   `RAIL_ACTIVE` fill.
2. **A multi-column card grid.** `Grid` computes
   `columns = max(1, (bodyWidth + GAP) / (CARD_MIN + GAP))` and lays the tab's cards out
   column-by-column, shortest-column-first, so the columns end level. At 560 px that is three
   columns. **This is the single biggest change from the old YACL screen**, whose one-column list is
   what made sixty settings four screens tall.
3. **Collapsible cards.** Clicking a card's header folds it to the header alone. Fold state lives in
   `SettingsScreen` for the session and is not saved: it is a working state, not a preference, and
   saving it would make the screen open differently depending on what you did last week.
4. **One row per setting, 14 px, label left and control right.** No description rows. The blurb is a
   tooltip on hover (§8.6), which is where NEU puts it and where it costs no vertical space.
5. **Search across every tab.** Typing in the header replaces the grid with a flat result grid drawn
   from *all* sections, each card labelled with its `tab · group` so the result keeps its context.
   This is Meteor's best idea: for a known setting, search is faster than any navigation.

Scrolling still exists, because a small window with three cards open can overflow. It is the
family's existing idiom: draw the body at `−scroll.offset()` inside
`graphics.enableScissor(...)`, report the content height back with `scroll.measured(content, view)`,
and draw a two-pixel bar on the right only when it overflows. Copy `Scroll` from the flipper's
`gui/Scroller.java` — the mods share no code, so this is a copy, not an import.

### 8.3 Rows, by `Control` kind

`Widgets` is the only file that knows what a `Control` looks like. Its `draw` and `click` both switch
over the sealed `Control`, so adding a sixth kind is a compile error here and nowhere else.

| Kind | Drawn as | Interaction |
|---|---|---|
| `Flag` | a 16×8 pill, knob left or right, `ON`/`OFF` fill | click anywhere on the row |
| `Whole` | label left; `value unit` then a 48 px track right | click positions; drag scrubs; wheel over it steps; `Shift` steps by one; `0` shows `zeroLabel` when set |
| `Real` | same, formatted by `Control.Format` | as above; `Shift` gives tenth steps |
| `Choice` | the formatted constant, right-aligned, in a `CARD_HEADER` chip | left-click next, right-click previous. A cycling chip, not a dropdown: no choice here has more than fourteen constants, and a dropdown needs overlay ordering the rest of the screen does not |
| `Colour` | a 12×8 swatch in the resolved colour, with a `BORDER` edge | click opens the popover (§8.4) |
| `Action` | the label greyed at the left, a `[Label]` button right | click runs it, then flashes the button `ACCENT` for 4 frames so a reset is visibly acknowledged |

A row whose feature is switched off elsewhere is drawn in `TEXT_FAINT` and ignores clicks.
`Settings` expresses that with a *dependency*: an optional `Supplier<Boolean>` on a registration,
defaulting to always-true. That mirrors Odin's `withDependency` and is what keeps a card readable
when its master flag is off.

### 8.4 The colour popover

A `Colour` row opens `Swatches` — a 150-wide popover anchored under the swatch, clamped inside the
panel, drawn after everything else and consuming every click while open:

```
┌ First blaze ───────────────┐
│ ●●●●●●●  the 14 accents    │   two rows of 7, 14x14, current has an ACCENT ring
│ ●●●●●●●                    │
│ ▁▁▁▁▁▁▁▁▁▁▁▁  the 12       │   one row of 12, 10x14, base-most first
│ ┌──────────────────┐ ┌┐    │   saturation across, brightness up; hue bar on the right
│ └──────────────────┘ └┘    │
│ ▓▓▓▓▓▒▒▒▒░ 70% ░░░░░░░░░░  │   chequerboard, the colour laid over it clear to solid
│ hex     [ #40a02b       ]  │   an EditBox; a valid 6- or 8-digit hex commits a Literal
└────────────────────────────┘
```

Picking from a grid commits a `Swatch.Named`; typing a hex commits a `Swatch.Literal`. The two rows
of swatches are *the current flavour's* colours, so the popover is itself themed and a player
picking "green" is picking the same green the rest of the screen uses.

The palette grid stays first, so it is the path of least resistance. The shade square and hue bar
below it reach any other colour by dragging; both commit a `Swatch.Literal`, keep the current
opacity, and save on release. The hue is held by the popover, so dragging through grey does not
lose it.

The opacity slider shows the answer instead of only a number: a chequerboard, with the colour laid
over it from clear on the left to solid on the right. It carries no label in the margin and the
percentage sits inside the bar, so the track runs the full content width and its ends line up with
the shade square above it; a word beside it read as a bite taken out of the chequers (check S4-07).
The percentage is the one shadowed text in the screen, because it sits on the player's own colour
rather than on a panel, and no theme role stays readable behind every colour. It is the one control
that keeps the
`Swatch`'s kind, through `Swatch.withAlpha`, so a palette colour stays a palette colour. Picking a
grid cell keeps the opacity too, so the two choices are independent. The row's own swatch is drawn
over the same chequers, so a see-through colour reads as see-through on the row and not as a darker
shade of itself.

### 8.5 Search

`Search` holds the query and answers `matches(Entry)`. An entry matches when the lower-cased query
is a substring of its label, its blurb, its key, or its section's tab or group. Blank query means no
filtering.

The header's `EditBox` is a real vanilla widget — `new EditBox(font, x, y, w, 12, Component)` with
`setBordered(false)`, `setHint(Component.literal("filter every tab…"))`, `setTextColor(Role.TEXT)`
and a `setResponder` that stores the query and resets the scroll. `setInitialFocus(box)` in `init()`
means the screen opens ready to type, which is the whole point of having it.

`Escape` with a non-empty query clears the query; `Escape` with an empty query closes the screen. So
Escape never throws away a filtered view by surprise.

### 8.6 Tooltips

The hovered row's blurb, word-wrapped at 180 px with `graphics.textWithWordWrap`, on a `HEADER` fill
with a `BORDER` edge, drawn after the popover, clamped inside the window, offset `(8, 10)` from the
cursor. The setting's key is appended in `TEXT_FAINT` — it is what the config file calls the setting,
and having it on screen is what makes a hand-edited `cherrypicking.json` possible.

### 8.7 Input, in 26.2's shapes

Verified against the deobfuscated jar. **These signatures changed in 26.2** and are the ones every
older tutorial gets wrong:

```java
public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick)
public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event)
public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy)
public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
public boolean keyPressed(net.minecraft.client.input.KeyEvent event)
public boolean charTyped(net.minecraft.client.input.CharacterEvent event)
```

`MouseButtonEvent` is a record with `x()`, `y()`, `button()` and `modifiers()`. `KeyEvent` is a
record with `key()`, `scancode()` and `modifiers()`.

Hit-testing order, both for clicks and for hover, is the reverse of draw order: popover, then footer,
then header, then the grid, then the rail.

Drag scrubbing needs the row that the press landed on to stay captured until release, so
`SettingsScreen` holds a nullable `Dragging(Setting<?> setting, int trackX, int trackWidth)` that
`mouseDragged` updates and `mouseReleased` clears. Without that, a fast drag off the row's 14 px band
stops updating mid-gesture.

### 8.8 Rendering, in 26.2's shapes

26.2 replaced `GuiGraphics` with `GuiGraphicsExtractor`, and the render hook is
`extractRenderState`, not `render`:

```java
@Override
public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
        float partialTick) {
    extractTransparentBackground(graphics);
    super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    // panel, rail, grid, footer, popover, tooltip
}
```

The calls used: `fill(x1,y1,x2,y2,argb)`, `outline(x,y,w,h,argb)`,
`text(font, Component, x, y, argb)`, `centeredText(...)`,
`textWithWordWrap(font, FormattedText, x, y, wrapWidth, argb)`,
`enableScissor(x1,y1,x2,y2)` / `disableScissor()`, and `nextStratum()` before the popover so it
cannot be drawn under a card.

`isPauseScreen()` returns `false`: the world keeps running behind the screen, matching the hub.
`onClose()` calls `minecraft.setScreenAndShow(parent)` so Escape and Done both return where the
player came from — which is what makes the hub's back-navigation work.

Saving: `ConfigFile.save()` on `onClose()`, and also whenever the popover commits, because a player
who alt-F4s after picking a colour should keep it.

---

## 9. Consistency with the sibling mods

`CLAUDE.md` asks for a consistent config style across the family. Today there is none to match:
coalroutegenerator uses YACL, skyblock-flipper uses Cloth Config, and the shared hub is a plain
vanilla screen. Both library screens are single-column scrolling lists and neither can be themed.

So this change **sets** the family's style rather than matching it. The theme kit and the screen are
written to be lifted whole:

- `client.theme` names nothing from this mod except `CherryPicking.LOGGER` and `id()`. Copying the
  package into either sibling is a package rename.
- `client.screen` depends only on `client.config`'s `Entry`, `Setting`, `Action`, `Control` and
  `Section` — and coalroutegenerator already has four of those five, identically named.
- **The mods still import nothing from each other.** Sharing here means copying, which is the
  family's existing rule and the reason the hub contract is a `Function<Screen, Screen>`.

When a sibling adopts it, the hub lists three mods whose screens look like one mod. Until then,
CherryPicking is the one that looks right, and §7 and §8 are the porting note.

---

## 10. The dungeon layer: state and gating

### 10.1 `DungeonState`

The gate every dungeon feature sits behind, and a close peer of coalroutegenerator's
`location/SkyBlockLocation.java` — read that file first; the sidebar-reading idioms and the
private-use area-marker trap are already solved there.

Publishes:

| | |
|---|---|
| `inCatacombs()` | in the Catacombs at all |
| `floor()` | `OptionalInt`; 5 for both F5 and M5 |
| `master()` | true for an M-floor |
| `inBoss()` | past the boss door |
| `inClear()` | `inCatacombs() && !inBoss()` |
| `generation()` | bumped on every crossing, in or out; everything downstream watches this number rather than the boolean |

How each is found:

- **In the Catacombs** — `/locraw`'s `mode` is `dungeon`, or the sidebar names The Catacombs.
  **This mod never sends `/locraw`.** coalroutegenerator already asks at every join, and on
  2026-09-17 two mods asking in the same second as Skyblocker tripped Hypixel's
  `You are sending commands too fast!`. `SharedLocraw` reads coalroutegenerator's reply from Fabric
  Loader's object share under `coalroutegenerator:locraw` — an immutable `Map<String, String>` of
  the reply's fields plus `receivedAt` (epoch millis), empty after a reset. JDK types only, so
  nothing is imported. `DungeonState` checks for a new `ClientLevel` every tick and ignores any
  reply received before it. Without coalroutegenerator the share stays empty and the sidebar alone
  decides.
- **Floor** — the sidebar carries `The Catacombs (F5)`. Match `\(([FM])(\d+)\)` against the lines
  from a copy of coalroutegenerator's `location/ScoreboardReader.java`. Odin instead reads the tab
  list footer; the sidebar is less plumbing and the tab list is the fallback if it turns out to be
  unreliable. §14.4.
- **In the boss** — Odin's per-floor coordinate thresholds, which are cheap and need no packet:

  | Floor | In the boss when |
  |---|---|
  | 1 | `x > −71 && z > −39` |
  | 2–4 | `x > −39 && z > −39` |
  | 5–6 | `x > −39 && z > −7` |
  | 7 | *(read `DungeonListener.getBoss()` for the rest before implementing)* |

  §14.5.

A `developer.forceDungeon` flag forces `inCatacombs()` true, exactly as coalroutegenerator's
`developer.forceActive` does, so the layer can be exercised off Hypixel.

### 10.2 `Dungeons`

The one line `CherryPickingClient` knows about all of this — the peer of coalroutegenerator's
`glacite/Glacite.java`. Adding a feature adds a line here; deleting the layer is deleting this
package and the one call to it.

```java
public static void register() {
    ClientTickEvents.END_CLIENT_TICK.register(DungeonState::tick);
    ClientTickEvents.END_CLIENT_TICK.register(RoomWatch::tick);
    ClientTickEvents.END_CLIENT_TICK.register(Puzzles::tick);
    ClientTickEvents.END_CLIENT_TICK.register(Livid::tick);
    ClientTickEvents.END_CLIENT_TICK.register(StarMobWatch::tick);
    MarkRenderer.register();
    StarMobRenderer.register();
    LividTitle.register();
}
```

---

## 11. The dungeon layer: room frames

### 11.1 `Rotation` and `RoomFrame`

```java
public enum Rotation {
    NORTH(15, 15), SOUTH(-15, -15), WEST(15, -15), EAST(-15, 15);
    public int dx(); public int dz();
    /** §3.1's table. */
    public BlockPos rotate(int x, int y, int z);
    public BlockPos unrotate(BlockPos pos);
}

/** A room's coordinate frame. y is always absolute. */
public record RoomFrame(int anchorX, int anchorZ, Rotation rotation) {
    public BlockPos real(int x, int y, int z);      // rotate then offset by (anchorX, 0, anchorZ)
    public BlockPos relative(BlockPos world);       // the inverse
    public AABB realBox(int x, int y, int z);       // the unit cube at real(x,y,z)
}
```

### 11.2 `RoomWatch` — the frame without a dungeon map

Per §3.2 we never learn the room's name. The frame and the puzzle's identity are found in one step:

1. **The room centre, from the player.**
   `centreX = ((player.blockX + 201) >> 5) * 32 - 185`, same for `z`. No map item, no scan.
2. **Four candidate frames.** For each `Rotation r`, the anchor is
   `(centreX + r.dx(), centreZ + r.dz())`.
3. **Ask the puzzles.** For each candidate frame, for each registered puzzle, test that puzzle's
   `signature()` — two or three relative positions and the blocks expected there. The first match
   gives the frame *and* names the puzzle.
4. **Publish** `frame()`, `puzzle()` and bump `generation()`. Re-run only when the centre changes, or
   when `DungeonState.generation()` changes, or when nothing matched and fewer than `N` attempts
   have been made — a room's blocks arrive over several ticks, so a first look can legitimately miss.
   Retry every 7 ticks, as coalroutegenerator's `CorpseWatch` does, and stop after about 3 seconds.

Cost: four rotations × a handful of block reads per puzzle, once per room. Two dozen block reads
against Odin's map subsystem.

**Signatures** (all relative, from §3):

| Puzzle | Signature |
|---|---|
| Water Board | `LEVER` at `(20,61,15)` **and** `(10,61,15)` **and** `(15,60,5)` |
| Creeper Beams | `(15,74,15)` and `(15,84,13)` are each `SEA_LANTERN` or `PRISMARINE` |
| Teleport Maze | `END_PORTAL_FRAME` at `(15,69,14)` **and** `(15,69,12)` — the exit pair, which is the one pair not in a group of four |
| Boulder | `(15,66,29)` is not air **and** at least one of the 42 grid cells at `y = 66` is not air |
| Blaze | *(none — Blaze needs no frame; see §12.4)* |

Signatures are deliberately over-determined: two or three positions, so a room that happens to have
one matching block does not claim a frame.

A `developer.drawRoomFrame` flag publishes marks for the resolved anchor, the two axes and the room
bounds. **This is the tool that makes a wrong rotation visible instead of silent**, and it is what to
turn on first when a solver draws in the wrong place.

---

## 12. The dungeon layer: drawing, and the features

### 12.1 One draw list, one renderer

Odin gives every solver its own render hook. Here a feature publishes an immutable `Marks` snapshot
from the client thread, and one listener draws it. **A new solver writes no render code**, the same
way a new setting writes no screen code.

```java
public sealed interface Mark {
    /** Static geometry: the box is meshed once, at publish time. */
    record Box(BoxMesh mesh, int argb, Style style) implements Mark { }
    /** A moving entity: its box is read per frame, so the box does not lag the mob. */
    /** lift moves the box's floor from the entity's feet; Blaze uses it to box the blaze under its stand. */
    record EntityBox(int entityId, double inflateXZ, double lift, double height, int argb, Style style)
            implements Mark { }
    record Line(Vec3 from, Vec3 to, int argb, float width) implements Mark { }
    /** From the player's eye to a point; the eye comes from the camera at draw time. */
    record Tracer(Vec3 to, int argb, float width) implements Mark { }
    /** The colour rides in the component's style; scale multiplies the size distance picked. */
    record Label(Vec3 at, Component text, double scale) implements Mark { }
}

public record Marks(List<Mark> marks) {
    public static final Marks NONE = new Marks(List.of());
    public static Builder builder();
}
```

Each feature holds `private static volatile Marks marks = Marks.NONE;` and replaces it whole. That
is exactly coalroutegenerator's `CorpseWatch` → `CorpseRenderer` handoff: the client thread builds,
the render thread reads one volatile reference, and nothing is allocated per frame except a moving
entity's box.

`MarkRenderer` registers **one** `LevelRenderEvents.COLLECT_SUBMITS` listener, returns immediately
when every source is empty, and otherwise:

```java
CameraRenderState camera = context.levelState().cameraRenderState;
Vec3 eye = camera.pos;
PoseStack poseStack = context.poseStack();
OrderedSubmitNodeCollector collector = context.submitNodeCollector().order(Integer.MAX_VALUE);
poseStack.pushPose();
poseStack.translate(-eye.x, -eye.y, -eye.z);   // the level pose is identity at the camera
...
poseStack.popPose();
```

Copy `CherryRenderTypes` from coalroutegenerator's `render/RouteRenderTypes.java`, `DrawKit` from its
`glacite/GlaciteDraw.java`, and `BoxMesh` from its `glacite/BoxMesh.java`. All three are already
correct for 26.2 and carry the reasoning in their comments — in particular that the line vertex
format is `POSITION_COLOR_NORMAL_LINE_WIDTH`, where every attribute must be written or the frame
throws, and a zero-length segment must be dropped rather than written. Labels go through
`collector.submitNameTag(...)`, pre-scaled the way `CorpseRenderer.label` does it.

Each source is registered with a `BooleanSupplier` that says whether it draws through terrain.
Room frames and Livid always do; puzzles follow `puzzles.throughWalls`, through the depth-tested
twins `CherryRenderTypes.lines(false)` and `quads(false)`. A `Tracer` always draws through terrain,
since it starts at the eye. A depth-tested box that sits on a block's faces is inflated by 0.01 so it
does not fight the block for depth.

Drawing through terrain is the point everywhere here: a puzzle's next lever, a starred mob and the
real Livid are all routinely behind a wall, and a depth-tested highlight appears exactly when it has
stopped being useful.

### 12.2 `Puzzle` and `Puzzles`

```java
public interface Puzzle {
    String id();                         // "water", "blaze", ...
    String label();                      // "Water Board"
    boolean enabled();                   // its own flag, from Settings
    /** Relative positions and the blocks expected there. Empty means "I need no frame". */
    List<RoomWatch.Signature> signature();
    /** How often tick() is called. 4 by default; 1 for a solver that would miss something. */
    default int pollTicks() { return 4; }
    /** Called once when RoomWatch names this puzzle. */
    void entered(RoomFrame frame, ClientLevel level);
    /** Client thread, every pollTicks ticks, only while this puzzle is the current room's. */
    void tick(Minecraft client, RoomFrame frame);
    /** What to draw. Never null, often Marks.NONE. */
    Marks marks();
    /** The room went out of view. Defaults to reset(). */
    default void left() { reset(); }
    /** Forget everything. */
    void reset();
}
```

`Puzzles` holds each solver as a `public static final` singleton (`Puzzles.BLAZE`, ...) so the
registry can bind `blaze::enabled`, lists them in `ALL`, hands `RoomWatch` the signatures at
`register()`, and watches `RoomWatch.generation()`: on a change it tells every running solver
`left()` and starts the claimed one plus every solver with no signature (which runs in every room
and finds its own puzzle). It publishes their marks joined, as one source.
**Adding a solver is adding one line to that list**, one class, and its registry lines.

**Two ways to forget.** `left()` is the room going out of view; the default forgets everything,
because a room walked back into is a room to read again. `reset()` is the whole forget: the dungeon
ended, the feature was switched off, or the player pressed its reset button. They are apart because
Water Board's clock has to survive walking out of the room and back — the countdown it is running is
about the lever, not about the visit. A solver that overrides `left()` keeps what outlives the room
and resets the rest, and its `entered()` must then cope with being called while that state is live.

**Each solver sets its own rate.** `pollTicks()` defaults to 4, a fifth of a second, which is what a
puzzle read out of blocks a player changes by hand needs. Teleport Maze and Water Board return 1:
the maze detects a teleport by a position that is only on the pad for a moment, and the water
countdown has to be timed from the lever and read smoothly. One solver asking for every tick does
not make the others pay for it.

**Publishing.** The joined snapshot is rebuilt whenever a solver polled, and also whenever one was
switched off since the last publish — otherwise nothing would clear what a solver left on screen
until its next poll came round.

**Watching it work.** `developer.logPuzzles` writes which solver started in each room, the frame it
got, its poll rate, and every change in how many marks are drawn. That is the solver-side companion
to `developer.logSignatures`, which says why a room was claimed at all.

**`Solutions`** reads each bundled file once, lazily, through one private `File<T>` holder that
carries the path, the parser and the value to hand back when the file cannot be read. Adding a file
is that constant and a getter; the reading, caching, locking and logging are written once, so the
third file cannot drift from the first.

**`Chat`** is the one way the mod puts a line in the player's chat, and the one place to check that
nothing is ever sent *as* the player. `Chat.say` is a feature's own styled line; `Chat.note(feature,
message)` is a solver saying why it is not helping — the puzzle was already started, or its shape is
not in the bundled file — dimmed, named, and copied to the log.

### 12.3 Poll the world; never watch the player's hands

Odin hooks `UseItemOnPost` to learn that the player flicked a lever or pushed a boulder. We read the
world instead:

| Feature | What is polled |
|---|---|
| Water Board | each lever's `LeverBlock.POWERED`; a change is a flick, **including a teammate's** |
| Boulder | the 42-cell grid; the blocks physically move, so the grid *is* the progress |
| Creeper Beams | the lantern pairs, since ends swap between `PRISMARINE` and `SEA_LANTERN` |
| Teleport Maze | the player's position snapping to a pad |
| Livid | the wool at `(5, 110, 42)` and `(5, 108, 43)` |

This needs **no mixin and no input hook at all** — `cherrypicking.mixins.json` stays empty — which
makes §2.1 true by construction rather than by care. It is also strictly better than the upstream:
polling sees what teammates do, and hooking the player's own click does not.

Poll every 4 ticks (a fifth of a second) for puzzles, every tick for Livid's single block read.

### 12.4 `Blaze`

Needs no room frame.

1. Collect the armour stands in the level whose names match
   `^\[Lv+\d+]  Blaze [\d,]+/([\d,]+)❤$`, parsing group 1 with the commas stripped. Walk
   `level.entitiesForRendering()`, the client's own flat view, which allocates nothing to iterate.
2. **Direction**, resolved once on entry and then held: find the chest in the room and compare its
   `y` to the mid-height of the blazes. Chest below → lowest health first; chest above → highest
   health first. `blaze.order` overrides with `Auto`, `Lowest health first`, `Highest health first`.
3. Sort, then publish an `EntityBox` per blaze: index 0 in `blaze.firstColour`, 1 in
   `blaze.secondColour`, 2 in `blaze.thirdColour`, the rest in `blaze.otherColour`. Odin inflates the
   box by `(0.5, 1.0, 0.5)` and moves it down 1.0 to fit the blaze rather than its stand.
4. When `blaze.nextLine` is on, draw a `Line` from blaze `i−1` to blaze `i` for the first
   `blaze.lines` of them, at `blaze.lineWidth`.
5. The puzzle is solved when the count reaches zero having last been one. Then, if
   `blaze.announce` is on, **print a client-side line** — see §12.8.

### 12.5 `CreeperBeams`

On entry, and whenever a polled end has changed: walk the 13 rows of
`creeper-beams-solutions.json`, keep a row when `frame.real(...)` of both ends currently holds
`SEA_LANTERN`, and publish a `Box` at each end plus, when `beams.tracer` is on, a `Line` between
their centres. Colour comes from a per-index cycle through eight `Swatch.Named` accents, so the pairs
are told apart at a glance and the palette stays the flavour's.

### 12.6 `TeleportMaze`

Hold `tpPads` (the 30 real positions), `visited`, `reachable` and `best`. On each detected teleport
(§3.6), update all four, then publish: a filled `Box` per pad, in `maze.oneColour` when it is the
only reachable pad, `maze.multipleColour` when it is one of several, `maze.visitedColour` when it has
been used, and a dim white otherwise; plus a `Tracer` to `best` when `maze.tracer` is on.

Line of sight: Odin's `isXZInterceptable` against the pad's box inflated `(0.75, 0, 0.75)` and 4
blocks tall, at 32 blocks. Port it as a private helper rather than pulling in its `VecUtils`.

### 12.7 `Boulder`

On entry, build the 42-character key (§3.7) and look it up. Publish a `Box` at each step's render
position — only the first unless `boulder.showAll` is on. Advance by re-reading the key each poll:
when it changes to a key whose solution list is a suffix of the current one, drop the steps that are
done. If the new key is not in the file at all, hold the last good list and set a `WARNING` state
rather than clearing the screen mid-puzzle.

### 12.8 `Livid`

1. Gate on `DungeonState.inBoss() && floor() == 5`.
2. Read both wool spots each tick (§3.8). Keep each spot's first loaded state; the first spot to
   change to one of the nine wools names the real Livid, on that tick. Fallback: 40 ticks after the
   first blindness, trust the ceiling wool. Box nothing before either.
3. Find the `Player` entity whose name is `"<Name> Livid"`, lock its id, and publish one
   `EntityBox` in `livid.boxColour`.
4. `livid.hideWhenBlind` (off by default) suppresses the box while the player is blinded.
5. The fight state resets on a new level or on leaving the Catacombs, **not** on
   `DungeonState.generation()`: the boss door bumps it, and a bump mid-fight would take the changed
   wool for the resting one.
6. **Say the colour, not the name.** Hypixel's nine Livids are called Frog, Hockey, Arcade and so
   on, which tell a player nothing about what to look at. `Livid.Name` carries the wool colour as
   well — `"DARK GREEN"`, `"RED"` — and both outputs lead with it. The two greens are spelled
   `DARK GREEN` (Frog) and `LIGHT GREEN` (Smile), because at a glance the word has to say which.
   `livid.announce` prints `Real Livid: DARK GREEN (Frog)`, the colour in its own chat colour.
7. `LividTitle` puts the same colour across the middle of the screen — `DARK GREEN!` — the moment
   the wool reads, for `livid.title.seconds` and at `livid.title.scale`, then fading out over half
   a second. The fade is the tail **past** the set time, not the end of it, so "how long it stays"
   is how long the colour is readable; folding the fade into the set time made the word look like
   it left early (check S4-04). It is this mod's own HUD element rather than `Hud.setTitle`, so Hypixel's own titles
   cannot replace it mid-fight. Register it the way coalroutegenerator's `render/LobbyMap.java`
   does: `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, CherryPicking.id("livid_title"), LividTitle::draw)`.

**The invulnerability timer is gone.** It counted 390 ticks down from the welcome line, and in play
that number matched neither the blindness nor the moment Livid comes down, so it told the player
nothing they could act on (in-game check `S3-12`). Cut rather than tuned: the fight already shows
both events, and a countdown that is confidently wrong is worse than none.

**One deliberate deviation from Odin parity.** Odin's "send complete" runs
`/pc Blaze puzzle solved!`. Sending chat as the player is the mod acting *as* the player, which sits
against §2.1, so `blaze.announce` prints a client-side line only — the player sees it, the party does
not. Everything else in Odin's parity list is display and is implemented as such. Say the word and it
becomes a party message; it is a one-line change and the setting already exists.

### 12.9 `StarMobWatch` and `StarMobRenderer`

Built to the shape of coalroutegenerator's Glacite corpses: `glacite/CorpseWatch.java` finds,
`glacite/Corpse.java` holds one find, `glacite/CorpseRenderer.java` draws. Copy that structure and its
comments; only the test and the box change. Starred mobs are the one feature that does **not** go
through `Marks` (§12.1): like the corpses, they get their own `COLLECT_SUBMITS` listener, so the
highlight is the same one players already know from the mineshaft.

**Finding (`StarMobWatch`, after `CorpseWatch`).** Runs in `inClear()` on any floor. Every
`PASS_INTERVAL_TICKS` (4) ticks, walk `level.entitiesForRendering()`:

1. Cheapest test first: `ArmorStand`, not removed, standing on one of the current room's tiles
   (`RoomWatch.inRoom`), with no distance limit. Only then read the custom name, and keep it when it
   contains `✯`.
2. **Never classify once.** Hypixel spawns the stand and names it in separate packets, so a stand
   that was unnamed last pass is tested again this pass. This is the corpse detector's "tested too
   early" bug, avoided the same way: every pass re-reads every stand in range.
3. The mob is `level.getEntity(standId − 1)`, or `standId − 3` when the name contains
   `Withermancer`. **Validate** the candidate: a `LivingEntity`, not an `ArmorStand`, alive,
   within 1.5 blocks horizontally, and between 4 below and 0.5 above the stand. If it fails, fall
   back to the nearest qualifying entity in that box. No mob, no box.
4. Cap at `MAX_MOBS` (64). `StarMob` is four small fields, so it is rebuilt each pass rather than
   cached.
5. Publish `private static volatile StarMob[] mobs`, replaced whole. **Never cleared by guesswork:**
   every pass re-confirms each mob against the live entities, so a dead mob, a removed stand or a
   stand that lost its `✯` drops out on the next pass. A generation change (§10.1), or turning
   `mobs.enabled` off, clears the lot.

`StarMob` is a record of `standId`, `mobId`, `MobKind kind` and `String name` (the stand's name
with `✯` and the health suffix removed). It keeps ids, not entities, for the reason `Corpse` gives.

**Hidden Fels.** A Fels waits as an invisible Enderman until a player comes close. Skyblocker skips
invisible entities and so never boxes one early; NoFrills does not skip them. Invisible mobs are
never rejected here, and with `mobs.hiddenFels` on (off by default: a hidden Fels shows no star, so
starred and unstarred cannot be told apart), every invisible Enderman in the room is boxed as a
Fels even with no star stand found for it. A real player (version-4 UUID) is never an owner; the
player-shaped dungeon mobs have other UUID versions.

**Drawing (`StarMobRenderer`, after `CorpseRenderer`).** One `LevelRenderEvents.COLLECT_SUBMITS`
listener that returns on an empty array. Otherwise, per mob:

- Look the mob up by id. Gone or removed: skip it for this frame.
- The box is the mob's own box at its interpolated position
  (`entity.getPosition(partialTick)`), widened by `WIDEN` (0.25) each side like `Corpse.of`, and
  the entity's own height (the `MobKind` heights in §3.9 are not used: the entity's box fits every
  mob, a hidden Fels included). This is the one difference from the corpses: a corpse never moves, so it is
  meshed once; a mob does, so its `BoxMesh` is built per frame. A room holds a handful, so that is
  a few small arrays per frame.
- Six shaded quads through `CherryRenderTypes.quadsThroughWalls()` and twelve edges through
  `linesThroughWalls()`: `DrawKit.fill` and `DrawKit.lines`, which are `GlaciteDraw`'s code already.
  **The opacity is the colour's own**: the edges take the `mobs.*Colour` alpha as it is, and the
  fill takes the colour's own fill opacity — the same split `DrawKit.box` makes everywhere else.
  So there is no `mobs.opacity` setting to keep in step, and one kind of mob can be louder, or
  more solid, than another.
- When `mobs.labels` is on, the mob's name and its distance, `Shadow Assassin  14m`, floating 0.4
  above the box and held at a readable size by `DrawKit.label`, the way `CorpseRenderer.label`
  does it.

Drawn through terrain, as the corpses are: a starred mob is usually round a corner.

---

## 13. The settings registry

Every entry, in screen order. `flag` / `whole` / `real` / `choice` / `colour` / `action` are the
registration helpers. Every colour default is a `Swatch.Named`, so it follows the flavour.

**Appearance · Theme**

| Key | Kind | Label |
|---|---|---|
| `theme.flavour` | choice | Flavour |
| `theme.accent` | choice | Accent colour |

**Appearance · Screen**

| Key | Kind | Label |
|---|---|---|
| `screen.cardWidth` | whole | Card width (160–280 px) |
| `screen.compact` | flag | Compact rows (12 px instead of 14) |
| `screen.tooltips` | flag | Show descriptions on hover |

`client.screen.ScreenSettings` is the one public class in that package: `Grid`, `Widgets` and
`Tooltip` each hold the value they use and stay package-private, so a layout constant is not
something the rest of the mod can reach into.

**Puzzles · All puzzles**

| Key | Kind | Label |
|---|---|---|
| `puzzles.enabled` | flag | Solve puzzles |
| `puzzles.drawDistance` | whole | Draw distance (16–128 blocks) |
| `puzzles.throughWalls` | flag | Draw through walls |

**Puzzles · Water Board** — `water.enabled`, `water.optimised`, `water.tracer`,
`water.tracerFirstColour`, `water.tracerSecondColour`, `water.labelScale`, `water.reset` (action)

**Puzzles · Blaze** — `blaze.enabled`, `blaze.style`, `blaze.nextLine`, `blaze.lines`,
`blaze.lineWidth`, `blaze.firstColour`, `blaze.secondColour`, `blaze.thirdColour`,
`blaze.otherColour`, `blaze.order`, `blaze.announce`, `blaze.reset` (action)

**Puzzles · Creeper Beams** — `beams.enabled`, `beams.style`, `beams.tracer`, `beams.alpha`,
`beams.reset` (action)

**Puzzles · Teleport Maze** — `maze.enabled`, `maze.oneColour`, `maze.multipleColour`,
`maze.visitedColour`, `maze.tracer`, `maze.tracerColour`, `maze.reset` (action)

**Puzzles · Boulder** — `boulder.enabled`, `boulder.showAll`, `boulder.style`, `boulder.colour`,
`boulder.reset` (action)

**Boss · Livid** — `livid.enabled`, `livid.style`, `livid.boxColour`, `livid.announce`,
`livid.hideWhenBlind`. `livid.boxColour` defaults to a bright literal green, not a palette name, for
the starred mobs' reason: it is drawn over dark stone. `livid.announce` prints the real Livid's name
to the player's own chat once the wool reads.

**Boss · Livid title** — `livid.title.enabled`, `livid.title.scale`, `livid.title.seconds`

**Mobs · Starred mobs** — `mobs.enabled`, `mobs.labels`, `mobs.hiddenFels`, `mobs.colour`,
`mobs.felsColour`, `mobs.minibossColour`. Three kinds only (every starred mob, Fels, and minibosses
including the Shadow Assassin), in bright literal colours that read on grey stone. Each colour
carries its own outline and fill opacity, set on the two bars in its picker, so there is no
`mobs.opacity` here. There
is no range either: only the current room's starred mobs are boxed, however far off.

**Advanced · Developer** — `developer.forceDungeon`, `developer.drawRoomFrame`,
`developer.logRoomFrame`, `developer.logSignatures`, `developer.logPuzzles`

About sixty-one entries. Each needs a stable key, a label a player would recognise, one plain
sentence of description, and a `Section`.

---

## 14. Constants that need in-game eyes

There is no dev client. These are the things reading cannot settle, each with the developer tool that
makes it visible.

1. **Blaze direction.** The chest rule is from the wiki, not from code. `developer.drawRoomFrame`
   plus a logged chest `y` and blaze span shows what the room actually looks like; `blaze.order`
   overrides it if the rule reads backwards.
2. **Room frame.** Rotation is the thing most likely to be subtly wrong. `developer.drawRoomFrame`
   draws the anchor, both axes and the room bounds, so a wrong rotation is visible rather than a
   solver drawing into a wall.
3. **Livid's Crossed wool.** Odin says magenta, Devonian says pink. Both are mapped; harmless either
   way. Log which one actually appears.
4. **Floor detection.** `The Catacombs (F5)` on the sidebar is the assumption. If the line is not
   there, the tab list footer is the fallback. `developer.logRoomFrame` logs the sidebar lines once
   per floor change.
5. **Boss thresholds.** Odin's floor-7 case was not read; read `DungeonListener.getBoss()` before
   implementing floors 7 and up.
6. **26.2 block names.** Coloured blocks moved to `ColorCollection`: it is
   `Blocks.WOOL.pick(DyeColor.WHITE)` and `Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE)`, **not**
   `Blocks.WHITE_WOOL`. Verify every block a solver names against the jar before writing code around
   it:
   ```bash
   javap -cp ~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/\
   minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar \
     net.minecraft.world.level.block.Blocks | grep -i wool
   ```
7. **Water Board's `hardened_clay`.** The JSON key is a 1.8 name. The block Odin probes for pattern 0
   is plain `TERRACOTTA`. Confirm the lever beside the clay is at `(10,61,10)` and that the key maps
   to it.
8. **The blaze name regex has two spaces** after `]`. Log a raw stand name before trusting it.

---

## 15. Build order

Each stage ends with `./gradlew build`, which is finalised by `installMod` and puts a fresh jar in
`~/Library/Application Support/minecraft/mods/`. Each stage is testable on Hypixel on its own.

**Stage 1 — theme and screen.** `client.theme` whole; `client.screen` whole; `Entry`, `Action`,
`Control.Colour`, `Swatch`; `Settings` gains `settings()`, `action()`, `colour()` and the dependency
supplier; `ConfigFile` swatch read/write; `ConfigScreen.build` becomes the adapter; `Section` gains
`THEME` and `SCREEN`; `Placeholder` and `general.placeholder` deleted; YACL removed from
`build.gradle`, `gradle.properties` and `fabric.mod.json`. At the end of this stage `/cherry` opens
the new screen with two real settings on it — the flavour and the accent — and changing either
re-themes it live. That is a complete, shippable change on its own.

**Stage 2 — the dungeon layer's spine.** `DungeonState` (with `SharedLocraw`, and `ScoreboardReader`
copied from coalroutegenerator), `Rotation`, `RoomFrame`, `RoomWatch`, `CherryRenderTypes`,
`DrawKit`, `BoxMesh`, `Style`, `Mark`, `Marks`, `MarkRenderer`, `Dungeons`, and the `Developer`
section. Nothing user-facing draws except `developer.drawRoomFrame`, which is the point: walk a
dungeon and watch the frame land on real rooms.

**Stage 3 — Livid and starred mobs.** Neither needs a room frame, so they prove the gate, the mark
pipeline, the HUD and the new widgets end to end. `LividTitle` is the first HUD element.

**Stage 4 — the puzzle framework, Blaze and Creeper Beams.** `Puzzle`, `Puzzles`, `Solutions`, and
the two easiest solvers. Blaze needs no frame; Creeper Beams is the first signature and the first
frame user, so it is what proves stage 2.

**Stage 5 — Boulder and Teleport Maze.** A grid probe against a 42-character key, and a visited set
with a line-of-sight filter.

**Stage 6 — Water Board.** The largest: pattern identifier, wool slots, the three-level solution
lookup, the per-lever countdown labels, and the lever polling that starts the clock.

**Stage 7 — documentation.** Update `README.md` (features, theme, the Odin and Catppuccin notices)
and `CLAUDE.md`:

- The "Hard constraint: display-only" section gains §12.8's note about `blaze.announce`.
- The YACL sentences go: it is no longer a dependency. `HubEntry`'s reason for never returning `null`
  becomes stronger — the screen is this mod's own and has no library that could be missing.
- The "every tunable goes in the settings registry" section gains `Colour` and `Action`, and the note
  that `Settings.all()` returns entries while `Settings.settings()` returns settings.
- A new "Dungeons" section pointing at `docs/dungeon-layer.md`.
- A new "Theme" section pointing at §7 and §8 of the same document.

---

## 16. Verification

**Every stage**

- `./gradlew build` succeeds and logs `installMod: installed cherrypicking-1.0.0.jar`.
- Launch the real client. The mod loads; the log shows `cherrypicking loaded`.
- With ModMenu removed, `/cherry` still opens the screen. With the flipper removed, the mod still
  loads.

**Stage 1, the screen** — this is the stage with the most to check by eye.

- `/cherry`, ModMenu's Settings button, and the hub all open the same screen.
- Every tab. Every card folds and unfolds. Three columns at 1080p, fewer in a narrow window, no
  horizontal scrolling ever.
- Every widget kind: a flag toggles, a slider drags and scrubs and steps with `Shift`, a choice
  cycles both ways, a colour opens the popover and both the palette grid and the hex field commit, an
  action's button flashes.
- Search: type `colour` and see matches from several tabs at once, each labelled with its tab and
  group. `Escape` clears the query; `Escape` again closes.
- Switch `theme.flavour` to Mocha: the panel, the rail, the cards and every unset feature colour all
  change. Switch back to Latte. A colour set by hex does **not** change.
- Close, reopen: values hold. Check `config/cherrypicking.json` — colours read as `"green"` or
  `"#ff40a02b"`, there is no `general.placeholder` key, and no action key.
- Hand-edit one value in the file, restart, confirm it took.

**Stage 2, the frame** — in a Catacombs run with `developer.drawRoomFrame` on: the frame lands on
real rooms, the anchor sits on a room corner, and the axes point along the room rather than across
it. Watch it through a door in each of the four rotations.

**Stages 3–6, the features** — one Catacombs run per stage, `Developer` on for the first run of each:

- F5 or M5 for Livid: the box lands on the right one the tick the ceiling wool changes, and its
  outline stays lit while blinded. The colour, not the Hypixel name, is what the chat line and the
  on-screen title say.
- Any floor for starred mobs: every `✯` mob is boxed and nothing else is, including in a crowded
  room, and boxes vanish on death. The box and label look like a Glacite corpse's, follow the mob
  smoothly, and show through walls. A mob that loads unnamed is boxed once its name arrives.
- Each puzzle room as it turns up. Water Board is the one to watch closely: the countdown must agree
  with the lever you actually flick, including when a teammate flicks one.

**Always**

- Stand in the Hub with no dungeon anywhere near and confirm nothing draws and nothing polls: the
  gate is one volatile read and a return.
