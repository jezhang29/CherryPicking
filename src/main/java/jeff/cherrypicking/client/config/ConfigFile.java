package jeff.cherrypicking.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.theme.Swatch;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Saves and loads {@link Settings} as JSON.
 *
 * <p>Flat, keyed by {@link Setting#key()}. That means a setting can be added,
 * removed or reordered without touching this class, and an old file loads into
 * a newer mod: keys it does not recognise are ignored, and settings the file
 * does not mention keep their default. Only {@link Settings#settings()} is
 * written; an {@link Action} has no value and never appears.
 *
 * <p>A colour is written as a string: {@code "green"} for a palette name, which
 * follows the flavour, or {@code "#aarrggbb"} for a literal, which does not. A
 * box colour may end in {@code "/59"}, its own fill opacity.
 *
 * <p>The file is this mod's own, under its own name. It is not shared with the
 * other mods in the family, so their release cycles cannot put saved player
 * settings at risk.
 *
 * <p>Nothing here throws. A config file is a convenience, and a mod that
 * refuses to start because one line of JSON is malformed is worse than one that
 * logs it and uses the defaults.
 *
 * <p>The file is the one place a value can arrive that no widget produced, so
 * loading is where values are checked: each must have its control's JSON shape,
 * and numbers are clamped to the control's limits. Saving writes a temporary
 * file beside the real one and moves it into place, so a failed or interrupted
 * save leaves the previous file whole.
 */
public final class ConfigFile {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * The one fill share every box used before each colour had a fill of its own. A file that
	 * still has it gets each box colour's fill worked out from it once, so boxes look the same
	 * after the update; the next save drops the key.
	 */
	private static final String LEGACY_FILL_SHARE = "boxes.fillOpacity";

	/**
	 * Creeper Beams has no colour picker, so its fill is a setting of its own. Before fills were
	 * per colour, lanterns filled at their outline opacity times the shared share; an old file gets
	 * that product once, like the box colours.
	 */
	private static final String BEAMS_OUTLINE = "beams.alpha";
	private static final String BEAMS_FILL = "beams.fillAlpha";

	private ConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(CherryPicking.MOD_ID + ".json");
	}

	/** Applies the saved file over the defaults. Silent when there is no file yet. */
	public static void load() {
		load(path());
	}

	static void load(Path file) {
		if (!Files.isRegularFile(file)) {
			return;
		}

		JsonObject saved;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				CherryPicking.LOGGER.warn("Config at {} is not a JSON object; using defaults.", file);
				return;
			}
			saved = parsed.getAsJsonObject();
		} catch (IOException | RuntimeException failed) {
			CherryPicking.LOGGER.warn("Could not read the config at {}; using defaults.", file, failed);
			return;
		}

		Double legacyShare = legacyShare(saved);
		for (Setting<?> setting : Settings.settings()) {
			JsonElement value = saved.get(setting.key());
			if (value != null) {
				apply(setting, value, legacyShare);
			}
		}
		if (legacyShare != null && !saved.has(BEAMS_FILL)) {
			legacyBeamsFill(legacyShare);
		}
	}

	@SuppressWarnings("unchecked")
	private static void legacyBeamsFill(double share) {
		Settings.byKey(BEAMS_OUTLINE).ifPresent(outline -> Settings.byKey(BEAMS_FILL).ifPresent(fill ->
				((Setting<Double>) fill).value((Double) outline.value() * share)));
	}

	/** @return the old shared fill share, or null when the file has none */
	private static Double legacyShare(JsonObject saved) {
		try {
			JsonElement share = saved.get(LEGACY_FILL_SHARE);
			return share == null ? null : Math.clamp(share.getAsDouble(), 0.0, 1.0);
		} catch (RuntimeException wrongShape) {
			return null;
		}
	}

	/** Writes every setting's current value. */
	public static void save() {
		save(path());
	}

	static void save(Path file) {
		JsonObject out = new JsonObject();
		for (Setting<?> setting : Settings.settings()) {
			write(out, setting);
		}

		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				GSON.toJson(out, writer);
			}
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException | RuntimeException failed) {
			CherryPicking.LOGGER.warn("Could not write the config to {}; the previous file is unchanged.",
					file, failed);
			try {
				Files.deleteIfExists(temporary);
			} catch (IOException | RuntimeException ignored) {
				// Left for the next save to overwrite.
			}
		}
	}

	private static void write(JsonObject out, Setting<?> setting) {
		Object value = setting.value();
		switch (value) {
			case Boolean flag -> out.addProperty(setting.key(), flag);
			case Number number -> out.addProperty(setting.key(), number);
			case Enum<?> constant -> out.addProperty(setting.key(), constant.name());
			case Swatch swatch -> out.addProperty(setting.key(), swatch.written());
			default -> CherryPicking.LOGGER.warn("Setting {} holds an unsaveable {}.",
					setting.key(), value == null ? "null" : value.getClass().getSimpleName());
		}
	}

	/**
	 * Pushes one saved value into one setting.
	 *
	 * <p>The unchecked casts are what the switch on {@link Control} has just
	 * proved: a {@code Control.Flag} only ever belongs to a {@code
	 * Setting<Boolean>}, because {@code Control<T>} is what ties the two
	 * together in {@link Setting}'s signature.
	 */
	@SuppressWarnings("unchecked")
	private static void apply(Setting<?> setting, JsonElement value, Double legacyShare) {
		try {
			switch (setting.control()) {
				case Control.Flag ignored ->
						((Setting<Boolean>) setting).value(shaped(value, JsonPrimitive::isBoolean).getAsBoolean());
				case Control.Whole whole ->
						((Setting<Integer>) setting).value(Math.clamp(Math.round(finite(value)), whole.min(), whole.max()));
				case Control.Real real ->
						((Setting<Double>) setting).value(Math.clamp(finite(value), real.min(), real.max()));
				case Control.Choice<?> choice ->
						applyChoice(setting, choice, shaped(value, JsonPrimitive::isString).getAsString());
				case Control.Colour colour -> {
					String text = shaped(value, JsonPrimitive::isString).getAsString();
					Swatch.read(text).ifPresentOrElse(
							swatch -> ((Setting<Swatch>) setting).value(
									colour.fill() && legacyShare != null && !Swatch.namesFill(text)
											? swatch.withFill((int) Math.round(swatch.alpha() * legacyShare))
											: swatch),
							() -> CherryPicking.LOGGER.warn(
									"Saved colour for {} is neither a palette name nor a hex; keeping the default.",
									setting.key()));
				}
			}
		} catch (RuntimeException wrongShape) {
			CherryPicking.LOGGER.warn("Saved value for {} could not be read; keeping the default.",
					setting.key(), wrongShape);
		}
	}

	/**
	 * Gson turns {@code "yes"} into {@code false} and {@code 3} into {@code "3"} without complaint,
	 * so each control accepts only its own JSON type.
	 */
	private static JsonPrimitive shaped(JsonElement value, Predicate<JsonPrimitive> type) {
		if (value.isJsonPrimitive() && type.test(value.getAsJsonPrimitive())) {
			return value.getAsJsonPrimitive();
		}
		throw new IllegalArgumentException("wrong JSON type: " + value);
	}

	/** A number that is not NaN or infinite; the lenient parser accepts both. */
	private static double finite(JsonElement value) {
		double number = shaped(value, JsonPrimitive::isNumber).getAsDouble();
		if (!Double.isFinite(number)) {
			throw new IllegalArgumentException("not a finite number: " + value);
		}
		return number;
	}

	@SuppressWarnings("unchecked")
	private static <E extends Enum<E>> void applyChoice(Setting<?> setting,
			Control.Choice<E> choice, String name) {
		((Setting<E>) setting).value(Enum.valueOf(choice.type(), name));
	}
}
