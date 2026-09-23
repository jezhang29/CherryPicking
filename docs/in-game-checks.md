# In-game checks

This file lists the things that only a test in the real client on Hypixel can confirm. A code
review adds the checks. The player does the checks and writes the verdicts.

Checks the player marked `PASS` are moved, word for word, to
[in-game-checks-passed.md](in-game-checks-passed.md). Everything left here still needs attention.

## How to use this file

### Player

1. Find the checks that show `Verdict: OPEN`.
2. Do the steps in the game.
3. Change `OPEN` to one verdict from the table below.
4. Write what you saw in `Notes`. Include the log line if the check tells you to look for one.

| Verdict   | Meaning                                                    |
|-----------|------------------------------------------------------------|
| `OPEN`    | Not tested yet.                                            |
| `PASS`    | It works as the check says.                                |
| `FAIL`    | It does not work. Say what happened in `Notes`.            |
| `PARTLY`  | Some of it works. Say which part does not in `Notes`.      |
| `SKIP`    | Not tested, and not necessary now. Say why in `Notes`.     |

The log is `~/Library/Application Support/minecraft/logs/latest.log`. Search it for the text the
check gives.

### Bots (read this before every review)

1. **Read this file before you start a review.** Each `FAIL` or `PARTLY` check on the code you
   review is a confirmed bug. Fix it in your review, and report it as a finding. Also read the
   `Notes` in `in-game-checks-passed.md`: a `PASS` can still carry a problem the player saw.
2. **Add a new section at the end of this file for each review.** Give the section a heading
   `## <review name> (<YYYY-MM-DD>)` and one line that says what the review covered.
3. **Add one check for each thing your review cannot confirm from the code.** For example: what
   Hypixel sends, timing, what a player sees, and whether a shader compiles.
4. **Give each check a stable ID:** a short prefix for the review, then a number (`S3-01`,
   `S3-02`). Never reuse or renumber an ID.
5. **Write each check in the format below.** Write steps that a player can do with no knowledge of
   the code. Give the exact log text or chat text to look for.
6. **Set every new check to `Verdict: OPEN`** and leave `Notes` empty. Only the player writes a
   verdict.
7. **Never delete or change a check, a verdict or a note that is already in this file.** If a fix
   changes the behaviour that an earlier check tested, add a new check with the text
   `Retest of <old ID>` in `Code`. Moving a check as rule 10 says is not a change.
8. **Do not add a check that is already `OPEN`.** Refer to its ID in your report instead.
9. In your final report to the player, give the IDs of the checks you added.
10. **Move each `PASS` check to `in-game-checks-passed.md`, word for word.** Put it under the same
    `##` section heading and intro line there; add them if that section is not there yet. Remove
    a section heading from this file when its last check moves. Keep `OPEN`, `FAIL`, `PARTLY` and
    `SKIP` checks here.

### Check format

```markdown
### <ID>: <short title>
- **Setup:** what must be true before you start (floor, setting, location).
- **Do:** the steps, in order.
- **Expect:** what you must see if it works.
- **Look for:** the exact log or chat text, or "nothing".
- **Code:** file:line that the check tests.
- **Verdict:** OPEN
- **Notes:**
```

---

## Stage 3 review: Livid, starred mobs, colour picker (2026-09-19)

Covered `dungeon/boss/`, `dungeon/mob/`, the no-fog line shader, `RoomWatch.inRoom` and the colour
picker in `screen/Swatches.java`.

### S3-04: M5 keeps the same Livid after a colour change
- **Setup:** M5. `Find the real Livid` on.
- **Do:** Do the fight to the end.
- **Expect:** The box stays on the same real Livid for the full fight.
- **Look for:** `Livid: the wool at ... changed to ...; still boxing ...`. It shows only if the wool
  changed again. Write if it showed.
- **Code:** `dungeon/boss/Livid.java:263`
- **Verdict:** OPEN
- **Notes:**

### S3-08: Starred mobs across a big room
- **Setup:** Any floor. `Box starred mobs` on.
- **Do:** Go into a large room (2x2 or a long 1x4). Stand at one end.
- **Expect:** Starred mobs at the far end of the room have a box. Mobs in other rooms have no box.
  If a far mob has no box, write how far away it was. Hypixel may not send mobs that are far away.
- **Look for:** Nothing.
- **Code:** `dungeon/room/RoomWatch.java:495`, `dungeon/mob/StarMobWatch.java:129`
- **Verdict:** OPEN
- **Notes:**

### S3-10: Hidden Fels
- **Setup:** A floor with Fels. Open `/cherry` and set `Box hidden Fels` off. Your old config can
  still have it on.
- **Do:** Go into a room with a Fels that is still hidden. Then turn `Box hidden Fels` on.
- **Expect:** Off: no box on a hidden Fels. On: a box on a hidden Fels, which can be an unstarred
  one. When a Fels comes out, it has a box only if it is starred.
- **Look for:** Nothing.
- **Code:** `dungeon/mob/StarMobWatch.java:153`
- **Verdict:** OPEN
- **Notes:**

---

## Stage 3 follow-up: Livid colour call-out, per-colour opacity (2026-09-19)

Acts on the notes in `S3-05`, `S3-08`, `S3-11` and `S3-12`. The Livid invulnerability timer is
removed; the Livid's wool colour now goes on screen and into chat; every colour picker carries its
own opacity slider, and the `Opacity` slider on the Starred mobs card is gone.

### S4-09: Starred mob boxes take their opacity from their colour
- **Setup:** Any floor. `Box starred mobs` on. Your old config had `mobs.opacity` at 50%; that
  setting is gone, so the boxes start at the shipped look instead.
- **Do:** Go into a room with starred mobs of more than one kind. Then open `/cherry` and drag the
  opacity of `Miniboss colour` down to about 30%, leaving the others alone.
- **Expect:** Boxes look about as solid as before. Dragging one kind's opacity changes **only**
  that kind: the miniboss box and its outline both fade, and the other mobs' boxes do not change.
  Write whether the shipped solidity is right for you, or which way you had to move it.
- **Look for:** Nothing.
- **Code:** `dungeon/mob/StarMobRenderer.java:115`
- **Verdict:** OPEN
- **Notes:** The problem is that the opacity seems to apply both for the highlight and outline. I think these could be separate, with default values to whatever looks good (and reset button for these). 

### S4-10: The old config file loads
- **Setup:** Your `config/cherrypicking.json` from before this change, holding `livid.hud.*` and
  `mobs.opacity`.
- **Do:** Start the game. Open `/cherry`, check a few values, and close it. Look at the file again.
- **Expect:** Every setting that still exists keeps what you had - flavour Macchiato, accent
  Yellow, the blaze colours, `mobs.hiddenFels` on. No warning in the log. `livid.hud.*` and/
  `mobs.opacity` are gone from the file after the screen saves once.
- **Look for:** `Saved value for` or `Saved colour for` in the log. There should be none.
- **Code:** `config/ConfigFile.java:71`
- **Verdict:** OPEN
- **Notes:** Check this for me. 

---

## Stage 4 follow-up: title timing, the opacity row, fill opacity (2026-09-19)

Acts on the notes in `S4-04`, `S4-07` and `S4-09`. The colour call-out now holds for the full time
set and fades after it; the opacity slider lost its label and runs the whole width of the popover;
box fills have their own opacity, apart from the outline's.

### S5-01: The colour stays for the time that is set
- **Setup:** Floor 5 or M5. `Show the colour on screen` on. Set `How long it stays` to 10.0s.
- **Do:** Start the fight. Count from the moment the word appears to the moment it starts to dim,
  and to the moment it is gone.
- **Expect:** It stays at full strength for the whole 10 seconds, then fades over about half a
  second. It does not begin dimming early. `Retest of S4-04`.
- **Look for:** Nothing.
- **Code:** `dungeon/boss/LividTitle.java:84`, `dungeon/boss/LividTitle.java:99`
- **Verdict:** OPEN
- **Notes:**

### S5-02: The opacity row
- **Setup:** Open `/cherry`. Open any colour setting.
- **Do:** Look at the bar under the shade square. Drag the knob from end to end, and stop near each
  end and in the middle.
- **Expect:** No `opacity` word beside the bar. The bar runs the full width of the popover: its
  left end lines up with the left edge of the shade square, and its right end with the right edge
  of the hue bar. The percentage is inside the bar, centred, readable over the chequers, over the
  colour, and over the knob. Nothing cuts into the chequerboard. `Retest of S4-07`.
- **Look for:** Nothing.
- **Code:** `screen/Swatches.java:203`, `screen/Swatches.java:417`
- **Verdict:** OPEN
- **Notes:**

### S5-03: Fill opacity is apart from outline opacity
- **Setup:** Any floor with starred mobs. `Box starred mobs` on. Open `/cherry` → `Appearance` →
  `Boxes`.
- **Do:**
  1. Find `Fill opacity`. It starts at 35%.
  2. Drag it to 100%, then to 0%, then back to about 35%.
  3. Leave it at 0% and open `Starred mob colour`. Drag that colour's own opacity down to 40%.
- **Expect:**
  1. At 100% the inside of every box is as solid as its outline. At 0% only outlines are left, and
     the outlines are exactly as bright as before.
  2. The colour's own opacity slider fades the **outline**. The fill follows it in proportion, but
     the share between them is only ever changed by `Fill opacity`.
  3. So a bright outline around a nearly clear fill is now possible, and so is the reverse.
     `Retest of S4-09`.
- **Look for:** Nothing.
- **Code:** `dungeon/draw/Style.java:48`, `dungeon/mob/StarMobRenderer.java:116`,
  `config/Settings.java:74`
- **Verdict:** OPEN
- **Notes:**

### S5-04: Fill opacity reaches every box
- **Setup:** A run with Blaze, Creeper Beams and Livid in it. Those solvers on, each in
  `Filled and outline`.
- **Do:** Set `Fill opacity` to 100%. Look at the blaze boxes, the beam boxes and the Livid box.
  Then set it to 10% and look again.
- **Expect:** All of them change together: the one setting governs every box the mod draws, not
  only the mob boxes. The lantern boxes still answer to the `Opacity` slider in the `Creeper Beams`
  group as well, which stands in for the colour picker they do not have.
- **Look for:** Nothing.
- **Code:** `dungeon/draw/DrawKit.java:52`
- **Verdict:** OPEN
- **Notes:**

### S5-05: Reset puts the fills back
- **Setup:** Open `/cherry` → `Appearance`. Move `Fill opacity` away from 35%.
- **Do:** Click `Reset tab` in the footer. Close the screen and open it again.
- **Expect:** `Fill opacity` is back at 35%, and the boxes in the world look the way they did
  before you touched it.
- **Look for:** Nothing.
- **Code:** `config/Section.java:19`
- **Verdict:** OPEN
- **Notes:**

---

## Stage 5 follow-up: fill opacity per colour, more themes, dropdowns (2026-09-22)

Covered: each box colour has its own fill bar in its picker, and the shared `Fill opacity` row in
`Appearance` is gone; the Creeper Beams group has its own fill slider; 22 editor themes join the
four Catppuccin flavours; every choice opens a dropdown.

### S6-01: The picker has an outline bar and a fill bar
- **Setup:** Open `/cherry` → `Mobs`. `Box starred mobs` on.
- **Do:**
  1. Click the swatch of `Starred mob colour`.
  2. Drag the bar that says `Outline` to 100%. Drag the bar that says `Fill` to 0%.
  3. Look at a starred mob.
  4. Drag `Fill` to 100% and `Outline` to 20%. Look again.
- **Expect:** Two bars under the shade square, each with its name and percentage inside it. In
  step 3 the box is a solid outline with a clear inside. In step 4 it is a solid block with a
  faint outline. Each bar changes only its own part. The row's small swatch shows the fill inside
  and the outline round it. `Retest of S5-03`.
- **Look for:** Nothing.
- **Code:** `screen/Swatches.java:170`, `screen/Swatches.java:388`
- **Verdict:** OPEN
- **Notes:**

### S6-02: Each box colour has its own fill
- **Setup:** A run with Blaze, Creeper Beams and Livid in it. Those solvers on, each in
  `Filled and outline`.
- **Do:** Set the fill bar of `Box colour` (Livid) to 100%. Leave the Blaze colours alone. In
  `Creeper Beams`, set `Fill opacity` to 0%.
- **Expect:** Only the Livid box turns solid. The blaze boxes keep their fill. The lantern boxes
  show only outlines. `Appearance` has no `Boxes` group and no `Fill opacity` row.
  `Retest of S5-04`, `Retest of S5-05`.
- **Look for:** Nothing.
- **Code:** `dungeon/draw/Marks.java:44`, `config/Settings.java:156`
- **Verdict:** OPEN
- **Notes:**

### S6-03: An old config keeps its box fills
- **Setup:** Before you install this build, look at `config/cherrypicking.json`. Write down
  `boxes.fillOpacity` (for example `0.35`).
- **Do:** Install this build. Start the game and join Hypixel. Look at a starred mob box. Open
  `/cherry`, then close it. Open `config/cherrypicking.json` again.
- **Expect:** The boxes look as solid as before the update. After the screen closes, the file has
  no `boxes.fillOpacity`, and each box colour ends in `/xx` (for example `"#ff55ffff/59"`), unless
  its fill is the default.
- **Look for:** No `Saved colour for` warning in the log.
- **Code:** `config/ConfigFile.java:150`
- **Verdict:** OPEN
- **Notes:**

### S6-04: The theme dropdown
- **Setup:** Open `/cherry` → `Appearance`.
- **Do:**
  1. Click the chip next to `Colour theme`.
  2. Scroll the list with the wheel.
  3. Press the Down key a few times, then the Up key.
  4. Click `GitHub Dark Dimmed`.
- **Expect:** A list opens under the chip, with the current theme marked and seven small colour
  squares beside each name. The wheel scrolls it. Each Up or Down press changes the whole screen to
  the next theme at once, and the list stays open. A click sets the theme and closes the list.
  Right-click on the chip still steps to the next theme without the list.
- **Look for:** No `Theme flavour` or `No palette colour named` warning in the log.
- **Code:** `screen/Dropdown.java:157`, `screen/Widgets.java:233`
- **Verdict:** OPEN
- **Notes:**

### S6-05: Every theme is readable
- **Setup:** Open `/cherry`.
- **Do:** Step through every theme with the Down key. In each one, look at the tab rail, a card, a
  flag that is on, a slider, and the colour picker's palette grid.
- **Expect:** Text is readable on every panel. On and off flags look different. The 14 palette
  colours in the picker are all different. Write the names of any theme that is hard to read in
  `Notes`.
- **Look for:** Nothing.
- **Code:** `src/main/resources/assets/cherrypicking/theme/themes.json`
- **Verdict:** OPEN
- **Notes:**

---

## Modernization baseline audit (2026-09-22)

Covered current settings access, dungeon lifecycle, room/puzzle detection, rendering, mobs and
shutdown. Documentation-only audit: these checks do not imply a new implementation or install.
Existing OPEN checks are reused in `modernization-plan.md`, including S3-04/08/10, S4-10,
S5-01 and S6-01–05. Record the build/modpack used in Notes when testing.

### MOD-01: Settings work alone and through optional menus
- **Setup:** A real-client profile with CherryPicking and its required Fabric dependencies. Keep
  your normal profile intact; use separate launches to test with ModMenu and the flipper hub.
- **Do:** Open `/cherry`, close it, then open `/cherry config`. In the launches with the optional
  mods, open CherryPicking from ModMenu and from the flipper's shared settings hub. Press Done
  and Escape on separate visits.
- **Expect:** Both commands work with optional mods absent. Every route opens the same settings.
  Done/Escape returns to the menu you came from, or the game for commands. No immediate close
  caused by chat, missing-class error or launch failure.
- **Look for:** `cherrypicking loaded` in the log on each launch.
- **Code:** `client/CherryPickingClient.java:19`, `client/config/ScreenOpener.java:26`,
  `client/config/HubEntry.java:26`, `client/config/ModMenuHooks.java:16`, `client/screen/SettingsScreen.java:603`
- **Verdict:** OPEN
- **Notes:**

### MOD-02: Dungeon state follows a warp and a new run
- **Setup:** Developer `Force the Catacombs` off and `Log room frames` on. Dungeon features on.
  Test once with coalroutegenerator installed and once without it, using normal client profiles.
- **Do:** Enter a dungeon, leave for the Hub, then start another run on the same floor. If possible,
  repeat on another floor and enter its boss room. Record the floor and whether it is Master Mode.
- **Expect:** Hub shows no old boxes, room frame, puzzle guidance or Livid title. The new run
  discovers its own rooms and targets. F1–F6 stop clear-room guidance at the boss. Record F7/M7
  separately: their boss boundary is not implemented, so this check does not promise it works.
- **Look for:** `Catacombs: in`, `Catacombs: out`, and `Room: centre x` in the log. F7/M7 may log
  `Catacombs: no boss-room rule for floor 7`. Record any stale drawing during the warp.
- **Code:** `client/dungeon/DungeonState.java:64`, `client/dungeon/SharedLocraw.java:28`,
  `client/dungeon/room/RoomWatch.java:138`, `client/dungeon/puzzle/Puzzles.java:82`
- **Verdict:** OPEN
- **Notes:**

### MOD-03: Room outlines fit joined tiles and arriving chunks
- **Setup:** A dungeon run with `Draw the room frame` and `Log room frames` on.
- **Do:** Visit a small room, a long room, an L-shaped room and a 2×2 room when available. Cross
  internal tile boundaries and actual doors. Enter a room while its chunks are still appearing,
  wait at least five seconds, then leave and return. In Creeper Beams, inspect the marked axes.
- **Expect:** One outline covers the whole current room and excludes its neighbours. Crossing an
  internal tile seam keeps that room; crossing a door changes it. Loaded geometry is eventually
  recognised. In Beams, the axes and lantern markers agree with the room orientation. Record
  any case that works only after leaving and returning.
- **Look for:** `Room: centre x`, `Room: beams at anchor x`, or `Room: nothing claimed centre x`.
- **Code:** `client/dungeon/room/RoomWatch.java:138`, `client/dungeon/room/RoomFrame.java:21`
- **Verdict:** OPEN
- **Notes:**

### MOD-04: Blaze ordering and teammate progress
- **Setup:** Blaze solver on, Order set to Auto, `Say when solved` on. Test both higher-first and
  lower-first variants as they occur. Ask a teammate to perform some of the kills normally.
- **Do:** Enter before the first kill, compare the first highlighted blaze with its visible health,
  then watch highlights as you and the teammate kill blazes. Leave/re-enter once if practical.
  Try the manual order and Reset controls; return to Auto for the next fresh room.
- **Expect:** Auto agrees with the room's required direction. Next targets follow remaining health
  order for either player's kills. Missing direction gives neutral boxes rather than a guessed
  order. The local `Blaze puzzle solved!` message appears only on actual completion; nobody else
  receives it. Record any premature completion after leaving or a mob disappearing from view.
- **Look for:** `Blaze: chest at`, `Blaze: no chest within`, `Blaze: puzzle solved.`
- **Code:** `client/dungeon/puzzle/Blaze.java:140`, `client/dungeon/puzzle/Puzzles.java:82`
- **Verdict:** OPEN
- **Notes:**

### MOD-05: Creeper Beams follows the room and both players
- **Setup:** Creeper Beams solver and `Line between pairs` on. Test additional room orientations
  over later runs when available.
- **Do:** Enter before completion. Check the lines' two endpoints. Have you and a teammate solve
  different pairs. Leave and return, and try Reset after the puzzle has been completed.
- **Expect:** Marked pairs connect the intended lit lanterns and sit on their blocks in each
  orientation. Each solved pair disappears for either player's progress. Completion clears
  guidance; revisiting/resetting a completed room does not invent an unsolved pair.
- **Look for:** No `Puzzle solutions`, `Could not make sense of`, or `Skipping a malformed beams row`
  warning concerning the bundled beams file.
- **Code:** `client/dungeon/puzzle/CreeperBeams.java:96`, `client/dungeon/puzzle/Solutions.java:60`
- **Verdict:** OPEN
- **Notes:**

### MOD-06: World marks and Livid remain visually correct
- **Setup:** Normal modpack and rendering settings. A dungeon with moving starred mobs/puzzle
  targets and, for the blindness portion, F5/M5. `Hide while blinded` off. Reuse S6-01/02 for alpha.
- **Do:** Watch moving boxes up close and far away, then overlap two mobs in your view. Toggle
  puzzle `Draw through walls` beside an actual wall. During Livid blindness inspect the box,
  then change resource packs or reload resources normally and inspect again. While a title is
  visible, test its own switch and the Livid master switch separately, and record the result.
- **Expect:** Boxes track their targets smoothly, near mob boxes appear in front, puzzle terrain
  visibility follows its setting, and Livid edges remain coloured under blindness. Reload causes
  no lost geometry or shader errors. The title's own off switch hides it; record whether the
  master switch leaves a previously shown title until expiry (its desired behavior needs review).
- **Look for:** No shader/pipeline error mentioning `lines_no_fog` or `cherrypicking`. This includes
  the unverified log portion of S3-03; it does not change that check's verdict.
- **Code:** `client/dungeon/draw/CherryRenderTypes.java:57`, `client/dungeon/draw/MarkRenderer.java:66`,
  `client/dungeon/mob/StarMobRenderer.java:72`, `client/dungeon/boss/LividTitle.java:79`
- **Verdict:** OPEN
- **Notes:**

### MOD-07: Search, reset, resizing and saving stay usable
- **Setup:** Open `/cherry`. Note any preferences you want to restore after this check. Use
  S6-01/04/05 for picker/dropdown/theme-specific checks.
- **Do:** Search `colour`, change one value, clear the search with Escape, and switch tabs. Fold
  a card and resize the window/change GUI scale. Test Reset shown while searching and Reset tab
  without a search. Test Reset all's confirmation only if you want defaults. Change a flag and
  slider, close with Done, restart and check them. Try keyboard navigation and narration if used.
- **Expect:** Search finds matching settings across tabs. Reset shown/tab affects only that scope;
  Reset all's first click alone changes nothing. Click targets match visible rows after resizing.
  Values survive normal close/restart. Record unreachable controls, clipped popovers, focus or
  narration gaps and whether an external screen replacement loses an unsaved preference.
- **Look for:** No `Could not write the config to` or `Saved value for` warning.
- **Code:** `client/screen/SettingsScreen.java:121`, `client/screen/Grid.java:45`,
  `client/screen/Widgets.java:110`, `client/config/ConfigFile.java:99`
- **Verdict:** OPEN
- **Notes:**

### MOD-08: Quit cleanly with the actual modpack
- **Setup:** The real client with the normal modpack. Record whether `Quit cleanly` is on or off.
  Save normal game/settings changes before quitting.
- **Do:** Quit normally to the launcher with the feature on. Relaunch and verify settings remain.
  If practical, compare a normal quit with the feature off. Do not deliberately crash the client.
- **Expect:** Enabled: window and process close without the launcher's post-main crash report;
  saved settings survive. Disabled: ordinary vanilla/modpack shutdown behavior is retained.
  This check alone does not verify genuine crash paths or every third-party shutdown hook.
- **Look for:** `Exiting past threads that would hold the game open:` may appear when enabled.
  Record whether `Client shutdown from post-main` appears and whether the launcher reports a crash.
- **Code:** `client/CleanExit.java:37`, `mixin/ShutdownWatchdogMixin.java:26`
- **Verdict:** OPEN
- **Notes:**

### MOD-09: Starred boxes belong to the right mob
- **Setup:** Dungeon with `Box starred mobs` and labels on, hidden Fels off. Reuse S3-08 for
  distant minibosses and S3-10 for hidden Fels.
- **Do:** Watch a crowded group, including a Withermancer or miniboss when available. Stand near
  a teammate, let mobs die, and watch newly arriving nametags and door crossings.
- **Expect:** Each box/label follows its own live mob, no mob has duplicate boxes, and teammates
  are not boxed. New nametags are picked up after arriving; dead mobs and mobs in the previous
  room lose boxes. Record wrong associations with mob names and the room situation.
- **Look for:** Nothing.
- **Code:** `client/dungeon/mob/StarMobWatch.java:117`, `client/dungeon/mob/StarMobRenderer.java:72`
- **Verdict:** OPEN
- **Notes:**
