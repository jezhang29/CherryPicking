# Future work

Things the player has asked for that are **not built yet**, and must not be started without a
design first. Each item says where it came from, what the problem is, and what is still unknown.

Nothing here is a promise about the shape of the answer. An item leaves this file when it is
designed in `dungeon-layer.md` and built, or when the player drops it.

---

## 1. Find minibosses by the mob, not by the nametag

**From:** in-game check `S3-08`, 2026-09-19.

### The problem

`StarMobWatch` finds a mob by first finding its `✯` nametag stand, then stepping to the entity
below it (`docs/dungeon-layer.md` §12.9). That works for normal starred mobs.

It does **not** work for minibosses. The player reports that a miniboss's nametag disappears once
they are more than roughly 20 blocks away, **inside the same room**. Hypixel stops sending the
stand. No stand, no box — exactly when a box across a large room would be most use.

So the detector has to be able to find a miniboss **from the mob itself**, with no nametag to lean
on.

### What a design has to answer

1. **What identifies a miniboss without its stand?** Shadow Assassin, Lost Adventurer, Angry
   Archaeologist and King Midas are the four `MobKind.MINIBOSS` names today. Candidate signals:
   entity type, skin or profile, equipment, health, size. Each needs confirming in-game; none is
   confirmed yet.
2. **How is a false positive avoided?** The same entity type is used by ordinary dungeon mobs and,
   for the player-shaped ones, by real players. `StarMobWatch` already rejects version-4 UUIDs;
   whatever replaces the nametag needs its own equivalent.
3. **Does the room say a miniboss must be there?** The player's idea: know, per room, what clearing
   it requires. Three cases they named:
   - rooms that need **starred mobs** killed,
   - rooms that need a **miniboss** killed,
   - one room that needs **both** (the player believes there is only one).

   A room can also hold a miniboss that is *not* starred and does *not* have to die. So "there is a
   miniboss in this room" and "this room needs a miniboss killed" are different facts, and a box
   that does not say which would mislead.
4. **Where does that room data come from?** Wiki, another mod's data set, or measured in-game. It
   has to be keyed to something the mod can already work out. `RoomWatch` finds a room's frame and
   rotation but does **not** identify which room it is. Identifying the room is itself unbuilt
   work, and may be the larger half of this item.
5. **Is the room data worth the weight?** A per-room table has to be shipped, kept current across
   Hypixel's changes, and matched to a room the mod can name. If signal 1 turns out to be reliable
   on its own, the table may not be needed at all. Answer 1 before committing to 3 and 4.

### Out of scope here

**Fels.** A hidden Fels shows no star until it comes out, so starred and unstarred cannot be told
apart beforehand (`docs/dungeon-layer.md` §12.9, and the `mobs.hiddenFels` setting). The player has
set that aside as its own problem. Do not fold it into this one.

### Constraint

Whatever this becomes, it stays display-only: find and box. `CLAUDE.md`'s hard constraint is not
relaxed by a detection improvement.
