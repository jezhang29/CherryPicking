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
 * {@link Flavor}.
 *
 * <p>The colors are data, not code, so a corrected hex is an edit to the JSON, and a new theme is
 * the JSON plus one line in {@link Flavor}.
 * The file is read once, lazily, straight off the classpath: it ships inside the jar, so it needs
 * no resource manager and is there before any resource reload has run.
 *
 * <p>Nothing here throws. A theme is a convenience, and a mod that will not start over one bad
 * hex is worse than one that logs it. A flavor that is missing or malformed falls back to the
 * built-in Latte.
 */
public final class Palettes {
	private static final String PATH = "/assets/cherrypicking/theme/themes.json";

	/** Latte, in {@link Palette#NAMES} order: the fallback for a missing or malformed flavor. */
	private static final int[] BUILT_IN_LATTE = {
			0xdc8a78, 0xdd7878, 0xea76cb, 0x8839ef, 0xd20f39, 0xe64553, 0xfe640b,
			0xdf8e1d, 0x40a02b, 0x179299, 0x04a5e5, 0x209fb5, 0x1e66f5, 0x7287fd,
			0x4c4f69, 0x5c5f77, 0x6c6f85, 0x7c7f93, 0x8c8fa1, 0x9ca0b0,
			0xacb0be, 0xbcc0cc, 0xccd0da, 0xeff1f5, 0xe6e9ef, 0xdce0e8};

	private static volatile Map<Flavor, Palette> loaded;

	private Palettes() {
	}

	public static Palette of(Flavor flavor) {
		return all().get(flavor);
	}

	private static Map<Flavor, Palette> all() {
		Map<Flavor, Palette> palettes = loaded;
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

	private static Map<Flavor, Palette> load() {
		JsonObject root = read();
		Map<Flavor, Palette> palettes = new EnumMap<>(Flavor.class);
		for (Flavor flavor : Flavor.values()) {
			palettes.put(flavor, root == null ? builtInLatte() : parse(root, flavor));
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

	private static Palette parse(JsonObject root, Flavor flavor) {
		try {
			JsonObject colors = root.getAsJsonObject(flavor.key());
			if (colors == null) {
				throw new IllegalArgumentException("no such flavor");
			}
			Map<String, Integer> parsed = new HashMap<>();
			for (String name : Palette.NAMES) {
				JsonElement value = colors.get(name);
				if (value == null) {
					throw new IllegalArgumentException("no color named " + name);
				}
				parsed.put(name, rgb(value.getAsString()));
			}
			return new Palette(parsed);
		} catch (RuntimeException malformed) {
			CherryPicking.LOGGER.warn("Theme flavor '{}' is malformed ({}); using the built-in Latte for it.",
					flavor.key(), malformed.getMessage());
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
		Map<String, Integer> colors = new HashMap<>();
		for (int i = 0; i < names.size(); i++) {
			colors.put(names.get(i), 0xFF000000 | BUILT_IN_LATTE[i]);
		}
		return new Palette(colors);
	}
}
