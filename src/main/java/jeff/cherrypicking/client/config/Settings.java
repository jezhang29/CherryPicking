package jeff.cherrypicking.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every setting the mod has, declared once.
 *
 * <p><b>This list is the config screen.</b> {@link ConfigScreen} walks it and
 * builds the tabs, groups and widgets from what it finds; {@link ConfigFile}
 * saves and loads it by key. Neither knows what any particular setting is. So a
 * new tunable is added here and nowhere else, and it turns up in the screen and
 * in the saved file for free.
 *
 * <p><b>Adding one.</b> Give the live field a getter and a setter on the class
 * that already owns it, then add a line below with:
 * <ul>
 *   <li>a stable {@code key} - it is the name in the config file, so renaming
 *       one quietly discards what players had saved;</li>
 *   <li>a {@code label} a player would recognise;</li>
 *   <li>a {@code blurb} of <b>one plain sentence</b>: what it does and why you
 *       would touch it, not how it is implemented;</li>
 *   <li>a {@link Section}, which decides where it lands.</li>
 * </ul>
 *
 * <p>Defaults are not written here. Each setting's default is whatever its owner
 * holds when this class is first touched, which is before anything has had a
 * chance to change it. That keeps one copy of every default, at the field it
 * belongs to, and makes Reset restore what the code actually ships with rather
 * than a number copied over here and left behind.
 */
public final class Settings {
	private static final List<Setting<?>> ALL = new ArrayList<>();

	static {
		// The mod has no features yet, so this is the one setting that proves
		// the screen draws and the file round-trips. Delete it, and
		// Placeholder, with the first real setting.
		flag("general.placeholder", "Placeholder setting", Section.GENERAL,
				"Does nothing yet; it is here so the settings screen has something to show.",
				Placeholder::enabled, Placeholder::enabled);
	}

	private Settings() {
	}

	/** Every setting, in the order the screen draws them. */
	public static List<Setting<?>> all() {
		return List.copyOf(ALL);
	}

	public static Optional<Setting<?>> byKey(String key) {
		return ALL.stream().filter(setting -> setting.key().equals(key)).findFirst();
	}

	/** Puts every setting back to its default. */
	public static void resetAll() {
		ALL.forEach(Setting::reset);
	}

	/** {@code BOTTOM_RIGHT} reads as "Bottom right". */
	private static String pretty(Enum<?> value) {
		String words = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
		return Character.toUpperCase(words.charAt(0)) + words.substring(1);
	}

	// One registration helper per Control kind. Only flag() has a caller so far;
	// the rest are here so the first slider or dropdown is one line below and no
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

	/** Registers one setting, taking its owner's current value as the default. */
	private static <T> void add(String key, String label, String blurb, Section section,
			Control<T> control, Supplier<T> read, Consumer<T> write) {
		ALL.add(new Setting<>(key, label, blurb, section, control, read.get(), read, write));
	}
}
