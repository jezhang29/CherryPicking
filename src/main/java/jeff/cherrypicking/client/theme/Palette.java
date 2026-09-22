package jeff.cherrypicking.client.theme;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import jeff.cherrypicking.CherryPicking;

/**
 * One theme's 26 colours, by Catppuccin's own names. A theme that is not Catppuccin is laid out in
 * the same slots, so {@code "mauve"} is that theme's purple and {@code "base"} its background.
 *
 * <p>Every value is an opaque ARGB {@code int}. Alpha is the caller's business: a {@link Role}
 * adds it, and a {@link Swatch.Literal} carries its own.
 */
public record Palette(Map<String, Integer> colours) {
	/** Catppuccin's fourteen accents, in palette order. */
	public static final List<String> ACCENTS = List.of(
			"rosewater", "flamingo", "pink", "mauve", "red", "maroon", "peach",
			"yellow", "green", "teal", "sky", "sapphire", "blue", "lavender");

	/** The twelve neutrals, in palette order: text first, crust last. */
	public static final List<String> NEUTRALS = List.of(
			"text", "subtext1", "subtext0", "overlay2", "overlay1", "overlay0",
			"surface2", "surface1", "surface0", "base", "mantle", "crust");

	/** All 26, accents first. A flavour missing any of these is malformed. */
	public static final List<String> NAMES = Stream.concat(ACCENTS.stream(), NEUTRALS.stream()).toList();

	/** Names already warned about, so a bad name in a hot path logs once and not every frame. */
	private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

	public Palette {
		colours = Map.copyOf(colours);
	}

	/** @return the named colour, or {@code text} when there is no such name (logged once) */
	public int of(String name) {
		Integer argb = colours.get(name);
		if (argb != null) {
			return argb;
		}
		if (WARNED.add(name)) {
			CherryPicking.LOGGER.warn("No palette colour named '{}'; using text instead.", name);
		}
		return colours.getOrDefault("text", 0xFF000000);
	}

	public boolean has(String name) {
		return colours.containsKey(name);
	}

	/** The fourteen accents, in palette order. */
	public List<String> accents() {
		return ACCENTS;
	}

	/** The twelve neutrals, background end first: crust, mantle, base, and on up to text. */
	public List<String> neutrals() {
		return NEUTRALS.reversed();
	}
}
