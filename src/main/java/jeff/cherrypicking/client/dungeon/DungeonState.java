package jeff.cherrypicking.client.dungeon;

import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * The gate every dungeon feature sits behind: are we in the Catacombs, on which
 * floor, and past the boss door?
 *
 * <p>A close peer of coalroutegenerator's {@code location.SkyBlockLocation}.
 * {@code /locraw}'s {@code mode} is the authority for "in the Catacombs"; the
 * sidebar's {@code The Catacombs (F5)} line gives the floor, and is also the
 * fallback for the window before locraw answers.
 *
 * <p><b>This mod never sends {@code /locraw}.</b> The reply comes from
 * coalroutegenerator through {@link SharedLocraw}. Without that mod, the
 * sidebar alone decides.
 *
 * <p>Outside the Catacombs a tick is a counter and a return, except once a
 * second when the sidebar is read.
 */
public final class DungeonState {
	/** What {@code /locraw} calls a dungeon instance. */
	private static final String DUNGEON_MODE = "dungeon";

	/** {@code The Catacombs (F5)}, {@code (M7)}. The entrance, {@code (E)}, has no floor number. */
	private static final Pattern FLOOR = Pattern.compile("\\(([FM])(\\d+)\\)");

	private static final String CATACOMBS = "The Catacombs";

	private static final int POLL_INTERVAL_TICKS = 20;

	// Published. Volatile: read by the render thread and by every feature.
	private static volatile boolean inCatacombs;
	private static volatile int floor = -1;
	private static volatile boolean master;
	private static volatile boolean inBoss;
	private static volatile int generation;
	private static volatile List<String> sidebar = List.of();

	/** The {@code developer.forceDungeon} flag. */
	private static volatile boolean forced;

	// Poll state. Client thread only.
	private static int tickCounter;
	private static boolean inSkyBlock;
	private static boolean sidebarSaysCatacombs;
	private static boolean locrawSaysCatacombs;
	private static ClientLevel lastLevel;
	private static boolean warnedBoss;

	private DungeonState() {
	}

	/** Client thread, every tick. */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null) {
			clear();
			return;
		}

		// A warp builds a new ClientLevel. Checked every tick, not every poll, so a
		// reply from the old level cannot slip in between the warp and the check.
		if (level != lastLevel) {
			lastLevel = level;
			SharedLocraw.invalidate();
			locrawSaysCatacombs = false;
		}

		if (++tickCounter >= POLL_INTERVAL_TICKS) {
			tickCounter = 0;
			poll(client, level);
		}

		boolean nowIn = forced || (inSkyBlock
				&& (locrawSaysCatacombs || sidebarSaysCatacombs));
		boolean nowBoss = nowIn && pastBossDoor(client.player);
		if (nowIn != inCatacombs || nowBoss != inBoss) {
			inCatacombs = nowIn;
			inBoss = nowBoss;
			generation++;
			CherryPicking.LOGGER.info("Catacombs: {}{}{}", nowIn ? "in" : "out",
					floor < 0 ? "" : " " + (master ? "M" : "F") + floor,
					nowBoss ? ", in the boss room" : "");
		}
	}

	/** Once a second: re-read the sidebar and the shared locraw reply. */
	private static void poll(Minecraft client, ClientLevel level) {
		List<String> lines = ScoreboardReader.lines(level);
		sidebar = lines;
		inSkyBlock = ScoreboardReader.title(level).toUpperCase().contains("SKYBLOCK");

		locrawSaysCatacombs = DUNGEON_MODE.equals(SharedLocraw.mode());

		int nowFloor = -1;
		boolean nowMaster = false;
		boolean catacombs = false;
		for (String line : lines) {
			if (!line.contains(CATACOMBS)) {
				continue;
			}
			catacombs = true;
			Matcher matcher = FLOOR.matcher(line);
			if (matcher.find()) {
				nowMaster = matcher.group(1).equals("M");
				nowFloor = Integer.parseInt(matcher.group(2));
			}
			break;
		}
		sidebarSaysCatacombs = catacombs;

		if (nowFloor != floor || nowMaster != master) {
			floor = nowFloor;
			master = nowMaster;
			warnedBoss = false;
			generation++;
		}
	}

	/**
	 * Odin's per-floor thresholds. Cheap and packet-free: the boss room is the
	 * corner of the map past these coordinates.
	 */
	private static boolean pastBossDoor(LocalPlayer player) {
		if (player == null || floor < 0) {
			return false;
		}

		double x = player.getX();
		double z = player.getZ();
		return switch (floor) {
			case 1 -> x > -71 && z > -39;
			case 2, 3, 4 -> x > -39 && z > -39;
			case 5, 6 -> x > -39 && z > -7;
			default -> {
				// Floor 7's rule was not read from Odin; see docs/dungeon-layer.md §14.5.
				if (!warnedBoss) {
					warnedBoss = true;
					CherryPicking.LOGGER.info("Catacombs: no boss-room rule for floor {}", floor);
				}
				yield false;
			}
		};
	}

	private static void clear() {
		if (inCatacombs || inBoss || floor >= 0) {
			generation++;
		}
		tickCounter = 0;
		inSkyBlock = false;
		sidebarSaysCatacombs = false;
		inCatacombs = forced;
		inBoss = false;
		floor = -1;
		master = false;
		locrawSaysCatacombs = false;
		sidebar = List.of();
		lastLevel = null;
	}

	/** In the Catacombs at all, or forced. */
	public static boolean inCatacombs() {
		return inCatacombs;
	}

	/** 5 for both F5 and M5. Empty in the entrance, and before the sidebar says. */
	public static OptionalInt floor() {
		int now = floor;
		return now < 0 ? OptionalInt.empty() : OptionalInt.of(now);
	}

	/** True on a Master Mode floor. */
	public static boolean master() {
		return master;
	}

	/** Past the boss door. */
	public static boolean inBoss() {
		return inBoss;
	}

	/** Clearing rooms: in the Catacombs and not yet in the boss. */
	public static boolean inClear() {
		return inCatacombs && !inBoss;
	}

	/**
	 * Bumped on every crossing, in or out, and on a floor change. Everything
	 * downstream watches this number rather than the booleans: a change means
	 * "throw away what you found".
	 */
	public static int generation() {
		return generation;
	}

	/** The sidebar as last read, top first, colour codes removed. */
	public static List<String> sidebar() {
		return sidebar;
	}

	/** Developer override, so the layer can be exercised off Hypixel. */
	public static boolean forced() {
		return forced;
	}

	public static void forced(boolean value) {
		forced = value;
	}
}
