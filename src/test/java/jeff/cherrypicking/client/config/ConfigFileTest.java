package jeff.cherrypicking.client.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jeff.cherrypicking.client.dungeon.draw.Style;
import jeff.cherrypicking.client.theme.Swatch;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a player relies on from {@code config/cherrypicking.json}: their choices survive a restart,
 * a hand-edited or damaged file cannot push a value past what the screen allows, and a file from
 * before per-colour fills keeps its boxes looking the same.
 *
 * <p>Settings are global, so every test starts and ends from the shipped defaults.
 */
class ConfigFileTest {
	@TempDir
	Path directory;

	private Path file;

	@BeforeEach
	void start() {
		file = directory.resolve("cherrypicking.json");
		Settings.resetAll();
	}

	@AfterEach
	void finish() {
		Settings.resetAll();
	}

	@Test
	void savedValuesComeBackAfterARestart() {
		set("blaze.nextLine", false);
		set("blaze.lines", 7);
		set("beams.alpha", 0.4);
		set("livid.style", Style.OUTLINE);
		set("livid.boxColour", new Swatch.Named("red", 0x80, 0x20));

		ConfigFile.save(file);
		Settings.resetAll();
		ConfigFile.load(file);

		assertEquals(false, value("blaze.nextLine"));
		assertEquals(7, value("blaze.lines"));
		assertEquals(0.4, value("beams.alpha"));
		assertEquals(Style.OUTLINE, value("livid.style"));
		assertEquals(new Swatch.Named("red", 0x80, 0x20), value("livid.boxColour"));
	}

	@Test
	void savedFileHoldsEverySettingAndNoButtons() throws IOException {
		ConfigFile.save(file);
		JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();

		for (Setting<?> setting : Settings.settings()) {
			assertTrue(saved.has(setting.key()), setting.key());
		}
		assertFalse(saved.has("blaze.reset"));
		assertFalse(saved.has("beams.reset"));
	}

	@Test
	void aFailedSaveLeavesThePreviousFileWhole() throws IOException {
		set("blaze.lines", 3);
		ConfigFile.save(file);
		byte[] before = Files.readAllBytes(file);

		// A directory where the save's temporary file must go makes the write fail.
		Files.createDirectory(directory.resolve("cherrypicking.json.tmp"));
		set("blaze.lines", 9);
		ConfigFile.save(file);

		assertArrayEquals(before, Files.readAllBytes(file));
	}

	@Test
	void aGoodSaveLeavesNoTemporaryFile() throws IOException {
		ConfigFile.save(file);
		ConfigFile.save(file);

		try (var entries = Files.list(directory)) {
			assertEquals(1, entries.count());
		}
	}

	@Test
	void numbersOutsideTheSliderAreClampedToIt() throws IOException {
		write("{\"blaze.lines\": 99, \"beams.alpha\": -3, \"puzzles.drawDistance\": 5}");

		assertEquals(10, value("blaze.lines"));
		assertEquals(0.0, value("beams.alpha"));
		assertEquals(16, value("puzzles.drawDistance"));
	}

	@Test
	void valuesOfTheWrongTypeKeepTheDefault() throws IOException {
		write("{\"blaze.nextLine\": \"yes\", \"blaze.lines\": \"7\", \"livid.style\": 3,"
				+ " \"livid.boxColour\": 5, \"beams.alpha\": NaN, \"blaze.lineWidth\": Infinity}");

		for (String key : new String[] {"blaze.nextLine", "blaze.lines", "livid.style", "livid.boxColour",
				"beams.alpha", "blaze.lineWidth"}) {
			assertEquals(fallback(key), value(key), key);
		}
	}

	@Test
	void oneBadValueDoesNotSpoilTheRest() throws IOException {
		write("{\"blaze.lines\": \"x\", \"blaze.nextLine\": false, \"not.a.setting\": 1}");

		assertEquals(fallback("blaze.lines"), value("blaze.lines"));
		assertEquals(false, value("blaze.nextLine"));
	}

	@Test
	void anUnreadableFileKeepsEveryDefault() throws IOException {
		for (String text : new String[] {"[1, 2]", "{\"blaze.lines\": ", ""}) {
			write(text);
			for (Setting<?> setting : Settings.settings()) {
				assertEquals(setting.fallback(), setting.value(), text + " " + setting.key());
			}
		}
	}

	@Test
	void anOldSharedFillBecomesEachBoxColoursFill() throws IOException {
		write("{\"boxes.fillOpacity\": 0.5, \"livid.boxColour\": \"red@80\","
				+ " \"blaze.firstColour\": \"red@80/20\"}");

		// 0x80 outline at half share: fill 0x40. A colour that names its own fill keeps it.
		assertEquals(new Swatch.Named("red", 0x80, 0x40), value("livid.boxColour"));
		assertEquals(new Swatch.Named("red", 0x80, 0x20), value("blaze.firstColour"));
	}

	@Test
	void anOldSharedFillBecomesTheLanternFill() throws IOException {
		write("{\"boxes.fillOpacity\": 0.5, \"beams.alpha\": 0.8}");
		assertEquals(0.4, (double) value("beams.fillAlpha"), 1e-9);

		Settings.resetAll();
		write("{\"boxes.fillOpacity\": 0.5, \"beams.alpha\": 0.8, \"beams.fillAlpha\": 0.9}");
		assertEquals(0.9, value("beams.fillAlpha"));

		Settings.resetAll();
		write("{\"beams.alpha\": 0.8}");
		assertEquals(fallback("beams.fillAlpha"), value("beams.fillAlpha"));
	}

	@Test
	void theNextSaveDropsTheOldSharedFill() throws IOException {
		write("{\"boxes.fillOpacity\": 0.5, \"livid.boxColour\": \"red@80\"}");
		ConfigFile.save(file);

		String saved = Files.readString(file);
		assertFalse(saved.contains("boxes.fillOpacity"), saved);
		assertTrue(saved.contains("\"red@80/40\""), saved);
	}

	/** Writes {@code text} as the config file, then loads it. */
	private void write(String text) throws IOException {
		Files.writeString(file, text, StandardCharsets.UTF_8);
		ConfigFile.load(file);
	}

	private static Object value(String key) {
		return Settings.byKey(key).orElseThrow().value();
	}

	private static Object fallback(String key) {
		return Settings.byKey(key).orElseThrow().fallback();
	}

	@SuppressWarnings("unchecked")
	private static <T> void set(String key, T fresh) {
		((Setting<T>) Settings.byKey(key).orElseThrow()).value(fresh);
	}
}
