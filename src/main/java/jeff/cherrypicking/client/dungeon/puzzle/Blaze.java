package jeff.cherrypicking.client.dungeon.puzzle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.Chat;
import jeff.cherrypicking.client.dungeon.draw.Marks;
import jeff.cherrypicking.client.dungeon.draw.Style;
import jeff.cherrypicking.client.dungeon.room.RoomFrame;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;
import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Blaze (Higher or Lower): orders the ten blazes by health and marks the next one.
 *
 * <p>After Odin's {@code BlazeSolver}. See docs/dungeon-layer.md §3.4 and §12.4.
 *
 * <p><b>No frame.</b> Each blaze carries a name stand, {@code [Lv15]  Blaze 1,234/1,234❤} - note
 * the two spaces. The solver runs in every room and finds its puzzle by those stands, keeping only
 * the ones in the player's room.
 *
 * <p><b>Direction.</b> Odin reads the room's name ("Lower Blaze" or "Higher Blaze") from its room
 * database. We have no room names, so the chest decides, per the wiki: it starts at the bottom of
 * its iron-bar chain for lowest health first, and at the top for highest health first. Found once
 * per room and then held, since the chest moves as blazes die. Unverified in game (§14.1);
 * {@code blaze.order} overrides it.
 *
 * <p><b>Solved</b> when the count reaches zero having last been one. {@code blaze.announce} then
 * prints a line in the player's own chat. Odin sends it to party chat instead; that would be the
 * mod speaking as the player, so it does not (§12.8).
 */
public final class Blaze implements Puzzle {
	/** Group 1 is the maximum health, with thousands commas. Odin's, verbatim. §14.8. */
	private static final Pattern HEALTH = Pattern.compile("^\\[Lv+\\d+]  Blaze [\\d,]+/([\\d,]+)❤$");

	// Odin's box: the stand's box, grown by (0.5, 1.0, 0.5) and moved down 1.0,
	// so it fits the blaze below the stand rather than the stand.
	private static final double INFLATE_XZ = 0.5;
	private static final double LIFT = -2.0;
	private static final double GROW_Y = 2.0;

	// Where the chest is looked for: the column around the room's centre, over the
	// heights the rooms use. §14.1.
	private static final int CHEST_RADIUS = 4;
	private static final int CHEST_BOTTOM = 12;
	private static final int CHEST_TOP = 140;
	/** Polls between two looks for the chest, and how many looks before giving up. */
	private static final int CHEST_RETRY_POLLS = 5;
	private static final int CHEST_TRIES = 12;

	private static final int NONE = -1;

	/** The order the blazes are killed in. */
	public enum Order {
		AUTO("Auto"),
		LOWEST_FIRST("Lowest health first"),
		HIGHEST_FIRST("Highest health first");

		private final String label;

		Order(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** One blaze, found this poll. */
	private record Found(int standId, int maxHealth, Vec3 at, double standHeight) {
	}

	// Settings. Volatile: written by the config screen, read by the tick. Defaults
	// are here, at the field. Colours after Odin's green, gold, red and white.
	private volatile boolean enabled = true;
	private volatile Style style = Style.FILLED_OUTLINE;
	private volatile boolean nextLine = true;
	private volatile int lines = 1;
	private volatile double lineWidth = 2.0;
	private volatile Swatch firstColour = Swatch.of("green");
	private volatile Swatch secondColour = Swatch.of("peach");
	private volatile Swatch thirdColour = Swatch.of("red");
	private volatile Swatch otherColour = Swatch.of("overlay1");
	private volatile Order order = Order.AUTO;
	private volatile boolean announce = false;

	private volatile Marks marks = Marks.NONE;

	// Room state. Client thread only.
	/** The chest's verdict for this room: lowest first, highest first, or not yet known. */
	private Order chest = Order.AUTO;
	private int chestTries;
	private int sinceChestTry;
	private int lastCount = NONE;
	private boolean solved;

	Blaze() {
	}

	@Override
	public String id() {
		return "blaze";
	}

	@Override
	public String label() {
		return "Blaze";
	}

	@Override
	public List<RoomWatch.Signature> signature() {
		return List.of();
	}

	@Override
	public void entered(RoomFrame frame, ClientLevel level) {
		reset();
	}

	@Override
	public void tick(Minecraft client, RoomFrame frame) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || solved) {
			marks = Marks.NONE;
			return;
		}

		List<Found> blazes = find(level);
		int count = blazes.size();
		if (count == 0) {
			if (lastCount == 1) {
				solved();
			}
			if (lastCount != NONE) {
				lastCount = 0;
			}
			marks = Marks.NONE;
			return;
		}
		lastCount = count;

		Order direction = order != Order.AUTO ? order : chest(level, blazes);
		if (direction == Order.AUTO) {
			// The chest is not found yet. Box them all, in no order, rather than guess.
			Marks.Builder builder = Marks.builder();
			for (Found blaze : blazes) {
				box(builder, player, blaze, otherColour);
			}
			marks = builder.build();
			return;
		}

		Comparator<Found> byHealth = Comparator.comparingInt(Found::maxHealth);
		if (direction == Order.HIGHEST_FIRST) {
			byHealth = byHealth.reversed();
		}
		blazes.sort(byHealth.thenComparingInt(Found::standId));
		marks = draw(player, blazes);
	}

	/** Every blaze stand in the player's room. Client thread: reads the room. */
	private static List<Found> find(ClientLevel level) {
		List<Found> found = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand) || stand.isRemoved() || !stand.hasCustomName()) {
				continue;
			}
			if (!RoomWatch.inRoom(stand.getX(), stand.getZ())) {
				continue;
			}
			Component custom = stand.getCustomName();
			String text = custom == null ? null : ChatFormatting.stripFormatting(custom.getString());
			if (text == null || !text.contains("Blaze")) {
				continue;
			}
			Matcher matcher = HEALTH.matcher(text);
			if (!matcher.matches()) {
				continue;
			}
			try {
				int health = Integer.parseInt(matcher.group(1).replace(",", ""));
				found.add(new Found(stand.getId(), health, stand.position(), stand.getBbHeight()));
			} catch (NumberFormatException tooLarge) {
				// Not a puzzle blaze.
			}
		}
		return found;
	}

	/**
	 * The chest's verdict: below the blazes' middle is lowest first, above is highest first.
	 * Looked for every {@value #CHEST_RETRY_POLLS} polls, {@value #CHEST_TRIES} times, since the
	 * room loads over several ticks.
	 */
	private Order chest(ClientLevel level, List<Found> blazes) {
		if (chest != Order.AUTO || chestTries >= CHEST_TRIES) {
			return chest;
		}
		if (sinceChestTry++ % CHEST_RETRY_POLLS != 0) {
			return chest;
		}
		chestTries++;

		double low = Double.MAX_VALUE;
		double high = -Double.MAX_VALUE;
		for (Found blaze : blazes) {
			low = Math.min(low, blaze.at().y);
			high = Math.max(high, blaze.at().y);
		}
		double middle = (low + high) / 2.0;

		// The room's centre, from the first blaze's tile.
		Vec3 first = blazes.getFirst().at();
		int centreX = ((BlockPos.containing(first).getX() + 201) >> 5) * 32 - 185;
		int centreZ = ((BlockPos.containing(first).getZ() + 201) >> 5) * 32 - 185;

		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int x = centreX - CHEST_RADIUS; x <= centreX + CHEST_RADIUS; x++) {
			for (int z = centreZ - CHEST_RADIUS; z <= centreZ + CHEST_RADIUS; z++) {
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				for (int y = CHEST_BOTTOM; y <= CHEST_TOP; y++) {
					BlockState state = level.getBlockState(at.set(x, y, z));
					if (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST)) {
						chest = y < middle ? Order.LOWEST_FIRST : Order.HIGHEST_FIRST;
						CherryPicking.LOGGER.info("Blaze: chest at {} against the blazes' middle y {} -> {}",
								at.toShortString(), String.format(Locale.ROOT, "%.1f", middle),
								chest.label());
						return chest;
					}
				}
			}
		}
		if (chestTries == CHEST_TRIES) {
			CherryPicking.LOGGER.info("Blaze: no chest within {} of centre x {}, z {}; set blaze.order.",
					CHEST_RADIUS, centreX, centreZ);
		}
		return chest;
	}

	/** One box per blaze, in kill order, and the lines from each to the next. */
	private Marks draw(LocalPlayer player, List<Found> blazes) {
		Marks.Builder builder = Marks.builder();
		float width = (float) lineWidth;

		for (int i = 0; i < blazes.size(); i++) {
			Found blaze = blazes.get(i);
			Swatch colour = switch (i) {
				case 0 -> firstColour;
				case 1 -> secondColour;
				case 2 -> thirdColour;
				default -> otherColour;
			};
			box(builder, player, blaze, colour);
			if (nextLine && i > 0 && i <= lines) {
				builder.line(blazes.get(i - 1).at(), middle(blaze), Theme.resolve(colour), width);
			}
		}
		return builder.build();
	}

	private void box(Marks.Builder builder, LocalPlayer player, Found blaze, Swatch colour) {
		if (Puzzles.inReach(player, blaze.at())) {
			builder.entityBox(blaze.standId(), INFLATE_XZ, LIFT, blaze.standHeight() + GROW_Y, colour, style);
		}
	}

	/** The middle of the blaze's box. */
	private static Vec3 middle(Found blaze) {
		return blaze.at().add(0, LIFT + (blaze.standHeight() + GROW_Y) / 2.0, 0);
	}

	private void solved() {
		solved = true;
		CherryPicking.LOGGER.info("Blaze: puzzle solved.");
		if (announce) {
			Chat.say(Component.literal("Blaze puzzle solved!")
					.withStyle(ChatFormatting.GREEN));
		}
	}

	@Override
	public Marks marks() {
		return marks;
	}

	@Override
	public void reset() {
		chest = Order.AUTO;
		chestTries = 0;
		sinceChestTry = 0;
		lastCount = NONE;
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

	public boolean nextLine() {
		return nextLine;
	}

	public void nextLine(boolean value) {
		nextLine = value;
	}

	public int lines() {
		return lines;
	}

	public void lines(int value) {
		lines = value;
	}

	public double lineWidth() {
		return lineWidth;
	}

	public void lineWidth(double value) {
		lineWidth = value;
	}

	public Swatch firstColour() {
		return firstColour;
	}

	public void firstColour(Swatch value) {
		firstColour = value;
	}

	public Swatch secondColour() {
		return secondColour;
	}

	public void secondColour(Swatch value) {
		secondColour = value;
	}

	public Swatch thirdColour() {
		return thirdColour;
	}

	public void thirdColour(Swatch value) {
		thirdColour = value;
	}

	public Swatch otherColour() {
		return otherColour;
	}

	public void otherColour(Swatch value) {
		otherColour = value;
	}

	public Order order() {
		return order;
	}

	public void order(Order value) {
		order = value;
	}

	public boolean announce() {
		return announce;
	}

	public void announce(boolean value) {
		announce = value;
	}
}
