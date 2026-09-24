package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import jeff.cherrypicking.CherryPicking;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Your own looks, read from Skyblocker's saved config, {@code config/skyblocker.json}. Imports no
 * Skyblocker class, so this mod runs without Skyblocker, and Skyblocker's code can change freely.
 *
 * <p>Skyblocker keeps each kind of look in its own map under {@code general}, keyed by item uuid.
 * {@link #looks} turns those maps into one look per uuid, with the field names of the payload. The
 * values are copied as they are; {@link Payload#decode} checks them.
 */
final class SkyblockerFile {
	/** Skyblocker's map name, and the payload field it becomes. */
	private static final Map<String, String> FIELDS = Map.of(
			"customDyeColors", "dye",
			"customArmorTrims", "trim",
			"customHelmetTextures", "helmetTexture",
			"customGlint", "glint");

	private SkyblockerFile() {
	}

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("skyblocker.json");
	}

	/** The whole file, or empty if it is missing or is not a JSON object. Logs why. */
	static Optional<JsonObject> read(Path file) {
		if (!Files.isRegularFile(file)) {
			CherryPicking.LOGGER.info("Friend looks: no {}, so there is nothing of yours to share.", file);
			return Optional.empty();
		}
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			if (parsed.isJsonObject()) {
				return Optional.of(parsed.getAsJsonObject());
			}
			CherryPicking.LOGGER.warn("Friend looks: {} is not a JSON object.", file);
		} catch (IOException | JsonParseException failed) {
			CherryPicking.LOGGER.warn("Friend looks: could not read {}.", file, failed);
		}
		return Optional.empty();
	}

	/** Item uuid to look, sorted by uuid so the same file always gives the same payload. */
	static Map<String, JsonObject> looks(JsonObject skyblocker) {
		Map<String, JsonObject> looks = new TreeMap<>();
		if (!(skyblocker.get("general") instanceof JsonObject general)) {
			return looks;
		}
		FIELDS.forEach((map, field) -> {
			if (general.get(map) instanceof JsonObject byUuid) {
				byUuid.entrySet().forEach(entry ->
						looks.computeIfAbsent(entry.getKey(), uuid -> new JsonObject()).add(field, entry.getValue()));
			}
		});
		return looks;
	}
}
