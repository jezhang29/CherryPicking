package jeff.cherrypicking.client.dungeon.puzzle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.draw.Mark;
import jeff.cherrypicking.client.dungeon.draw.Marks;
import jeff.cherrypicking.client.dungeon.room.RoomFrame;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The solver registry, and the one tick that runs them.
 *
 * <p><b>Adding a solver</b> is one class, one line in {@link #ALL}, and its lines in the settings
 * registry. This class hands the signatures to {@link RoomWatch}, so the room is found for it.
 *
 * <p><b>Which solvers run.</b> When {@link RoomWatch} names a puzzle and its frame, that puzzle's
 * solver runs. A solver with no signature needs no frame and runs in every room, finding its own
 * puzzle. When {@link RoomWatch#generation()} moves - a new room, a new frame, or a new dungeon -
 * every solver that was running is told {@link Puzzle#left}, and the ones this room wants are
 * told {@link Puzzle#entered}.
 *
 * <p><b>How often.</b> Each solver sets its own {@link Puzzle#pollTicks()}, so a solver that must
 * sample every tick does not make every other solver do the same. Outside the Catacombs' clear
 * phase the tick is one volatile read and a return.
 *
 * <p>Solvers are singletons, held here, so the settings registry can reach their fields.
 */
public final class Puzzles {
	public static final Blaze BLAZE = new Blaze();
	public static final CreeperBeams BEAMS = new CreeperBeams();

	/** Every solver. Adding one adds a line here. */
	private static final List<Puzzle> ALL = List.of(BLAZE, BEAMS);

	// Settings. Volatile: written by the config screen, read by the tick and the
	// render thread. Defaults are here, at the field.
	private static volatile boolean enabled = true;
	private static volatile int drawDistance = 64;
	private static volatile boolean throughWalls = true;

	/** The {@code developer.logPuzzles} flag. */
	private static volatile boolean logging;

	/** Every running solver's marks, joined. Written by the client thread, read by the render thread. */
	private static volatile Marks marks = Marks.NONE;

	// Tick state, all indexed to ALL. Client thread only.
	private static final boolean[] running = new boolean[ALL.size()];
	private static final int[] sincePoll = new int[ALL.size()];

	/** Each running solver's switch as the last publish saw it, so turning one off clears it. */
	private static final boolean[] published = new boolean[ALL.size()];

	private static RoomFrame frame;
	private static int roomGeneration = -1;

	private Puzzles() {
	}

	/** Hands every signature to {@link RoomWatch}, so it can find the puzzle rooms. */
	public static void register() {
		List<RoomWatch.Claimant> claimants = new ArrayList<>();
		for (Puzzle puzzle : ALL) {
			if (!puzzle.signature().isEmpty()) {
				claimants.add(new RoomWatch.Claimant(puzzle.id(), puzzle.signature()));
			}
		}
		RoomWatch.claimants(claimants);
	}

	/** Client thread, every tick. Runs after {@link RoomWatch#tick}, so its room is this tick's. */
	public static void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (!enabled || !DungeonState.inClear() || level == null || client.player == null) {
			if (roomGeneration != -1 || marks != Marks.NONE) {
				stop();
			}
			return;
		}

		int now = RoomWatch.generation();
		if (now != roomGeneration) {
			roomGeneration = now;
			enter(level);
		}

		// Publish when a solver polled, and also when one was switched off since the
		// last publish: nothing else would clear what it left on screen.
		boolean republish = false;
		for (int i = 0; i < running.length; i++) {
			if (!running[i]) {
				continue;
			}
			Puzzle puzzle = ALL.get(i);
			if (puzzle.enabled() != published[i]) {
				republish = true;
			}
			if (!puzzle.enabled()) {
				continue;
			}
			if (++sincePoll[i] >= Math.max(1, puzzle.pollTicks())) {
				sincePoll[i] = 0;
				puzzle.tick(client, frame);
				republish = true;
			}
		}
		if (republish) {
			publish();
		}
	}

	/** A new room, frame or dungeon: tell the old solvers, start the ones this room wants. */
	private static void enter(ClientLevel level) {
		for (int i = 0; i < running.length; i++) {
			if (running[i]) {
				ALL.get(i).left();
				running[i] = false;
			}
		}

		frame = RoomWatch.frame();
		String claimed = RoomWatch.puzzle();
		for (int i = 0; i < running.length; i++) {
			Puzzle puzzle = ALL.get(i);
			boolean wants = puzzle.signature().isEmpty()
					|| (frame != null && Objects.equals(puzzle.id(), claimed));
			if (!wants) {
				continue;
			}
			puzzle.entered(puzzle.signature().isEmpty() ? null : frame, level);
			running[i] = true;
			// Poll on this tick, not a whole period from now.
			sincePoll[i] = Math.max(1, puzzle.pollTicks()) - 1;
			if (logging) {
				CherryPicking.LOGGER.info("Puzzles: {} entered, frame {}, polling every {} ticks",
						puzzle.id(), puzzle.signature().isEmpty() ? "none" : frame,
						puzzle.pollTicks());
			}
		}
		publish();
	}

	/** Joins the running solvers' marks into one snapshot. A disabled solver draws nothing. */
	private static void publish() {
		List<Mark> joined = new ArrayList<>();
		for (int i = 0; i < running.length; i++) {
			Puzzle puzzle = ALL.get(i);
			boolean on = running[i] && puzzle.enabled();
			published[i] = puzzle.enabled();
			if (on) {
				joined.addAll(puzzle.marks().marks());
			}
		}
		Marks now = joined.isEmpty() ? Marks.NONE : new Marks(joined);
		if (logging && now.marks().size() != marks.marks().size()) {
			CherryPicking.LOGGER.info("Puzzles: drawing {} marks", now.marks().size());
		}
		marks = now;
	}

	/** Out of the clear phase, or switched off: every solver forgets everything. */
	private static void stop() {
		ALL.forEach(Puzzle::reset);
		Arrays.fill(running, false);
		Arrays.fill(sincePoll, 0);
		Arrays.fill(published, false);
		frame = null;
		roomGeneration = -1;
		marks = Marks.NONE;
	}

	/** Whether {@code at} is inside {@code puzzles.drawDistance} of the player. */
	static boolean inReach(LocalPlayer player, Vec3 at) {
		double reach = drawDistance;
		return player.position().distanceToSqr(at) <= reach * reach;
	}

	/** What to draw. Never null, often empty. */
	public static Marks marks() {
		return marks;
	}

	public static boolean enabled() {
		return enabled;
	}

	/** Client thread: the settings screen. Turning it off clears at once. */
	public static void enabled(boolean value) {
		enabled = value;
		if (!value) {
			stop();
		}
	}

	public static int drawDistance() {
		return drawDistance;
	}

	public static void drawDistance(int value) {
		drawDistance = value;
	}

	/** Read by the render thread, once per frame. */
	public static boolean throughWalls() {
		return throughWalls;
	}

	public static void throughWalls(boolean value) {
		throughWalls = value;
	}

	/** Whether solvers write what they are doing to the game log. */
	public static boolean logging() {
		return logging;
	}

	public static void logging(boolean value) {
		logging = value;
	}
}
