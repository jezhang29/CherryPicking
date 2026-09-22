package jeff.cherrypicking.client.dungeon.boss;

import java.util.Arrays;
import java.util.OptionalInt;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.Chat;
import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.draw.Marks;
import jeff.cherrypicking.client.dungeon.draw.Style;
import jeff.cherrypicking.client.theme.Swatch;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the real Livid in the Floor 5 boss.
 *
 * <p>Written from the game facts in {@code docs/dungeon-layer.md} §3.8, not
 * from Devonian's code, which is GPL-3.0.
 *
 * <p><b>The wool names the real one.</b> When the fight starts, a wool block in
 * the arena ceiling turns the real Livid's colour. The Livids are player
 * entities named {@code "<Name> Livid"}; the real one is the one whose name
 * matches the wool. The arena is at fixed world coordinates, so no room frame is
 * involved.
 *
 * <p><b>Named on the change, not on the first read.</b> The wool has a resting
 * colour before the fight, so reading it on arrival names the wrong Livid. Each
 * spot's first loaded state is kept, and the first spot to change to one of the
 * nine wools names the real one, on that tick. Skyblocker's rule is the fallback,
 * for a real Livid whose colour is the resting one: two seconds after blindness
 * first lands, the ceiling wool is trusted as it reads. Nothing is boxed before
 * then; a guess would box the wrong Livid eight times in nine.
 *
 * <p><b>Locked once found.</b> On M5 the real Livid can change colour later in
 * the fight (Skyblocker), so the first entity found under the named colour is
 * kept by id, and later wool changes are logged but not followed.
 *
 * <p><b>The colour, not the name.</b> Hypixel's nine Livids are called Frog,
 * Hockey, Arcade and so on, which say nothing about what to look for. Both the
 * chat line and {@link LividTitle} lead with the wool colour the Livid is
 * wearing, and keep the Hypixel name after it for anyone who wants it.
 *
 * <p>Display-only: it reads one block, reads the entity list and draws a box.
 * {@code livid.announce} prints to the player's own chat only.
 */
public final class Livid {
	/** The ceiling wool, as Skyblocker reads it. Absolute. */
	private static final BlockPos CEILING = new BlockPos(5, 110, 42);

	/** Every spot that is watched for a change: the ceiling, then Odin's. §14.3. */
	private static final BlockPos[] WOOL = {CEILING, new BlockPos(5, 108, 43)};

	/** Skyblocker's two seconds from the first blindness to a settled wool. */
	private static final int SETTLE_TICKS = 40;

	private static final int LIVID_FLOOR = 5;

	/** A little wider than the player model, so the box does not sit on the skin. */
	private static final double INFLATE = 0.1;

	/**
	 * The nine Livids: Hypixel's name, the colour a player sees, and the wool
	 * blocks that name each.
	 *
	 * <p>The two greens are spelled out rather than left as Minecraft's "green"
	 * and "lime", because on screen they are dark green and light green and a
	 * player picking between them at a glance needs the word to say which.
	 */
	public enum Name {
		VENDETTA("Vendetta", "WHITE", ChatFormatting.WHITE, 0xFFFFFF, DyeColor.WHITE),
		// Odin reads magenta, Devonian pink. Both are mapped; §14.3.
		CROSSED("Crossed", "PINK", ChatFormatting.LIGHT_PURPLE, 0xFF7FEF, DyeColor.MAGENTA, DyeColor.PINK),
		ARCADE("Arcade", "YELLOW", ChatFormatting.YELLOW, 0xFFFF55, DyeColor.YELLOW),
		SMILE("Smile", "LIGHT GREEN", ChatFormatting.GREEN, 0x7FFF55, DyeColor.LIME),
		DOCTOR("Doctor", "GREY", ChatFormatting.GRAY, 0xBBBBBB, DyeColor.GRAY),
		PURPLE("Purple", "PURPLE", ChatFormatting.DARK_PURPLE, 0xC055FF, DyeColor.PURPLE),
		FROG("Frog", "DARK GREEN", ChatFormatting.DARK_GREEN, 0x22BB22, DyeColor.GREEN),
		SCREAM("Scream", "BLUE", ChatFormatting.BLUE, 0x5555FF, DyeColor.BLUE),
		HOCKEY("Hockey", "RED", ChatFormatting.RED, 0xFF5555, DyeColor.RED);

		private final String label;
		private final String colour;
		private final ChatFormatting chatColour;
		private final int rgb;
		private final String entityName;
		private final DyeColor[] wool;

		Name(String label, String colour, ChatFormatting chatColour, int rgb, DyeColor... wool) {
			this.label = label;
			this.colour = colour;
			this.chatColour = chatColour;
			this.rgb = rgb;
			this.entityName = label + " Livid";
			this.wool = wool;
		}

		/** The Livid this block names, or {@code null} when it is not one of the nine wools. */
		static Name of(BlockState state) {
			for (Name name : values()) {
				for (DyeColor colour : name.wool) {
					if (state.is(Blocks.WOOL.pick(colour))) {
						return name;
					}
				}
			}
			return null;
		}

		/** Hypixel's name for it, e.g. {@code "Frog"}. */
		public String label() {
			return label;
		}

		/** What the player sees it wearing, e.g. {@code "DARK GREEN"}. */
		public String colour() {
			return colour;
		}

		/** That colour in chat. */
		public ChatFormatting chatColour() {
			return chatColour;
		}

		/** That colour on screen, brightened so it reads over the dark arena. */
		public int rgb() {
			return rgb;
		}
	}

	// Settings. Volatile: written by the config screen, read by the tick and the
	// render thread. Defaults are here, at the field.
	private static volatile boolean enabled = true;
	private static volatile Style style = Style.FILLED_OUTLINE;
	/** Literal, like the starred-mob boxes: it is drawn over dark stone. */
	private static volatile Swatch boxColour = Swatch.of(0xFF55FF55);
	private static volatile boolean announce = true;
	/** Off: blindness is exactly when the box is needed, and the outline stays lit through it. */
	private static volatile boolean hideWhenBlind = false;

	/** What to draw. Written by the client thread, read by the render thread. */
	private static volatile Marks marks = Marks.NONE;

	// Tick state. Client thread only.
	private static ClientLevel lastLevel;
	/** The Livid the wool named; meaningful only once {@link #read}. */
	private static Name real = Name.HOCKEY;
	/** Whether the wool has named a Livid yet this fight. */
	private static boolean read;
	/** Each {@link #WOOL} spot's first loaded state this fight; {@code null} until loaded. */
	private static final BlockState[] resting = new BlockState[WOOL.length];
	/** Ticks since blindness first landed in the boss; {@code -1} before it has. */
	private static int sinceBlind = -1;
	/** The real Livid's entity id once found, or {@code -1}. */
	private static int lividId = -1;

	private Livid() {
	}

	/** Client thread, every tick. One volatile read outside the Catacombs. */
	public static void tick(Minecraft client) {
		if (!DungeonState.inCatacombs()) {
			if (lastLevel != null || marks != Marks.NONE) {
				reset();
			}
			return;
		}
		// One run is one level, so a new level is the only reset inside the
		// Catacombs. Not DungeonState.generation(): crossing the boss door bumps
		// it, the fight-start line can arrive either side of that, and a bump
		// mid-fight would take the changed wool for the resting one.
		if (client.level != lastLevel) {
			reset();
			lastLevel = client.level;
		}

		OptionalInt floor = DungeonState.floor();
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (!enabled || !DungeonState.inBoss() || floor.orElse(-1) != LIVID_FLOOR
				|| level == null || player == null) {
			marks = Marks.NONE;
			return;
		}

		boolean blind = player.hasEffect(MobEffects.BLINDNESS);
		if (blind && sinceBlind < 0) {
			sinceBlind = 0;
		} else if (sinceBlind >= 0) {
			sinceBlind++;
		}
		readWool(level);

		if (!read || (hideWhenBlind && blind)) {
			marks = Marks.NONE;
			return;
		}

		Player livid = find(level);
		marks = livid == null ? Marks.NONE : Marks.builder()
				.entityBox(livid.getId(), INFLATE, 0, 0, boxColour, style)
				.build();
	}

	/**
	 * The wool reads, every tick. The first spot to change from its resting state
	 * to one of the nine wools names the real Livid; after that a change is only
	 * logged, for §14.3.
	 */
	private static void readWool(ClientLevel level) {
		for (int i = 0; i < WOOL.length; i++) {
			if (!level.isLoaded(WOOL[i])) {
				continue;
			}
			BlockState state = level.getBlockState(WOOL[i]);
			if (resting[i] == null) {
				resting[i] = state;
				continue;
			}
			Name named = Name.of(state);
			if (named == null || state == resting[i]) {
				continue;
			}
			resting[i] = state;
			if (read) {
				if (named != real) {
					CherryPicking.LOGGER.info("Livid: the wool at {} changed to {}; still boxing {}.",
							WOOL[i].toShortString(), named.label(), real.label());
				}
				continue;
			}
			name(named, "the wool at " + WOOL[i].toShortString() + " changed");
		}

		if (!read && sinceBlind >= SETTLE_TICKS && level.isLoaded(CEILING)) {
			Name named = Name.of(level.getBlockState(CEILING));
			if (named != null) {
				name(named, "the ceiling wool, two seconds after blindness");
			}
		}
	}

	private static void name(Name named, String why) {
		read = true;
		real = named;
		CherryPicking.LOGGER.info("Livid: {} names {} ({}).", why, named.colour(), named.label());
		if (announce) {
			// The colour first and in its own colour: that is what the player looks for. The
			// Hypixel name follows, dimmed, for anyone reading it out to a party.
			Chat.say(Component.literal("Real Livid: ")
					.withStyle(ChatFormatting.GRAY)
					.append(Component.literal(named.colour()).withStyle(named.chatColour()))
					.append(Component.literal(" (" + named.label() + ")")
							.withStyle(ChatFormatting.DARK_GRAY)));
		}
		LividTitle.show(named);
	}

	/** The real Livid: the locked entity while it lives, else the first one named after it. */
	private static Player find(ClientLevel level) {
		if (lividId >= 0 && level.getEntity(lividId) instanceof Player locked && !locked.isRemoved()) {
			return locked;
		}
		String wanted = real.entityName;
		for (Player candidate : level.players()) {
			if (!candidate.isRemoved() && wanted.equals(candidate.getName().getString())) {
				lividId = candidate.getId();
				return candidate;
			}
		}
		return null;
	}

	/** Everything the wool and the entity search found. */
	private static void forgetFight() {
		real = Name.HOCKEY;
		read = false;
		Arrays.fill(resting, null);
		sinceBlind = -1;
		lividId = -1;
		marks = Marks.NONE;
	}

	private static void reset() {
		lastLevel = null;
		LividTitle.clear();
		forgetFight();
	}

	/** What to draw. Never null, often empty. */
	public static Marks marks() {
		return marks;
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void enabled(boolean value) {
		enabled = value;
		if (!value) {
			marks = Marks.NONE;
		}
	}

	public static Style style() {
		return style;
	}

	public static void style(Style value) {
		style = value;
	}

	public static Swatch boxColour() {
		return boxColour;
	}

	public static void boxColour(Swatch value) {
		boxColour = value;
	}

	public static boolean announce() {
		return announce;
	}

	public static void announce(boolean value) {
		announce = value;
	}

	public static boolean hideWhenBlind() {
		return hideWhenBlind;
	}

	public static void hideWhenBlind(boolean value) {
		hideWhenBlind = value;
	}
}
