package jeff.cherrypicking.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import jeff.cherrypicking.client.CleanExit;
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
import jeff.cherrypicking.client.theme.Flavour;
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
 *   <li>a {@code label} a player would recognise;</li>
 *   <li>a {@code blurb} of <b>one or two short, plain sentences</b>: what it does
 *       and why you would touch it, not how it is implemented;</li>
 *   <li>a {@link Section}, which decides where it lands.</li>
 * </ul>
 *
 * <p>Defaults are not written here. Each setting's default is whatever its owner
 * holds when this class is first touched, which is before anything has had a
 * chance to change it. That keeps one copy of every default, at the field it
 * belongs to, and makes Reset restore what the code actually ships with rather
 * than a number copied over here and left behind.
 *
 * <p><b>Dependencies.</b> Wrap registrations in {@link #when} to grey them out
 * while a master flag is off, so a card stays readable when its feature is
 * switched off.
 */
public final class Settings {
	private static final Supplier<Boolean> ALWAYS = () -> true;

	private static final List<Entry> ALL = new ArrayList<>();

	/** The condition {@link #when} is registering under; only used while this class loads. */
	private static Supplier<Boolean> dependency = ALWAYS;

	static {
		add("theme.flavour", "Colour theme",
				"Sets the colors for this screen and for the markers you see in the world.",
				Section.THEME, new Control.Choice<>(Flavour.class, Flavour::label, Flavour::preview),
				Theme::flavour, Theme::flavour);
		choice("theme.accent", "Accent colour", Section.THEME,
				"Picks the color for the selected tab and the sliders.",
				Theme.Accent.class, Theme.Accent::label, Theme::accent, Theme::accent);

		whole("screen.cardWidth", "Card width", Section.SCREEN,
				"Sets how wide each column of settings is. Wider columns mean fewer fit side by side.",
				new Control.Whole(ScreenSettings.MIN_CARD_WIDTH, ScreenSettings.MAX_CARD_WIDTH, 10,
						"px"),
				ScreenSettings::cardWidth, ScreenSettings::cardWidth);
		flag("screen.compact", "Compact rows", Section.SCREEN,
				"Packs the rows closer together so more settings fit without scrolling.",
				ScreenSettings::compact, ScreenSettings::compact);
		flag("screen.tooltips", "Show descriptions on hover", Section.SCREEN,
				"Shows a short description like this one when you hover over a setting.",
				ScreenSettings::tooltips, ScreenSettings::tooltips);

		flag("puzzles.enabled", "Solve puzzles", Section.PUZZLES,
				"Shows the solution to each dungeon puzzle you walk into.",
				Puzzles::enabled, Puzzles::enabled);
		when(Puzzles::enabled, () -> {
			whole("puzzles.drawDistance", "Draw distance", Section.PUZZLES,
					"How far away you can still see puzzle markers.",
					new Control.Whole(16, 128, 8, "blocks"),
					Puzzles::drawDistance, Puzzles::drawDistance);
			flag("puzzles.throughWalls", "Draw through walls", Section.PUZZLES,
					"Shows puzzle markers through blocks, not just in plain sight.",
					Puzzles::throughWalls, Puzzles::throughWalls);

			Blaze blaze = Puzzles.BLAZE;
			flag("blaze.enabled", "Solve Blaze", Section.BLAZE,
					"Boxes the blazes in the order you should kill them.",
					blaze::enabled, blaze::enabled);
			when(blaze::enabled, () -> {
				choice("blaze.style", "Box style", Section.BLAZE,
						"Draws each box filled, outlined, or both.",
						Style.class, Style::label, blaze::style, blaze::style);
				flag("blaze.nextLine", "Line to the next blaze", Section.BLAZE,
						"Draws a line from each blaze to the next one you kill.",
						blaze::nextLine, blaze::nextLine);
				whole("blaze.lines", "Lines", Section.BLAZE,
						"How many blazes ahead the lines reach.",
						new Control.Whole(1, 10, 1, ""), blaze::lines, blaze::lines);
				real("blaze.lineWidth", "Line width", Section.BLAZE,
						"How thick the lines between blazes are.",
						new Control.Real(0.5, 5.0, 0.1, Control.Format.PLAIN),
						blaze::lineWidth, blaze::lineWidth);
				boxColour("blaze.firstColour", "First colour", Section.BLAZE,
						"The color of the blaze to kill now.",
						blaze::firstColour, blaze::firstColour);
				boxColour("blaze.secondColour", "Second colour", Section.BLAZE,
						"The color of the blaze to kill next.",
						blaze::secondColour, blaze::secondColour);
				boxColour("blaze.thirdColour", "Third colour", Section.BLAZE,
						"The color of the blaze to kill after that.",
						blaze::thirdColour, blaze::thirdColour);
				boxColour("blaze.otherColour", "Other colour", Section.BLAZE,
						"The color of every other blaze.",
						blaze::otherColour, blaze::otherColour);
				choice("blaze.order", "Order", Section.BLAZE,
						"Sets whether you kill the lowest or highest health blaze first. Auto picks based on where the chest is.",
						Blaze.Order.class, Blaze.Order::label, blaze::order, blaze::order);
				flag("blaze.announce", "Say when solved", Section.BLAZE,
						"Posts a message in your chat when the last blaze dies. Only you can see it.",
						blaze::announce, blaze::announce);
				action("blaze.reset", "Reset the Blaze solver", Section.BLAZE,
						"Clears the solver and scans the room again. Use it if the boxes look wrong.",
						blaze::reset);
			});

			CreeperBeams beams = Puzzles.BEAMS;
			flag("beams.enabled", "Solve Creeper Beams", Section.BEAMS,
					"Pairs up the lit sea lanterns and gives each pair its own color.",
					beams::enabled, beams::enabled);
			when(beams::enabled, () -> {
				choice("beams.style", "Box style", Section.BEAMS,
						"Draws each lantern box filled, outlined, or both.",
						Style.class, Style::label, beams::style, beams::style);
				flag("beams.tracer", "Line between pairs", Section.BEAMS,
						"Draws a line between the two lanterns in each pair.",
						beams::tracer, beams::tracer);
				// The only opacity sliders outside a picker: the lantern colours are a fixed cycle,
				// so there is no colour picker here to carry them.
				real("beams.alpha", "Outline opacity", Section.BEAMS,
						"How solid the box outlines look. The pair colors are fixed, so you set their opacity here.",
						new Control.Real(0.0, 1.0, 0.05, Control.Format.PERCENT),
						beams::alpha, beams::alpha);
				real("beams.fillAlpha", "Fill opacity", Section.BEAMS,
						"How solid the inside of each lantern box looks.",
						new Control.Real(0.0, 1.0, 0.05, Control.Format.PERCENT),
						beams::fillAlpha, beams::fillAlpha);
				action("beams.reset", "Reset the Creeper Beams solver", Section.BEAMS,
						"Clears the solver and checks the lanterns again. Use it if the pairs look wrong.",
						beams::reset);
			});
		});

		flag("livid.enabled", "Find the real Livid", Section.LIVID,
				"Boxes the real Livid in the Floor 5 boss fight. The box shows through walls.",
				Livid::enabled, Livid::enabled);
		when(Livid::enabled, () -> {
			choice("livid.style", "Box style", Section.LIVID,
					"Draws the box filled, outlined, or both.",
					Style.class, Style::label, Livid::style, Livid::style);
			boxColour("livid.boxColour", "Box colour", Section.LIVID,
					"The color of the box around the real Livid.",
					Livid::boxColour, Livid::boxColour);
			flag("livid.announce", "Name it in chat", Section.LIVID,
					"Posts the real Livid's name in your chat. Only you can see it.",
					Livid::announce, Livid::announce);
			flag("livid.hideWhenBlind", "Hide while blinded", Section.LIVID,
					"Hides the box while Livid blinds you. Leave it off if you want the box when it helps most.",
					Livid::hideWhenBlind, Livid::hideWhenBlind);
		});

		flag("livid.title.enabled", "Show the colour on screen", Section.LIVID_TITLE,
				"Flashes the real Livid's color across the middle of your screen as soon as the mod finds it.",
				LividTitle::enabled, LividTitle::enabled);
		when(LividTitle::enabled, () -> {
			real("livid.title.scale", "Size", Section.LIVID_TITLE,
					"How big the color text is.",
					new Control.Real(LividTitle.MIN_SCALE, LividTitle.MAX_SCALE, 0.25, Control.Format.PLAIN),
					LividTitle::scale, LividTitle::scale);
			real("livid.title.seconds", "How long it stays", Section.LIVID_TITLE,
					"How many seconds the color stays on screen before it fades.",
					new Control.Real(LividTitle.MIN_SECONDS, LividTitle.MAX_SECONDS, 0.5,
							Control.Format.SECONDS),
					LividTitle::seconds, LividTitle::seconds);
		});

		flag("mobs.enabled", "Box starred mobs", Section.STAR_MOBS,
				"Draws a colored box around each starred mob in your current room. The box shows through walls.",
				StarMobWatch::enabled, StarMobWatch::enabled);
		when(StarMobWatch::enabled, () -> {
			flag("mobs.labels", "Name starred mobs", Section.STAR_MOBS,
					"Shows the mob's name and distance above its box.",
					StarMobWatch::labels, StarMobWatch::labels);
			flag("mobs.hiddenFels", "Box hidden Fels", Section.STAR_MOBS,
					"Boxes hidden Fels before they come out. They don't show a star until then, so this catches them early.",
					StarMobWatch::hiddenFels, StarMobWatch::hiddenFels);
			kindColour("mobs.colour", MobKind.STARRED,
					"Box color for starred mobs that aren't Fels or minibosses.");
			kindColour("mobs.felsColour", MobKind.FELS,
					"Box color for Fels, hidden or not.");
			kindColour("mobs.minibossColour", MobKind.MINIBOSS,
					"Box color for Shadow Assassins, Lost Adventurers, Angry Archaeologists, and King Midas.");
		});

		flag("quitting.cleanExit", "Quit cleanly", Section.QUITTING,
				"Closes the game right away when you quit. Without it, another mod can leave the game hanging until it crashes.",
				CleanExit::enabled, CleanExit::enabled);

		flag("developer.forceDungeon", "Force the Catacombs", Section.DEVELOPER,
				"Treats every location as a dungeon so you can test dungeon features anywhere.",
				DungeonState::forced, DungeonState::forced);
		flag("developer.drawRoomFrame", "Draw the room frame", Section.DEVELOPER,
				"Outlines the room you're standing in. Once a puzzle is found, it also marks the room's corner and facing.",
				RoomWatch::drawRoomFrame, RoomWatch::drawRoomFrame);
		flag("developer.logRoomFrame", "Log room frames", Section.DEVELOPER,
				"Writes each room you enter and the dungeon sidebar to the game log. Useful for bug reports.",
				RoomWatch::logRoomFrame, RoomWatch::logRoomFrame);
		flag("developer.logSignatures", "Log signature checks", Section.DEVELOPER,
				"Writes every block the mod checks while spotting puzzle rooms to the game log.",
				RoomWatch::logSignatures, RoomWatch::logSignatures);
		flag("developer.logPuzzles", "Log puzzle solvers", Section.DEVELOPER,
				"Writes what each puzzle solver does in each room to the game log.",
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
	// they are here so the first slider or colour is one line above and no
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

	/** Every colour carries its own opacity, set on the slider in its picker. */
	private static void colour(String key, String label, Section section, String blurb,
			Supplier<Swatch> read, Consumer<Swatch> write) {
		add(key, label, blurb, section, new Control.Colour(false), read, write);
	}

	/** A box's colour: its picker has an outline slider and a fill slider, both saved with it. */
	private static void boxColour(String key, String label, Section section, String blurb,
			Supplier<Swatch> read, Consumer<Swatch> write) {
		add(key, label, blurb, section, new Control.Colour(true), read, write);
	}

	private static void kindColour(String key, MobKind kind, String blurb) {
		boxColour(key, kind.label() + " colour", Section.STAR_MOBS, blurb, kind::colour, kind::colour);
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
