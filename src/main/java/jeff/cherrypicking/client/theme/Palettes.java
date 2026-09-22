package jeff.cherrypicking.client.theme;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jeff.cherrypicking.CherryPicking;

/**
 * Loads {@code assets/cherrypicking/theme/themes.json} and hands out one {@link Palette} per
 * {@link Flavour}.
 *
 * <p>The colours are data, not code, so a corrected hex is an edit to the JSON, and a new theme is
 * the JSON plus one line in {@link Flavour}.
 * The file is read once, lazily, straight off the classpath: it ships inside the jar, so it needs
 * no resource manager and is there before any resource reload has run.
 *
 * <p>Nothing here throws. A theme is a convenience, and a mod that will not start over one bad
 * hex is worse than one that logs it. A flavour that is missing or malformed falls back to the
 * built-in Latte.
 */
public final class Palettes {
	private static final String PATH = "/assets/cherrypicking/theme/themes.json";

	/** Latte, in {@link Palette#NAMES} order: the fallback for a missing or malformed flavour. */
	private static final int[] BUILT_IN_LATTE = {
			0xdc8a78, 0xdd7878, 0xea76cb, 0x8839ef, 0xd20f39, 0xe64553, 0xfe640b,
			0xdf8e1d, 0x40a02b, 0x179299, 0x04a5e5, 0x209fb5, 0x1e66f5, 0x7287fd,
			0x4c4f69, 0x5c5f77, 0x6c6f85, 0x7c7f93, 0x8c8fa1, 0x9ca0b0,
			0xacb0be, 0xbcc0cc, 0xccd0da, 0xeff1f5, 0xe6e9ef, 0xdce0e8};

	private static volatile Map<Flavour, Palette> loaded;

	private Palettes() {
	}

	public static Palette of(Flavour flavour) {
		return all().get(flavour);
	}

	private static Map<Flavour, Palette> all() {
		Map<Flavour, Palette> palettes = loaded;
		if (palettes == null) {
			synchronized (Palettes.class) {
				palettes = loaded;
				if (palettes == null) {
					palettes = load();
					loaded = palettes;
				}
			}
		}
		return palettes;
	}

	private static Map<Flavour, Palette> load() {
		JsonObject root = read();
		Map<Flavour, Palette> palettes = new EnumMap<>(Flavour.class);
		for (Flavour flavour : Flavour.values()) {
			palettes.put(flavour, root == null ? builtInLatte() : parse(root, flavour));
		}
		return Map.copyOf(palettes);
	}

	/** @return the file's root object, or null when it cannot be read */
	private static JsonObject read() {
		try (InputStream in = Palettes.class.getResourceAsStream(PATH)) {
			if (in == null) {
				CherryPicking.LOGGER.warn("Theme palette {} is missing; using the built-in Latte.", PATH);
				return null;
			}
			try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (!parsed.isJsonObject()) {
					CherryPicking.LOGGER.warn("Theme palette {} is not a JSON object; using the built-in Latte.", PATH);
					return null;
				}
				return parsed.getAsJsonObject();
			}
		} catch (Exception failed) {
			CherryPicking.LOGGER.warn("Could not read the theme palette {}; using the built-in Latte.", PATH, failed);
			return null;
		}
	}

	private static Palette parse(JsonObject root, Flavour flavour) {
		try {
			JsonObject colours = root.getAsJsonObject(flavour.key());
			if (colours == null) {
				throw new IllegalArgumentException("no such flavour");
			}
			Map<String, Integer> parsed = new HashMap<>();
			for (String name : Palette.NAMES) {
				JsonElement value = colours.get(name);
				if (value == null) {
					throw new IllegalArgumentException("no colour named " + name);
				}
				parsed.put(name, rgb(value.getAsString()));
			}
			return new Palette(parsed);
		} catch (RuntimeException malformed) {
			CherryPicking.LOGGER.warn("Theme flavour '{}' is malformed ({}); using the built-in Latte for it.",
					flavour.key(), malformed.getMessage());
			return builtInLatte();
		}
	}

	/** {@code "#40a02b"} to an opaque ARGB int. */
	private static int rgb(String hex) {
		if (hex.length() != 7 || hex.charAt(0) != '#') {
			throw new IllegalArgumentException("'" + hex + "' is not #rrggbb");
		}
		return 0xFF000000 | Integer.parseInt(hex.substring(1), 16);
	}

	private static Palette builtInLatte() {
		List<String> names = Palette.NAMES;
		Map<String, Integer> colours = new HashMap<>();
		for (int i = 0; i < names.size(); i++) {
			colours.put(names.get(i), 0xFF000000 | BUILT_IN_LATTE[i]);
		}
		return new Palette(colours);
	}
}
