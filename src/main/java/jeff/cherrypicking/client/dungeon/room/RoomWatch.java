package jeff.cherrypicking.client.dungeon.room;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.draw.DrawKit;
import jeff.cherrypicking.client.dungeon.draw.Marks;
import jeff.cherrypicking.client.dungeon.draw.Style;
import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Finds the frame of the room the player is in, and names the puzzle in it.
 *
 * <p><b>No dungeon map.</b> Odin reads the map item, hashes every room and
 * looks the hash up to learn the room's name. We never need the name: the room
 * centre comes from the player's position, and each puzzle identifies itself by
 * probing a few of its own blocks. See docs/dungeon-layer.md §11.2.
 *
 * <ol>
 *   <li>The centre: {@code ((blockX + 201) >> 5) * 32 - 185}, the same for z.</li>
 *   <li>Four candidate frames, one per {@link Rotation}.</li>
 *   <li>For each frame, each {@link Claimant}'s signature. The first that
 *       matches gives the frame and names the puzzle.</li>
 * </ol>
 *
 * <p><b>Room shape.</b> Rooms larger than 1x1 span several 32-block tiles.
 * Between two separate rooms, the one-block seam is empty except at the door;
 * inside one room, the seam is floor and wall along its whole length. So two
 * tiles are one room when the seam holds blocks at two points away from the
 * door ({@value #SEAM_PROBE} blocks either side of its middle). A flood fill
 * from the player's tile gives the room's tiles, and the developer drawing
 * outlines them as one shape. Only a 1x1 room is offered to the claimants:
 * every puzzle room is 1x1.
 *
 * <p>This re-runs only when the player leaves the room's tiles, when
 * {@link DungeonState#generation()} changes, or when nothing matched yet: a
 * room's blocks arrive over several ticks, so it tries again every
 * {@value #RETRY_TICKS} ticks, {@value #MAX_ATTEMPTS} times. A room with no
 * puzzle costs those few tries and then nothing.
 *
 * <p>With {@code developer.drawRoomFrame} on it draws the room bounds, and once
 * a frame is found, the anchor and both axes. That is what makes a wrong
 * rotation visible instead of silent.
 */
public final class RoomWatch {
	/** One room-relative position and what is expected there. */
	public record Signature(int x, int y, int z, Predicate<BlockState> expect, String what) {
		boolean matches(ClientLevel level, RoomFrame frame) {
			return expect.test(level.getBlockState(frame.real(x, y, z)));
		}
	}

	/**
	 * Something that can claim a room: a puzzle's id and its signature. Every
	 * position must match. Keep two or three, so a room that happens to hold one
	 * matching block does not claim a frame.
	 */
	public record Claimant(String id, List<Signature> signature) {
		public Claimant {
			signature = List.copyOf(signature);
		}
	}

	/** Tiles run {@code 0..5} on each axis. */
	private static final int TILES = 6;

	private static final int RETRY_TICKS = 7;

	/** About three seconds of retries. */
	private static final int MAX_ATTEMPTS = 9;

	/** A room is 31 blocks across: its centre, plus this on each side. */
	private static final int HALF = 15;

	/** Tile centres are this far apart; the seam sits at centre + 16. */
	private static final int TILE = 32;

	/** How far from the middle of a seam to look, so a door is never probed. */
	private static final int SEAM_PROBE = 8;

	/** The heights a seam is read over; below is the dungeon's own floor. */
	private static final int SCAN_BOTTOM = 12;
	private static final int SCAN_TOP = 140;

	// The developer drawing's extent. The room has no such bounds; these frame the
	// heights the puzzle rooms use.
	private static final int DRAW_BOTTOM = 60;
	private static final int DRAW_TOP = 100;
	private static final int AXIS_Y = 69;
	private static final int AXIS_LENGTH = 8;
	private static final float AXIS_WIDTH = 3.0f;

	private static final int NONE = Integer.MIN_VALUE;

	/** Who may claim a room. Set once, by whatever owns the puzzles. */
	private static volatile List<Claimant> claimants = List.of();

	// Published. Volatile: read by features and by the render thread.
	private static volatile RoomFrame frame;
	private static volatile String puzzle;
	private static volatile int generation;
	private static volatile Marks marks = Marks.NONE;

	// Developer flags.
	private static volatile boolean drawRoomFrame;
	private static volatile boolean logRoomFrame;
	private static volatile boolean logSignatures;

	// Watch state. Client thread only.
	private static int centreX = NONE;
	private static int centreZ = NONE;
	/** The room's tiles, bit {@code tileZ * TILES + tileX}. */
	private static long roomTiles;
	private static int attempts;
	private static int sinceTry;
	private static int dungeonGeneration = -1;
	private static String loggedFloor = "";

	private RoomWatch() {
	}

	/** Client thread, every tick. One volatile read outside the Catacombs. */
	public static void tick(Minecraft client) {
		int now = DungeonState.generation();
		if (now != dungeonGeneration) {
			dungeonGeneration = now;
			forget();
			logSidebar();
		}

		if (!DungeonState.inClear()) {
			return;
		}

		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null) {
			return;
		}

		int tileX = (player.getBlockX() + 201) >> 5;
		int tileZ = (player.getBlockZ() + 201) >> 5;
		if (tileX < 0 || tileX >= TILES || tileZ < 0 || tileZ >= TILES) {
			if (centreX != NONE) {
				forget();
			}
			return;
		}

		if (centreX == NONE || (roomTiles & bit(tileX, tileZ)) == 0) {
			enter(tileX, tileZ);
		}

		if (frame != null || attempts >= MAX_ATTEMPTS) {
			return;
		}
		if (++sinceTry < RETRY_TICKS) {
			return;
		}
		sinceTry = 0;
		attempts++;

		// The seams load with the chunks, so the shape can grow over the first tries.
		long tiles = shape(level, tileOf(centreX), tileOf(centreZ));
		if (tiles != roomTiles) {
			roomTiles = tiles;
			publish();
			if (logRoomFrame) {
				CherryPicking.LOGGER.info("Room: centre x {}, z {} spans {} tile(s)", centreX, centreZ,
						Long.bitCount(tiles));
			}
		}
		if (Long.bitCount(roomTiles) == 1 && !claimants.isEmpty()) {
			resolve(level);
		}
	}

	private static long bit(int tileX, int tileZ) {
		return 1L << (tileZ * TILES + tileX);
	}

	private static int centre(int tile) {
		return tile * TILE - 185;
	}

	private static int tileOf(int centre) {
		return (centre + 185) / TILE;
	}

	/** Every tile joined to this one: a flood fill across the joined seams. */
	private static long shape(ClientLevel level, int tileX, int tileZ) {
		long tiles = bit(tileX, tileZ);
		long frontier = tiles;
		while (frontier != 0) {
			int index = Long.numberOfTrailingZeros(frontier);
			frontier &= frontier - 1;
			int x = index % TILES;
			int z = index / TILES;
			int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
			for (int[] step : steps) {
				int nx = x + step[0];
				int nz = z + step[1];
				if (nx < 0 || nx >= TILES || nz < 0 || nz >= TILES || (tiles & bit(nx, nz)) != 0) {
					continue;
				}
				if (joined(level, Math.min(x, nx), Math.min(z, nz), step[0] != 0)) {
					tiles |= bit(nx, nz);
					frontier |= bit(nx, nz);
				}
			}
		}
		return tiles;
	}

	/**
	 * Whether the seam on the +x ({@code alongX}) or +z side of a tile is inside one
	 * room. Probes both sides of the door, so a door alone never joins two rooms.
	 */
	private static boolean joined(ClientLevel level, int tileX, int tileZ, boolean alongX) {
		int x = centre(tileX);
		int z = centre(tileZ);
		if (alongX) {
			return solid(level, x + HALF + 1, z - SEAM_PROBE) && solid(level, x + HALF + 1, z + SEAM_PROBE);
		}
		return solid(level, x - SEAM_PROBE, z + HALF + 1) && solid(level, x + SEAM_PROBE, z + HALF + 1);
	}

	/**
	 * Any block in this column between {@value #SCAN_BOTTOM} and {@value #SCAN_TOP}.
	 * An unloaded column counts as empty, and is tried again.
	 *
	 * <p>Reads the blocks, not the heightmap. A heightmap test ("any block at all")
	 * joined every gap, so the whole dungeon became one room: either something sits
	 * under the gaps or the server's heightmaps are wrong, and reading the blocks
	 * over the rooms' heights is right either way. The range is the one the
	 * world-scanning dungeon maps use (Skytils, Funnymap).
	 */
	private static boolean solid(ClientLevel level, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return false;
		}
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, 0, z);
		for (int y = SCAN_TOP; y >= SCAN_BOTTOM; y--) {
			if (!level.getBlockState(at.setY(y)).isAir()) {
				return true;
			}
		}
		return false;
	}

	/** A new room: drop the old frame and shape, and try on this tick. */
	private static void enter(int tileX, int tileZ) {
		int x = centre(tileX);
		int z = centre(tileZ);
		centreX = x;
		centreZ = z;
		roomTiles = bit(tileX, tileZ);
		attempts = 0;
		sinceTry = RETRY_TICKS - 1;
		frame = null;
		puzzle = null;
		generation++;
		publish();
		if (logRoomFrame) {
			CherryPicking.LOGGER.info("Room: centre x {}, z {}", x, z);
		}
	}

	/** Every rotation against every claimant; the first full match wins. */
	private static void resolve(ClientLevel level) {
		List<Claimant> candidates = claimants;
		for (Rotation rotation : Rotation.values()) {
			RoomFrame candidate = RoomFrame.at(centreX, centreZ, rotation);
			for (Claimant claimant : candidates) {
				if (matches(level, candidate, claimant)) {
					frame = candidate;
					puzzle = claimant.id();
					generation++;
					publish();
					if (logRoomFrame) {
						CherryPicking.LOGGER.info("Room: {} at anchor x {}, z {}, facing {} (try {})",
								claimant.id(), candidate.anchorX(), candidate.anchorZ(), rotation,
								attempts);
					}
					return;
				}
			}
		}

		if (logRoomFrame && attempts == MAX_ATTEMPTS) {
			CherryPicking.LOGGER.info("Room: nothing claimed centre x {}, z {}", centreX, centreZ);
		}
	}

	private static boolean matches(ClientLevel level, RoomFrame candidate, Claimant claimant) {
		for (Signature signature : claimant.signature()) {
			boolean hit = signature.matches(level, candidate);
			if (logSignatures) {
				BlockPos at = candidate.real(signature.x(), signature.y(), signature.z());
				CherryPicking.LOGGER.info("Signature {} {}: {} at ({}, {}, {}) -> {} is {} - {}",
						claimant.id(), candidate.rotation(), signature.what(),
						signature.x(), signature.y(), signature.z(), at.toShortString(),
						level.getBlockState(at).getBlock().getName().getString(),
						hit ? "match" : "no match");
			}
			if (!hit) {
				return false;
			}
		}
		return !claimant.signature().isEmpty();
	}

	/** The developer drawing: the room bounds, and the anchor and axes once a frame is found. */
	private static void publish() {
		if (!drawRoomFrame || centreX == NONE) {
			marks = Marks.NONE;
			return;
		}

		Marks.Builder builder = Marks.builder();
		outline(builder, roomTiles, colour("teal"));

		RoomFrame now = frame;
		int count = Long.bitCount(roomTiles);
		Vec3 centre = count == 1
				? new Vec3(centreX + 0.5, AXIS_Y + 2, centreZ + 0.5)
				: middle(roomTiles);
		if (now == null) {
			builder.label(centre, count == 1
					? "Room " + centreX + ", " + centreZ + " - no frame"
					: "Room of " + count + " tiles - no frame");
		} else {
			Vec3 origin = Vec3.atCenterOf(now.real(0, AXIS_Y, 0));
			Vec3 alongX = Vec3.atCenterOf(now.real(AXIS_LENGTH, AXIS_Y, 0));
			Vec3 alongZ = Vec3.atCenterOf(now.real(0, AXIS_Y, AXIS_LENGTH));
			builder.box(now.realBox(0, AXIS_Y, 0), Swatch.of("peach"), Style.FILLED_OUTLINE)
					.line(origin, alongX, colour("red"), AXIS_WIDTH)
					.line(origin, alongZ, colour("blue"), AXIS_WIDTH)
					.label(alongX, "+x")
					.label(alongZ, "+z")
					.label(centre, puzzle + " - facing " + now.rotation());
		}
		marks = builder.build();
	}

	/**
	 * The room's outline as one shape: a line along every tile side that has no
	 * room tile beyond it, at the bottom and the top, and an upright at each
	 * corner. A tile that is joined on its +x or +z side reaches across the seam
	 * to its neighbour, so sides meet and collinear pieces merge.
	 */
	private static void outline(Marks.Builder builder, long tiles, int argb) {
		// Sides as {fixed, from, to}: x-sides (lines of constant x) and z-sides.
		List<int[]> xSides = new ArrayList<>();
		List<int[]> zSides = new ArrayList<>();
		for (int index = 0; index < TILES * TILES; index++) {
			if ((tiles & (1L << index)) == 0) {
				continue;
			}
			int tx = index % TILES;
			int tz = index / TILES;
			boolean east = tx + 1 < TILES && (tiles & bit(tx + 1, tz)) != 0;
			boolean west = tx > 0 && (tiles & bit(tx - 1, tz)) != 0;
			boolean south = tz + 1 < TILES && (tiles & bit(tx, tz + 1)) != 0;
			boolean north = tz > 0 && (tiles & bit(tx, tz - 1)) != 0;
			int minX = centre(tx) - HALF;
			int minZ = centre(tz) - HALF;
			int maxX = centre(tx) + HALF + (east ? 2 : 1);
			int maxZ = centre(tz) + HALF + (south ? 2 : 1);
			if (!west) {
				xSides.add(new int[] {minX, minZ, maxZ});
			}
			if (!east) {
				xSides.add(new int[] {maxX, minZ, maxZ});
			}
			if (!north) {
				zSides.add(new int[] {minZ, minX, maxX});
			}
			if (!south) {
				zSides.add(new int[] {maxZ, minX, maxX});
			}
		}

		Set<Long> corners = new HashSet<>();
		for (int[] side : merge(xSides)) {
			for (int y : new int[] {DRAW_BOTTOM, DRAW_TOP}) {
				builder.line(new Vec3(side[0], y, side[1]), new Vec3(side[0], y, side[2]), argb, DrawKit.EDGE_WIDTH);
			}
			corners.add(((long) side[0] << 32) | (side[1] & 0xFFFFFFFFL));
			corners.add(((long) side[0] << 32) | (side[2] & 0xFFFFFFFFL));
		}
		for (int[] side : merge(zSides)) {
			for (int y : new int[] {DRAW_BOTTOM, DRAW_TOP}) {
				builder.line(new Vec3(side[1], y, side[0]), new Vec3(side[2], y, side[0]), argb, DrawKit.EDGE_WIDTH);
			}
		}
		for (long corner : corners) {
			int x = (int) (corner >> 32);
			int z = (int) corner;
			builder.line(new Vec3(x, DRAW_BOTTOM, z), new Vec3(x, DRAW_TOP, z), argb, DrawKit.EDGE_WIDTH);
		}
	}

	/** Joins sides on the same line that touch or overlap. */
	private static List<int[]> merge(List<int[]> sides) {
		sides.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
		List<int[]> merged = new ArrayList<>();
		for (int[] side : sides) {
			int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
			if (last != null && last[0] == side[0] && side[1] <= last[2]) {
				last[2] = Math.max(last[2], side[2]);
			} else {
				merged.add(side.clone());
			}
		}
		return merged;
	}

	/** The middle of the tiles' bounding rectangle, for the label. */
	private static Vec3 middle(long tiles) {
		int minX = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE;
		int minZ = Integer.MAX_VALUE;
		int maxZ = Integer.MIN_VALUE;
		for (int index = 0; index < TILES * TILES; index++) {
			if ((tiles & (1L << index)) != 0) {
				minX = Math.min(minX, centre(index % TILES));
				maxX = Math.max(maxX, centre(index % TILES));
				minZ = Math.min(minZ, centre(index / TILES));
				maxZ = Math.max(maxZ, centre(index / TILES));
			}
		}
		return new Vec3((minX + maxX) / 2.0 + 0.5, AXIS_Y + 2, (minZ + maxZ) / 2.0 + 0.5);
	}

	private static int colour(String name) {
		return Theme.resolve(Swatch.of(name));
	}

	/** §14.4: the sidebar lines, once per floor, so a missed floor line can be read off the log. */
	private static void logSidebar() {
		if (!logRoomFrame || !DungeonState.inCatacombs()) {
			return;
		}
		OptionalInt floor = DungeonState.floor();
		String key = floor.isPresent()
				? (DungeonState.master() ? "M" : "F") + floor.getAsInt()
				: "(no floor)";
		if (key.equals(loggedFloor)) {
			return;
		}
		loggedFloor = key;
		CherryPicking.LOGGER.info("Catacombs floor {}; sidebar: {}", key, DungeonState.sidebar());
	}

	private static void forget() {
		boolean had = centreX != NONE || frame != null;
		centreX = NONE;
		centreZ = NONE;
		roomTiles = 0;
		attempts = 0;
		sinceTry = 0;
		frame = null;
		puzzle = null;
		marks = Marks.NONE;
		if (had) {
			generation++;
		}
	}

	/** Sets who may claim a room. Features call this once, at registration. */
	public static void claimants(List<Claimant> list) {
		claimants = List.copyOf(list);
	}

	/**
	 * Whether a world position is inside the room the player is in, every tile of
	 * it. False when no room is known. Client thread only: it reads the watch state.
	 */
	public static boolean inRoom(double x, double z) {
		if (centreX == NONE) {
			return false;
		}
		int tileX = (Mth.floor(x) + 201) >> 5;
		int tileZ = (Mth.floor(z) + 201) >> 5;
		return tileX >= 0 && tileX < TILES && tileZ >= 0 && tileZ < TILES
				&& (roomTiles & bit(tileX, tileZ)) != 0;
	}

	/** The current room's frame, or {@code null} when none is known. */
	public static RoomFrame frame() {
		return frame;
	}

	/** The id of the puzzle that claimed the current room, or {@code null}. */
	public static String puzzle() {
		return puzzle;
	}

	/** Bumped whenever {@link #frame()} or {@link #puzzle()} changes. */
	public static int generation() {
		return generation;
	}

	/** The developer drawing. Never null, usually {@link Marks#NONE}. */
	public static Marks marks() {
		return marks;
	}

	public static boolean drawRoomFrame() {
		return drawRoomFrame;
	}

	/** Client thread: the settings screen. Redraws at once. */
	public static void drawRoomFrame(boolean value) {
		drawRoomFrame = value;
		publish();
	}

	public static boolean logRoomFrame() {
		return logRoomFrame;
	}

	public static void logRoomFrame(boolean value) {
		logRoomFrame = value;
		if (!value) {
			loggedFloor = "";
		}
	}

	public static boolean logSignatures() {
		return logSignatures;
	}

	public static void logSignatures(boolean value) {
		logSignatures = value;
	}
}
