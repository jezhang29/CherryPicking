package jeff.cherrypicking.client.dungeon.mob;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Finds the starred mobs in the room you are in and keeps the list honest.
 *
 * <p>Built to the shape of coalroutegenerator's {@code glacite.CorpseWatch}.
 *
 * <p><b>The star is on a separate armour stand</b>, not on the mob. Hypixel
 * names the stand {@code ✯ Zombie Lord 1.2M❤} and floats it above the mob.
 * The mob is usually the entity spawned just before the stand ({@code standId
 * - 1}, or {@code - 3} for a Withermancer); when that entity fails the checks,
 * the nearest mob under the stand is used instead.
 *
 * <p><b>Never classified once.</b> The stand is spawned by one packet and named
 * by another, so every pass re-reads every stand in range. A stand that was
 * unnamed a moment ago is picked up as soon as its name lands, and a dead mob,
 * a removed stand or a stand that lost its star drops out on the next pass.
 *
 * <p><b>The room, not a range.</b> A mob is kept when it stands on one of the
 * current room's tiles ({@link RoomWatch#inRoom}), however far off that is. A mob
 * in the next room is behind a wall you are not fighting through yet.
 *
 * <p><b>Only starred.</b> A mob is boxed only through a stand whose name has
 * {@code ✯}, so an unstarred Fels is never boxed that way.
 *
 * <p><b>Hidden Fels.</b> A Fels waits invisible until a player comes close. It
 * has no name stand yet, so a starred Fels and an unstarred one cannot be told
 * apart while it hides. Invisible mobs are never skipped here, and only with
 * {@code mobs.hiddenFels} on, which is off by default, is any invisible Enderman
 * in the room boxed as a Fels with no star stand found for it.
 *
 * <p>Display-only: it draws boxes, nothing else.
 */
public final class StarMobWatch {
	private static final int PASS_INTERVAL_TICKS = 4;

	/** A ceiling on what one pass may hold, so a surprise cannot grow without bound. */
	private static final int MAX_MOBS = 64;

	private static final String STAR = "✯";

	// Where the mob may be, relative to its stand. The stand floats just above the
	// mob's head, so the mob is below it and nearly under it.
	private static final double MAX_HORIZONTAL = 1.5;
	private static final double MAX_BELOW = 4.0;
	private static final double MAX_ABOVE = 0.5;

	/** Real players carry version-4 UUIDs; Hypixel's player-shaped mobs do not. */
	private static final int REAL_PLAYER_UUID_VERSION = 4;

	private static final StarMob[] NONE = new StarMob[0];

	// Settings. Volatile: written by the config screen, read by the pass and the
	// render thread. Defaults are here, at the field.
	private static volatile boolean enabled = true;
	private static volatile boolean labels = true;
	/** Off: a hidden Fels shows no star, so this may box an unstarred one. */
	private static volatile boolean hiddenFels = false;

	/** What to draw. Written by the client thread, read by the render thread. */
	private static volatile StarMob[] mobs = NONE;

	// Pass state. Client thread only.
	private static int generation = -1;
	private static int sincePass;

	private StarMobWatch() {
	}

	/** Client thread, every tick. One volatile read when there is nothing to do. */
	public static void tick(Minecraft client) {
		int now = DungeonState.generation();
		if (now != generation) {
			generation = now;
			clear();
		}

		if (!enabled || !DungeonState.inClear()) {
			return;
		}
		if (++sincePass < PASS_INTERVAL_TICKS) {
			return;
		}
		sincePass = 0;

		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}

		scan(level);
	}

	/** One pass: every star stand in the room, then the hidden Fels. Client thread: reads the room. */
	private static void scan(ClientLevel level) {
		List<StarMob> found = new ArrayList<>();
		IntSet boxed = new IntOpenHashSet();

		for (Entity entity : level.entitiesForRendering()) {
			if (found.size() >= MAX_MOBS) {
				break;
			}
			if (!(entity instanceof ArmorStand stand) || stand.isRemoved() || !stand.hasCustomName()) {
				continue;
			}
			if (!RoomWatch.inRoom(stand.getX(), stand.getZ())) {
				continue;
			}
			Component custom = stand.getCustomName();
			String text = custom == null ? "" : custom.getString();
			if (!text.contains(STAR)) {
				continue;
			}

			LivingEntity mob = owner(level, stand, text);
			if (mob == null || !boxed.add(mob.getId())) {
				continue;
			}
			MobKind kind = MobKind.of(text);
			found.add(new StarMob(stand.getId(), mob.getId(), kind, clean(text)));
		}

		if (hiddenFels) {
			for (Entity entity : level.entitiesForRendering()) {
				if (found.size() >= MAX_MOBS) {
					break;
				}
				if (entity instanceof EnderMan fels && fels.isInvisible() && fels.isAlive()
						&& !boxed.contains(fels.getId())
						&& RoomWatch.inRoom(fels.getX(), fels.getZ())) {
					boxed.add(fels.getId());
					found.add(new StarMob(-1, fels.getId(), MobKind.FELS, "Fels (hidden)"));
				}
			}
		}

		int was = mobs.length;
		mobs = found.isEmpty() ? NONE : found.toArray(StarMob[]::new);
		if (found.size() != was) {
			CherryPicking.LOGGER.debug("Starred mobs: {} in this room.", found.size());
		}
	}

	/** The mob a star stand names: the id guess first, then the nearest mob under the stand. */
	private static LivingEntity owner(ClientLevel level, ArmorStand stand, String text) {
		int offset = text.contains("Withermancer") ? 3 : 1;
		if (level.getEntity(stand.getId() - offset) instanceof LivingEntity guess
				&& qualifies(guess, stand)) {
			return guess;
		}

		AABB under = new AABB(stand.getX() - MAX_HORIZONTAL, stand.getY() - MAX_BELOW,
				stand.getZ() - MAX_HORIZONTAL, stand.getX() + MAX_HORIZONTAL,
				stand.getY() + MAX_ABOVE, stand.getZ() + MAX_HORIZONTAL);
		LivingEntity best = null;
		double bestSq = Double.MAX_VALUE;
		for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, under)) {
			if (qualifies(candidate, stand)) {
				double distanceSq = candidate.position().distanceToSqr(stand.position());
				if (distanceSq < bestSq) {
					bestSq = distanceSq;
					best = candidate;
				}
			}
		}
		return best;
	}

	/** A live mob under the stand. Invisible is fine: that is what a hidden Fels is. */
	private static boolean qualifies(LivingEntity mob, ArmorStand stand) {
		if (mob instanceof ArmorStand || mob.isRemoved() || mob.isDeadOrDying()) {
			return false;
		}
		if (mob instanceof Player player
				&& player.getUUID().version() == REAL_PLAYER_UUID_VERSION) {
			return false;
		}
		double dx = mob.getX() - stand.getX();
		double dz = mob.getZ() - stand.getZ();
		double dy = mob.getY() - stand.getY();
		return dx * dx + dz * dz <= MAX_HORIZONTAL * MAX_HORIZONTAL
				&& dy >= -MAX_BELOW && dy <= MAX_ABOVE;
	}

	/** {@code ✯ Zombie Lord 1.2M❤} reads as {@code Zombie Lord}. */
	private static String clean(String text) {
		String name = text.replace(STAR, "").strip();
		int space = name.lastIndexOf(' ');
		if (space > 0 && name.endsWith("❤")) {
			name = name.substring(0, space);
		}
		return name.strip();
	}

	private static void clear() {
		mobs = NONE;
		sincePass = 0;
	}

	/** What to draw. Never null, often empty. The render thread's only entry point. */
	public static StarMob[] mobs() {
		return mobs;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void enabled(boolean value) {
		enabled = value;
		if (!value) {
			clear();
		}
	}

	public static boolean labels() {
		return labels;
	}

	public static void labels(boolean value) {
		labels = value;
	}

	public static boolean hiddenFels() {
		return hiddenFels;
	}

	public static void hiddenFels(boolean value) {
		hiddenFels = value;
	}
}
