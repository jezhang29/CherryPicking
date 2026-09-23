# In-game checks: passed

Checks the player marked `PASS`, moved here word for word from
[in-game-checks.md](in-game-checks.md) so that file shows only the checks still to do. The rules,
the verdict table and the check format are in that file.

A `PASS` means the check worked as written. Some notes here still describe a problem the player
saw; each one of those has a later check in `in-game-checks.md` or an item in
`modernization-plan.md`. Read the notes before a review of the same code.

---

## Stage 3 review: Livid, starred mobs, colour picker (2026-09-19)

Covered `dungeon/boss/`, `dungeon/mob/`, the no-fog line shader, `RoomWatch.inRoom` and the colour
picker in `screen/Swatches.java`.

### S3-01: Which wool block changes colour
- **Setup:** Floor 5 or M5. `Find the real Livid` on.
- **Do:** Go into the Livid boss room. Wait for the fight to start.
- **Expect:** A log line names the Livid. It names one of the two wool blocks.
- **Look for:** `Livid: the wool at 5, 110, 42 changed names` or `Livid: the wool at 5, 108, 43
  changed names`. Write which one you saw, and what colour the ceiling was before the fight.
- **Code:** `dungeon/boss/Livid.java:218`
- **Verdict:** PASS
- **Notes:** Both are valid wool blocks to look at for change.

### S3-02: The box shows as soon as the wool changes
- **Setup:** As S3-01.
- **Do:** Look at the Livids when the fight starts.
- **Expect:** The box goes on the real Livid at the same time the wool changes colour, before the
  blindness ends. The box is on the correct Livid.
- **Look for:** Nothing.
- **Code:** `dungeon/boss/Livid.java:218`
- **Verdict:** PASS
- **Notes:** Working as intended

### S3-03: The box stays bright while you are blind
- **Setup:** As S3-01. `Hide while blinded` off (this is the default now).
- **Do:** Look at the real Livid while you have blindness. Stand far from it, and also close to it.
- **Expect:** The outline and the fill stay in their colour at all distances. They do not go black
  or fade.
- **Look for:** No shader error about `lines_no_fog` in the log after you join.
- **Code:** `dungeon/draw/CherryRenderTypes.java:66`,
  `assets/cherrypicking/shaders/core/lines_no_fog.fsh`
- **Verdict:** PASS
- **Notes:** Working as intended in terms of being able to see the real livid while blind, did not check the logs though. Bot will need to check this. 

### S3-05: The chat message
- **Setup:** As S3-01. `Name it in chat` on.
- **Do:** Start the fight.
- **Expect:** One green message `Real Livid: <name>`, with the correct name. It shows one time only.
- **Look for:** The chat message.
- **Code:** `dungeon/boss/Livid.java:254`
- **Verdict:** PASS
- **Notes:**

### S3-06: The 2-second fallback
- **Setup:** As S3-01. This check applies only when the real Livid has the same colour as the wool
  before the fight. Do it when this happens.
- **Do:** Start the fight.
- **Expect:** The box shows approximately 2 seconds after the blindness starts.
- **Look for:** `Livid: the ceiling wool, two seconds after blindness names`
- **Code:** `dungeon/boss/Livid.java:243`
- **Verdict:** PASS
- **Notes:**

### S3-07: A new run resets the Livid state
- **Setup:** Two F5 or M5 runs, one after the other.
- **Do:** Finish (or leave) the first run. Start the second run and get to Livid.
- **Expect:** No box and no timer from the first run show in the second run. The second run names
  its own Livid.
- **Look for:** One `Livid: ... names ...` line for each run.
- **Code:** `dungeon/boss/Livid.java:177`
- **Verdict:** PASS
- **Notes:**

### S3-09: Boxes change when you go through a door
- **Setup:** As S3-08.
- **Do:** Walk through a door into the next room. Stand in the doorway, then go fully in.
- **Expect:** The boxes change to the mobs of the new room. The mobs of the old room lose their box.
  Write what shows while you are in the doorway.
- **Look for:** Nothing.
- **Code:** `dungeon/mob/StarMobWatch.java:129`
- **Verdict:** PASS
- **Notes:**

### S3-11: The colour picker
- **Setup:** Open `/cherry`. Open any colour setting.
- **Do:**
  1. Drag in the square, and drag out of it past each edge.
  2. Drag in the hue bar.
  3. Drag the square to full grey or full black, then back.
  4. Click a palette colour, and type a hex value.
  5. Make the game window small, then open the picker again.
- **Expect:**
  1. The colour changes while you drag. It saves when you release. The marker stays in the square.
  2. The hue changes. The alpha does not change.
  3. The hue does not jump when you go through grey.
  4. The markers move to the new colour. The hex field shows the new colour.
  5. The full popover fits on the screen.
- **Look for:** Nothing.
- **Code:** `screen/Swatches.java:175`, `screen/Swatches.java:267`
- **Verdict:** PASS
- **Notes:** There should be a built in opacity slider for each color picker (including the livid color one), instead of a global slider. I think make it intuitive and easy to use. 

### S3-12: The Livid timer with the GUI hidden
- **Setup:** As S3-01. `Show the timer` on.
- **Do:** Start the fight. Press F1.
- **Expect:** Write if the timer shows or not. Most players expect it to hide.
- **Look for:** Nothing.
- **Code:** `dungeon/boss/LividHud.java:63`
- **Verdict:** PASS
- **Notes:** Honestly not too sure the purpose of this feature. It's not even accurate to the blindness OR when the livid comes down. Perhaps scrap this feature. 

---

## Stage 3 follow-up: Livid colour call-out, per-colour opacity (2026-09-19)

Acts on the notes in `S3-05`, `S3-08`, `S3-11` and `S3-12`. The Livid invulnerability timer is
removed; the Livid's wool colour now goes on screen and into chat; every colour picker carries its
own opacity slider, and the `Opacity` slider on the Starred mobs card is gone.

### S4-01: The colour on screen
- **Setup:** Floor 5 or M5. `Find the real Livid` on, `Show the colour on screen` on.
- **Do:** Go into the Livid boss room. Start the fight. Watch the middle of your screen.
- **Expect:** One large word in the middle of the screen, in the Livid's own colour, at the same
  moment the box appears: `RED!`, `DARK GREEN!`, `PINK!` and so on. It fades out after 4 seconds.
  The colour matches the Livid the box is on. Write which colour it said, and whether that was the
  right Livid.
- **Look for:** `Livid: the wool at ... names DARK GREEN (Frog).` in the log - the colour and the
  Hypixel name, in that order.
- **Code:** `dungeon/boss/LividTitle.java:62`, `dungeon/boss/Livid.java:253`
- **Verdict:** PASS
- **Notes:**

### S4-02: The colour call-out does not fight another mod
- **Setup:** As S4-01. You said a colour already shows on your screen, possibly from another mod.
- **Do:** Start the fight and watch for **two** call-outs.
- **Expect:** Write whether you see one or two, and if two, whether they agree. If the other one is
  better, say what it does that this one does not. If this one is enough, turn the other mod's off.
- **Look for:** Nothing.
- **Code:** `dungeon/boss/LividTitle.java:96`
- **Verdict:** PASS
- **Notes:**

### S4-03: The chat line names the colour
- **Setup:** As S4-01. `Name it in chat` on.
- **Do:** Start the fight.
- **Expect:** One line, `Real Livid: DARK GREEN (Frog)`. `Real Livid:` is grey, the colour word is
  in that colour, and the Hypixel name in brackets is dark grey. It shows one time only.
- **Look for:** The chat message.
- **Code:** `dungeon/boss/Livid.java:253`
- **Verdict:** PASS
- **Notes:**

### S4-04: Size and time
- **Setup:** As S4-01. Open `/cherry`, `Boss` tab, `Livid title` group.
- **Do:** Set `Size` to 1.00, then to 6.00. Set `How long it stays` to 1.0s, then to 10.0s. Do a
  fight at a setting you like.
- **Expect:** The word gets larger and smaller, stays centred, and never runs off the screen edge
  at size 6. It stays for the number of seconds set, then fades over about half a second.
- **Look for:** Nothing.
- **Code:** `dungeon/boss/LividTitle.java:93`
- **Verdict:** PASS
- **Notes:** I'm not so sure that the time is accurate, it seems to disappear slightly faster than the time indicated through the config. Check this. 

### S4-05: F1 hides the colour call-out
- **Setup:** As S4-01.
- **Do:** Start the fight and press F1 while the word is up. Press F1 again.
- **Expect:** The word goes away with the rest of the GUI, and comes back if there is time left.
- **Look for:** Nothing.
- **Code:** `dungeon/boss/LividTitle.java:85`
- **Verdict:** PASS
- **Notes:**

### S4-06: The timer is gone
- **Setup:** Floor 5 or M5.
- **Do:** Open `/cherry` and look at the `Boss` tab. Then do the fight.
- **Expect:** There is no `Livid timer` group and no `Show the timer` setting. Nothing counts ticks
  down in any corner during the fight. `Retest of S3-12`.
- **Look for:** Nothing.
- **Code:** `config/Section.java:23`
- **Verdict:** PASS
- **Notes:**

### S4-07: The opacity slider in the colour picker
- **Setup:** Open `/cherry`. Open any colour setting, for example `Livid` → `Box colour`.
- **Do:**
  1. Find the `opacity` row under the shade square. Drag it from end to end.
  2. Drag it to about 50%, then click a palette colour in the grid above.
  3. Drag it to 0%, then back up.
  4. Look at the small swatch on the row behind the popover.
- **Expect:**
  1. The bar shows a chequerboard with the colour over it, clear on the left and solid on the
     right. The percentage agrees with the knob.
  2. The colour changes and the opacity **stays** at 50%. The new palette colour keeps the ring.
  3. At 0% the bar is all chequers. Coming back up restores the colour, not a different one.
  4. The row swatch is drawn over chequers too, so a see-through colour looks see-through.
- **Look for:** Nothing.
- **Code:** `screen/Swatches.java:199`, `screen/Widgets.java:133`
- **Verdict:** PASS
- **Notes:** The "opacity" text cuts into the checkboard pattern behind the opacity slider. Just get rid of the text and make the opacity slider carry over. Not sure how you would label the % opacity though while making it look good still. The opacity slider should ideally lign up with the bottom edge of the color picker but idk. 

### S4-08: A see-through palette colour still follows the flavour
- **Setup:** Open `/cherry`. Set `Starred mob colour` to a palette colour, then drag its opacity to
  about 50%.
- **Do:** Switch `Flavour` to another one. Close the screen and reopen it. Then look at
  `config/cherrypicking.json`.
- **Expect:** The colour re-tints with the flavour and stays at 50%. The saved file writes it as
  `"peach@80"` or similar - a name, an `@`, and two hex digits.
- **Look for:** Nothing in the log. No warning about a saved colour that could not be read.
- **Code:** `theme/Swatch.java:64`, `theme/Theme.java:32`
- **Verdict:** PASS
- **Notes:**

---

## M1: reliable saved settings (2026-09-22)

The config file now saves through a temporary file, and loading checks each value's type and
range. Unit tests cover the logic; this check covers the real config folder.

### M1-01: Settings save and come back on your own disk
- **Setup:** Close the game. Open `~/Library/Application Support/minecraft/config/`.
- **Do:**
  1. Start the game. Open `/cherry`.
  2. Change `Lines` in `Blaze` to 3, and turn off `Line to the next blaze`.
  3. Press Done. Look in the config folder.
  4. Quit the game and start it again. Open `/cherry`.
  5. Put both settings back the way you had them, and press Done.
- **Expect:** After step 3 the folder has `cherrypicking.json` and no `cherrypicking.json.tmp`.
  After step 4 both changes are still there.
- **Look for:** No `Could not write the config to` and no `Saved value for` in the log.
- **Code:** `client/config/ConfigFile.java:132`, `client/config/ConfigFile.java:182`
- **Verdict:** PASS
- **Notes:** Changed different config compared to the "Do" part, but still worked/saved.
