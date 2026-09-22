package jeff.cherrypicking.client.dungeon.puzzle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jeff.cherrypicking.client.dungeon.draw.DrawKit;
import jeff.cherrypicking.client.dungeon.draw.Marks;
import jeff.cherrypicking.client.dungeon.draw.Style;
import jeff.cherrypicking.client.dungeon.room.RoomFrame;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;
import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Creeper Beams: pairs the lit sea lanterns and marks each pair.
 *
 * <p>After Odin's {@code BeamsSolver}, with its {@code creeper-beams-solutions.json} bundled
 * verbatim (see {@link Solutions}). See docs/dungeon-layer.md §3.5 and §12.5.
 *
 * <p>The file lists every candidate pair. A pair is live while <b>both</b> ends are sea lanterns;
 * ends turn to prismarine as the puzzle is worked, so every poll re-reads all of them. That is 26
 * block reads, and it sees a teammate's beam as soon as the player's own.
 *
 * <p>Each pair has its own colour, from a cycle of eight palette accents in file order, so the
 * pairs are told apart at a glance and follow the flavour. Odin's cycle is gold, green, light
 * purple, dark aqua, yellow, dark red, white, dark purple; these are its nearest Catppuccin names.
 *
 * <p>Solved when the chest at {@code (15, 69, 15)} appears where there was air.
 */
public final class CreeperBeams implements Puzzle {
	private static final String[] CYCLE =
			{"peach", "green", "pink", "teal", "yellow", "red", "rosewater", "mauve"};

	/** Where the reward chest appears. Room-relative. */
	private static final int[] CHEST = {15, 69, 15};

	/** Grows each lantern's box a little, so it does not fight the block for depth. */
	private static final double INFLATE = 0.01;

	private static final List<RoomWatch.Signature> SIGNATURE = List.of(
			new RoomWatch.Signature(15, 74, 15, CreeperBeams::lanternOrPrismarine, "lantern or prismarine"),
			new RoomWatch.Signature(15, 84, 13, CreeperBeams::lanternOrPrismarine, "lantern or prismarine"));

	// Settings. Volatile: written by the config screen, read by the tick. Defaults
	// are here, at the field; Odin's.
	private volatile boolean enabled = true;
	private volatile Style style = Style.FILLED_OUTLINE;
	private volatile boolean tracer = false;
	private volatile double alpha = 0.7;
	private volatile double fillAlpha = 0.25;

	private volatile Marks marks = Marks.NONE;

	// Room state. Client thread only.
	private boolean sawAir;
	private boolean solved;

	CreeperBeams() {
	}

	private static boolean lanternOrPrismarine(BlockState state) {
		return state.is(Blocks.SEA_LANTERN) || state.is(Blocks.PRISMARINE);
	}

	@Override
	public String id() {
		return "beams";
	}

	@Override
	public String label() {
		return "Creeper Beams";
	}

	@Override
	public List<RoomWatch.Signature> signature() {
		return SIGNATURE;
	}

	@Override
	public void entered(RoomFrame frame, ClientLevel level) {
		reset();
	}

	@Override
	public void tick(Minecraft client, RoomFrame frame) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || frame == null || solved) {
			marks = Marks.NONE;
			return;
		}

		BlockState chest = level.getBlockState(frame.real(CHEST[0], CHEST[1], CHEST[2]));
		if (chest.isAir()) {
			sawAir = true;
		} else if (sawAir && chest.is(Blocks.CHEST)) {
			solved = true;
			marks = Marks.NONE;
			return;
		}

		int opacity = (int) Math.round(Math.clamp(alpha, 0.0, 1.0) * 255.0) << 24;
		int fillOpacity = (int) Math.round(Math.clamp(fillAlpha, 0.0, 1.0) * 255.0) << 24;
		Marks.Builder builder = Marks.builder();
		// The file lists one pair twice; the first end is enough to tell.
		Set<BlockPos> seen = new HashSet<>();
		List<int[]> rows = Solutions.creeperBeams();
		for (int index = 0; index < rows.size(); index++) {
			int[] row = rows.get(index);
			BlockPos one = frame.real(row[0], row[1], row[2]);
			BlockPos two = frame.real(row[3], row[4], row[5]);
			if (!seen.add(one) || !lit(level, one) || !lit(level, two)) {
				continue;
			}
			Vec3 from = Vec3.atCenterOf(one);
			Vec3 to = Vec3.atCenterOf(two);
			if (!Puzzles.inReach(player, from) && !Puzzles.inReach(player, to)) {
				continue;
			}
			int rgb = Theme.resolve(Swatch.of(CYCLE[index % CYCLE.length])) & 0xFFFFFF;
			int argb = opacity | rgb;
			int fill = fillOpacity | rgb;
			builder.box(new AABB(one).inflate(INFLATE), argb, fill, style)
					.box(new AABB(two).inflate(INFLATE), argb, fill, style);
			if (tracer) {
				builder.line(from, to, argb, DrawKit.EDGE_WIDTH);
			}
		}
		marks = builder.build();
	}

	private static boolean lit(ClientLevel level, BlockPos at) {
		return level.getBlockState(at).is(Blocks.SEA_LANTERN);
	}

	@Override
	public Marks marks() {
		return marks;
	}

	@Override
	public void reset() {
		sawAir = false;
		solved = false;
		marks = Marks.NONE;
	}

	@Override
	public boolean enabled() {
		return enabled;
	}

	/** Client thread: the settings screen. Turning it off forgets the room. */
	public void enabled(boolean value) {
		enabled = value;
		if (!value) {
			reset();
		}
	}

	public Style style() {
		return style;
	}

	public void style(Style value) {
		style = value;
	}

	public boolean tracer() {
		return tracer;
	}

	public void tracer(boolean value) {
		tracer = value;
	}

	public double alpha() {
		return alpha;
	}

	public void alpha(double value) {
		alpha = value;
	}

	public double fillAlpha() {
		return fillAlpha;
	}

	public void fillAlpha(double value) {
		fillAlpha = value;
	}
}
