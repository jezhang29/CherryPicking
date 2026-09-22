package jeff.cherrypicking.client.dungeon.puzzle;

import java.util.List;

import jeff.cherrypicking.client.dungeon.draw.Marks;
import jeff.cherrypicking.client.dungeon.room.RoomFrame;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * One puzzle solver.
 *
 * <p>A solver reads the world and publishes {@link Marks}; it writes no render code and
 * registers no listener. {@link Puzzles} decides when it runs. Adding a solver is one class, one
 * line in {@link Puzzles}, and its lines in the settings registry.
 *
 * <p>Every method is called on the client thread, except {@link #marks()}, which the render
 * thread reads. So a solver keeps its published marks in one volatile field and replaces it whole.
 *
 * <p>A solver never learns about the player's hands: it polls the blocks and entities that
 * change when the puzzle is worked, so a teammate's progress shows too. See
 * docs/dungeon-layer.md §12.3.
 *
 * <p><b>Two ways to forget.</b> {@link #left} is the room going out of view, which most solvers
 * treat as forgetting everything, because a room re-entered is a room to read again. A solver
 * whose state outlives the room - a running clock, a step already done - overrides it to keep
 * that and reset the rest. {@link #reset} is the whole forget: the dungeon ended, the feature was
 * switched off, or the player pressed the reset button. Never call one from the other's caller;
 * {@link Puzzles} picks.
 */
public interface Puzzle {
	/** Short and stable, for the log and {@link RoomWatch#puzzle()}: {@code "blaze"}. */
	String id();

	/** What a player calls it: {@code "Blaze"}. */
	String label();

	/** Its own switch, from the settings registry. */
	boolean enabled();

	/**
	 * Room-relative positions and the blocks expected there, which let {@link RoomWatch} find
	 * this puzzle's room and its frame. Empty means the solver needs no frame: it then runs in
	 * every room and finds its puzzle itself.
	 */
	List<RoomWatch.Signature> signature();

	/**
	 * How often {@link #tick} is called, in ticks. The default is a fifth of a second, which is
	 * enough for a puzzle read from blocks that a player changes by hand.
	 *
	 * <p>Return 1 for a solver that would miss something between polls: a position that has to
	 * be sampled every tick to be seen at all, a clock whose start must be timed, or a label
	 * that counts down and would step rather than run. Say why in the override - a solver that
	 * polls every tick five times as often as it needs to is the cost this default exists to
	 * avoid.
	 */
	default int pollTicks() {
		return 4;
	}

	/**
	 * A room was entered. Called once per room, before the first {@link #tick}.
	 *
	 * @param frame the room's frame; null for a solver with no {@link #signature()}
	 */
	void entered(RoomFrame frame, ClientLevel level);

	/**
	 * A poll, every {@link #pollTicks()} ticks, while the solver's room is the player's.
	 *
	 * @param frame as given to {@link #entered}
	 */
	void tick(Minecraft client, RoomFrame frame);

	/** What to draw. Never null, often {@link Marks#NONE}. Any thread. */
	Marks marks();

	/**
	 * The solver's room is no longer the player's: draw nothing, and forget what belongs to that
	 * visit. The default forgets everything, which is right for a solver that reads its answer
	 * out of the room's blocks each time it walks in.
	 */
	default void left() {
		reset();
	}

	/** Forget everything and draw nothing. */
	void reset();
}
