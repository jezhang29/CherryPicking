package jeff.cherrypicking.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 */
public final class ConfigFile {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * The one fill share every box used before each colour had a fill of its own. A file that
	 * still has it gets each box colour's fill worked out from it once, so boxes look the same
	 * after the update; the next save drops the key.
	 */
	private static final String LEGACY_FILL_SHARE = "boxes.fillOpacity";

	private ConfigFile() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(CherryPicking.MOD_ID + ".json");
	}

	/** Applies the saved file over the defaults. Silent when there is no file yet. */
	public static void load() {
		Path file = path();
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
		JsonObject out = new JsonObject();
		for (Setting<?> setting : Settings.settings()) {
			write(out, setting);
		}

		Path file = path();
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(out, writer);
			}
		} catch (IOException | RuntimeException failed) {
			CherryPicking.LOGGER.warn("Could not write the config to {}.", file, failed);
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
						((Setting<Boolean>) setting).value(value.getAsBoolean());
				case Control.Whole ignored ->
						((Setting<Integer>) setting).value(value.getAsInt());
				case Control.Real ignored ->
						((Setting<Double>) setting).value(value.getAsDouble());
				case Control.Choice<?> choice ->
						applyChoice(setting, choice, value.getAsString());
				case Control.Colour colour -> Swatch.read(value.getAsString()).ifPresentOrElse(
						swatch -> ((Setting<Swatch>) setting).value(
								colour.fill() && legacyShare != null && !Swatch.namesFill(value.getAsString())
										? swatch.withFill((int) Math.round(swatch.alpha() * legacyShare))
										: swatch),
						() -> CherryPicking.LOGGER.warn(
								"Saved colour for {} is neither a palette name nor a hex; keeping the default.",
								setting.key()));
			}
		} catch (RuntimeException wrongShape) {
			CherryPicking.LOGGER.warn("Saved value for {} could not be read; keeping the default.",
					setting.key(), wrongShape);
		}
	}

	@SuppressWarnings("unchecked")
	private static <E extends Enum<E>> void applyChoice(Setting<?> setting,
			Control.Choice<E> choice, String name) {
		((Setting<E>) setting).value(Enum.valueOf(choice.type(), name));
	}
}
