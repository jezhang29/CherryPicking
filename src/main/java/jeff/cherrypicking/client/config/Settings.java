package jeff.cherrypicking.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jeff.cherrypicking.client.CleanExit;
import jeff.cherrypicking.client.cosmetics.Cosmetics;
import jeff.cherrypicking.client.cosmetics.Poller;
import jeff.cherrypicking.client.cosmetics.Publisher;
import jeff.cherrypicking.client.cosmetics.RelayClient;
import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.boss.Livid;
import jeff.cherrypicking.client.dungeon.boss.LividTitle;
import jeff.cherrypicking.client.dungeon.draw.Style;
import jeff.cherrypicking.client.dungeon.mob.MobKind;
import jeff.cherrypicking.client.dungeon.mob.StarMobWatch;
import jeff.cherrypicking.client.dungeon.puzzle.Blaze;
import jeff.cherrypicking.client.dungeon.puzzle.CreeperBeams;
import jeff.cherrypicking.client.dungeon.puzzle.Puzzles;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;
import jeff.cherrypicking.client.screen.ScreenSettings;
import jeff.cherrypicking.client.theme.Flavor;
import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

/**
 * Every setting and action the mod has, declared once.
 *
 * <p><b>This list is the config screen.</b> The screen walks it and builds the
 * tabs, groups and rows from what it finds; {@link ConfigFile} saves and loads
 * its settings by key. Neither knows what any particular entry is. So a new
 * tunable is added here and nowhere else, and it turns up in the screen and in
 * the saved file for free.
 *
 * <p><b>Adding one.</b> Give the live field a getter and a setter on the class
 * that already owns it, then add a line below with:
 * <ul>
 *   <li>a stable {@code key} - it is the name in the config file, so renaming
 *       one quietly discards what players had saved;</li>
 *   <li>a {@code label} a player would recognize;</li>
 *   <li>a {@code blurb} of <b>one or two short sentences</b> in ASD-STE100 Simplified Technical English
 *       with American spelling: what it does and why you would touch it, not how it
 *       is implemented;</li>
 *   <li>a {@link Section}, which decides where it lands.</li>
 * </ul>
 *
 * <p>Defaults are not written here. Each setting's default is whatever its owner
 * holds when this class is first touched, which is before anything has had a
 * chance to change it. That keeps one copy of every default, at the field it
 * belongs to, and makes Reset restore what the code actually ships with rather
 * than a number copied over here and left behind.
 *
 * <p><b>Dependencies.</b> Wrap registrations in {@link #when} to gray them out
 * while a master flag is off, so a card stays readable when its feature is
 * switched off.
 */
public final class Settings {
	private static final Supplier<Boolean> ALWAYS = () -> true;

	private static final List<Entry> ALL = new ArrayList<>();

	/** The condition {@link #when} is registering under; only used while this class loads. */
	private static Supplier<Boolean> dependency = ALWAYS;

	static {
		add("theme.flavor", "Color theme",
				"Sets the colors of this screen and of the markers in the world.",
				Section.THEME, new Control.Choice<>(Flavor.class, Flavor::label, Flavor::preview),
				Theme::flavor, Theme::flavor);
		choice("theme.accent", "Accent color", Section.THEME,
				"Sets the color of the selected tab and of the sliders.",
				Theme.Accent.class, Theme.Accent::label, Theme::accent, Theme::accent);

		whole("screen.cardWidth", "Card width", Section.SCREEN,
				"Sets the width of each column of settings. If the columns are wider, fewer columns fit across the screen.",
				new Control.Whole(ScreenSettings.MIN_CARD_WIDTH, ScreenSettings.MAX_CARD_WIDTH, 10,
						"px"),
				ScreenSettings::cardWidth, ScreenSettings::cardWidth);
		flag("screen.compact", "Compact rows", Section.SCREEN,
				"Puts the rows closer together. More settings then fit on the screen before you must scroll.",
				ScreenSettings::compact, ScreenSettings::compact);
		flag("screen.tooltips", "Show descriptions on hover", Section.SCREEN,
				"Shows a short description, like this one, when you hover over a setting.",
				ScreenSettings::tooltips, ScreenSettings::tooltips);

		flag("puzzles.enabled", "Solve puzzles", Section.PUZZLES,
				"Shows the solution to each dungeon puzzle when you go into its room.",
				Puzzles::enabled, Puzzles::enabled);
		when(Puzzles::enabled, () -> {
			whole("puzzles.drawDistance", "Draw distance", Section.PUZZLES,
					"Sets the maximum distance at which you can see puzzle markers.",
					new Control.Whole(16, 128, 8, "blocks"),
					Puzzles::drawDistance, Puzzles::drawDistance);
			flag("puzzles.throughWalls", "Draw through walls", Section.PUZZLES,
					"Shows puzzle markers through blocks. If this setting is off, you see only the markers in your line of sight.",
					Puzzles::throughWalls, Puzzles::throughWalls);

			Blaze blaze = Puzzles.BLAZE;
			flag("blaze.enabled", "Solve Blaze", Section.BLAZE,
					"Puts a box around each blaze to show the order in which you must kill them.",
					blaze::enabled, blaze::enabled);
			when(blaze::enabled, () -> {
				choice("blaze.style", "Box style", Section.BLAZE,
						"Sets the box type: filled, outline, or both.",
						Style.class, Style::label, blaze::style, blaze::style);
				flag("blaze.nextLine", "Line to the next blaze", Section.BLAZE,
						"Shows a line from each blaze to the next blaze that you must kill.",
						blaze::nextLine, blaze::nextLine);
				whole("blaze.lines", "Lines", Section.BLAZE,
						"Sets the number of lines to the next blazes.",
						new Control.Whole(1, 10, 1, ""), blaze::lines, blaze::lines);
				real("blaze.lineWidth", "Line width", Section.BLAZE,
						"Sets the width of the lines between the blazes.",
						new Control.Real(0.5, 5.0, 0.1, Control.Format.PLAIN),
						blaze::lineWidth, blaze::lineWidth);
				boxColor("blaze.firstColor", "First color", Section.BLAZE,
						"Sets the color of the blaze that you must kill now.",
						blaze::firstColor, blaze::firstColor);
				boxColor("blaze.secondColor", "Second color", Section.BLAZE,
						"Sets the color of the blaze that you must kill next.",
						blaze::secondColor, blaze::secondColor);
				boxColor("blaze.thirdColor", "Third color", Section.BLAZE,
						"Sets the color of the blaze that you must kill third.",
						blaze::thirdColor, blaze::thirdColor);
				boxColor("blaze.otherColor", "Other color", Section.BLAZE,
						"Sets the color of all the other blazes.",
						blaze::otherColor, blaze::otherColor);
				choice("blaze.order", "Order", Section.BLAZE,
						"Sets which blaze you kill first: the lowest health or the highest health. Auto uses the position of the chest to find the order.",
						Blaze.Order.class, Blaze.Order::label, blaze::order, blaze::order);
				flag("blaze.announce", "Say when solved", Section.BLAZE,
						"Shows a message in your chat when you kill the last blaze. Other players cannot see this message.",
						blaze::announce, blaze::announce);
				action("blaze.reset", "Reset the Blaze solver", Section.BLAZE,
						"Removes the current solution and examines the room again. Click this button if the boxes are not correct.",
						blaze::reset);
			});

			CreeperBeams beams = Puzzles.BEAMS;
			flag("beams.enabled", "Solve Creeper Beams", Section.BEAMS,
					"Finds the pairs of lit sea lanterns. Each pair has a different color.",
					beams::enabled, beams::enabled);
			when(beams::enabled, () -> {
				choice("beams.style", "Box style", Section.BEAMS,
						"Sets the type of the lantern boxes: filled, outline, or both.",
						Style.class, Style::label, beams::style, beams::style);
				flag("beams.tracer", "Line between pairs", Section.BEAMS,
						"Shows a line between the two lanterns of each pair.",
						beams::tracer, beams::tracer);
				// The only opacity sliders outside a picker: the lantern colors are a fixed cycle,
				// so there is no color picker here to carry them.
				real("beams.alpha", "Outline opacity", Section.BEAMS,
						"Sets the opacity of the box outlines. You cannot change the pair colors, so this setting controls their opacity.",
						new Control.Real(0.0, 1.0, 0.05, Control.Format.PERCENT),
						beams::alpha, beams::alpha);
				real("beams.fillAlpha", "Fill opacity", Section.BEAMS,
						"Sets the opacity of the inner area of each lantern box.",
						new Control.Real(0.0, 1.0, 0.05, Control.Format.PERCENT),
						beams::fillAlpha, beams::fillAlpha);
				action("beams.reset", "Reset the Creeper Beams solver", Section.BEAMS,
						"Removes the current solution and examines the lanterns again. Click this button if the pairs are not correct.",
						beams::reset);
			});
		});

		flag("livid.enabled", "Find the real Livid", Section.LIVID,
				"Puts a box around the real Livid in the Floor 5 boss fight. You can see the box through walls.",
				Livid::enabled, Livid::enabled);
		when(Livid::enabled, () -> {
			choice("livid.style", "Box style", Section.LIVID,
					"Sets the box type: filled, outline, or both.",
					Style.class, Style::label, Livid::style, Livid::style);
			boxColor("livid.boxColor", "Box color", Section.LIVID,
					"Sets the color of the box around the real Livid.",
					Livid::boxColor, Livid::boxColor);
			flag("livid.announce", "Name it in chat", Section.LIVID,
					"Shows the name of the real Livid in your chat. Other players cannot see this message.",
					Livid::announce, Livid::announce);
			flag("livid.hideWhenBlind", "Hide while blinded", Section.LIVID,
					"Hides the box while Livid makes you blind. The box helps most at that time, so keep this setting off if you need the box.",
					Livid::hideWhenBlind, Livid::hideWhenBlind);
		});

		flag("livid.title.enabled", "Show the color on screen", Section.LIVID_TITLE,
				"Shows the color of the real Livid in large text at the center of your screen when the mod finds it.",
				LividTitle::enabled, LividTitle::enabled);
		when(LividTitle::enabled, () -> {
			real("livid.title.scale", "Size", Section.LIVID_TITLE,
					"Sets the size of the color text.",
					new Control.Real(LividTitle.MIN_SCALE, LividTitle.MAX_SCALE, 0.25, Control.Format.PLAIN),
					LividTitle::scale, LividTitle::scale);
			real("livid.title.seconds", "How long it stays", Section.LIVID_TITLE,
					"Sets the time, in seconds, that the color text stays on the screen.",
					new Control.Real(LividTitle.MIN_SECONDS, LividTitle.MAX_SECONDS, 0.5,
							Control.Format.SECONDS),
					LividTitle::seconds, LividTitle::seconds);
		});

		flag("mobs.enabled", "Box starred mobs", Section.STAR_MOBS,
				"Puts a colored box around each starred mob in your current room. You can see the box through walls.",
				StarMobWatch::enabled, StarMobWatch::enabled);
		when(StarMobWatch::enabled, () -> {
			flag("mobs.labels", "Name starred mobs", Section.STAR_MOBS,
					"Shows the name of the mob and its distance above the box.",
					StarMobWatch::labels, StarMobWatch::labels);
			flag("mobs.hiddenFels", "Box hidden Fels", Section.STAR_MOBS,
					"Puts a box around each hidden Fels before it comes out. A hidden Fels does not show a star, so this setting finds it early.",
					StarMobWatch::hiddenFels, StarMobWatch::hiddenFels);
			kindColor("mobs.color", MobKind.STARRED,
					"Sets the box color for starred mobs that are not Fels or minibosses.");
			kindColor("mobs.felsColor", MobKind.FELS,
					"Sets the box color for all Fels, hidden or not hidden.");
			kindColor("mobs.minibossColor", MobKind.MINIBOSS,
					"Sets the box color for Shadow Assassins, Lost Adventurers, Angry Archaeologists, and King Midas.");
		});

		flag("friendCosmetics.enabled", "Show friends' cosmetics", Section.FRIEND_COSMETICS,
				"Shows your friends' Skyblocker armor looks, and shares yours with them. This uses the relay on the internet.",
				Cosmetics::enabled, Cosmetics::enabled);
		when(Cosmetics::enabled, () -> {
			text("friendCosmetics.friends", "Friends", Section.FRIEND_COSMETICS,
					"The Minecraft names of the friends whose armor looks you want to see, separated by commas. Add your own name to see your shared looks on yourself.",
					new Control.Text(200), Poller::friends, Poller::friends);
			whole("friendCosmetics.pollSeconds", "Check every", Section.FRIEND_COSMETICS,
					"How often to get your friends' looks when the live link to the relay is down. With the"
							+ " link, a change comes at once.",
					new Control.Whole(30, 600, 30, "s"), Poller::pollSeconds, Poller::pollSeconds);
			flag("friendCosmetics.share", "Share my cosmetics", Section.FRIEND_COSMETICS,
					"Sends your own Skyblocker armor looks to the relay, so your friends can see them.",
					Publisher::share, Publisher::share);
			text("friendCosmetics.relayUrl", "Relay address", Section.FRIEND_COSMETICS,
					"The web address of the relay that passes looks between you and your friends. It must start with https://.",
					new Control.Text(200), RelayClient::url, RelayClient::url);
		});

		flag("quitting.cleanExit", "Quit cleanly", Section.QUITTING,
				"Closes the game immediately when you quit. If this setting is off, a different mod can stop the game from closing and cause a crash.",
				CleanExit::enabled, CleanExit::enabled);

		flag("developer.forceDungeon", "Force the Catacombs", Section.DEVELOPER,
				"Makes the mod operate as if you are in a dungeon at all locations. Use this setting to test the dungeon features.",
				DungeonState::forced, DungeonState::forced);
		flag("developer.drawRoomFrame", "Draw the room frame", Section.DEVELOPER,
				"Shows an outline of your current room. When the mod finds a puzzle, it also shows the corner and the direction of the room.",
				RoomWatch::drawRoomFrame, RoomWatch::drawRoomFrame);
		flag("developer.logRoomFrame", "Log room frames", Section.DEVELOPER,
				"Writes each room that you go into and the dungeon sidebar to the game log. Use this data for bug reports.",
				RoomWatch::logRoomFrame, RoomWatch::logRoomFrame);
		flag("developer.logSignatures", "Log signature checks", Section.DEVELOPER,
				"Writes to the game log each block that the mod examines to find puzzle rooms.",
				RoomWatch::logSignatures, RoomWatch::logSignatures);
		flag("developer.logPuzzles", "Log puzzle solvers", Section.DEVELOPER,
				"Writes to the game log what each puzzle solver does in each room.",
				Puzzles::logging, Puzzles::logging);
	}

	private Settings() {
	}

	/** Every entry, in the order the screen draws them. The screen's view. */
	public static List<Entry> all() {
		return List.copyOf(ALL);
	}

	/** Only the entries that hold a value. The config file's view. */
	public static List<Setting<?>> settings() {
		List<Setting<?>> settings = new ArrayList<>();
		for (Entry entry : ALL) {
			if (entry instanceof Setting<?> setting) {
				settings.add(setting);
			}
		}
		return settings;
	}

	public static Optional<Setting<?>> byKey(String key) {
		return settings().stream().filter(setting -> setting.key().equals(key)).findFirst();
	}

	/** Puts every setting back to its default. */
	public static void resetAll() {
		settings().forEach(Setting::reset);
	}

	/**
	 * Puts {@code settings} back to their defaults.
	 *
	 * @return puts them back to the values they held before this call
	 */
	public static Runnable reset(List<Setting<?>> settings) {
		List<Runnable> undo = settings.stream().map(Settings::keep).toList();
		settings.forEach(Setting::reset);
		return () -> undo.forEach(Runnable::run);
	}

	/** Captures the value in force now, with its type, so it can be written back. */
	private static <T> Runnable keep(Setting<T> setting) {
		T kept = setting.value();
		return () -> setting.value(kept);
	}

	/**
	 * {@code BOTTOM_RIGHT} reads as "Bottom right". Here for the next {@code choice} over an enum
	 * that has no {@code label()} of its own; every current one has.
	 */
	private static String pretty(Enum<?> value) {
		String words = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	/**
	 * Registers everything inside {@code registrations} as available only while
	 * {@code condition} holds. Nested calls must all hold.
	 */
	private static void when(Supplier<Boolean> condition, Runnable registrations) {
		Supplier<Boolean> outer = dependency;
		dependency = outer == ALWAYS ? condition : () -> outer.get() && condition.get();
		try {
			registrations.run();
		} finally {
			dependency = outer;
		}
	}

	// One registration helper per kind of entry. Not every one has a caller yet;
	// they are here so the first slider or color is one line above and no
	// screen code, which is the whole point of the registry.
	private static void flag(String key, String label, Section section, String blurb,
			Supplier<Boolean> read, Consumer<Boolean> write) {
		add(key, label, blurb, section, new Control.Flag(), read, write);
	}

	private static void whole(String key, String label, Section section, String blurb,
			Control.Whole control, Supplier<Integer> read, Consumer<Integer> write) {
		add(key, label, blurb, section, control, read, write);
	}

	private static void real(String key, String label, Section section, String blurb,
			Control.Real control, Supplier<Double> read, Consumer<Double> write) {
		add(key, label, blurb, section, control, read, write);
	}

	private static <E extends Enum<E>> void choice(String key, String label, Section section,
			String blurb, Class<E> type, Function<E, String> naming,
			Supplier<E> read, Consumer<E> write) {
		add(key, label, blurb, section, new Control.Choice<>(type, naming), read, write);
	}

	/** Every color carries its own opacity, set on the slider in its picker. */
	private static void color(String key, String label, Section section, String blurb,
			Supplier<Swatch> read, Consumer<Swatch> write) {
		add(key, label, blurb, section, new Control.Color(false), read, write);
	}

	/** A box's color: its picker has an outline slider and a fill slider, both saved with it. */
	private static void boxColor(String key, String label, Section section, String blurb,
			Supplier<Swatch> read, Consumer<Swatch> write) {
		add(key, label, blurb, section, new Control.Color(true), read, write);
	}

	private static void kindColor(String key, MobKind kind, String blurb) {
		boxColor(key, kind.label() + " color", Section.STAR_MOBS, blurb, kind::color, kind::color);
	}

	private static void text(String key, String label, Section section, String blurb,
			Control.Text control, Supplier<String> read, Consumer<String> write) {
		add(key, label, blurb, section, control, read, write);
	}

	/** A button. Start the label with the verb; the button shows the first word. */
	private static void action(String key, String label, Section section, String blurb,
			Runnable run) {
		ALL.add(new Action(key, label, blurb, section, run, dependency));
	}

	/** Registers one setting, taking its owner's current value as the default. */
	private static <T> void add(String key, String label, String blurb, Section section,
			Control<T> control, Supplier<T> read, Consumer<T> write) {
		ALL.add(new Setting<>(key, label, blurb, section, control, read.get(), read, write, dependency));
	}
}
