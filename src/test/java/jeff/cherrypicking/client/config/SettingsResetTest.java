package jeff.cherrypicking.client.config;

import java.util.List;

import jeff.cherrypicking.client.theme.Flavor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the screen's reset button relies on: a reset touches only the settings it is given, and its
 * undo puts back exactly what the player had.
 *
 * <p>Settings are global, so every test starts and ends from the shipped defaults.
 */
class SettingsResetTest {
	@BeforeEach
	void start() {
		Settings.resetAll();
	}

	@AfterEach
	void finish() {
		Settings.resetAll();
	}

	@Test
	void resetTouchesOnlyTheSettingsGiven() {
		set("theme.flavor", Flavor.MOCHA);
		set("blaze.lines", 7);

		Settings.reset(List.of(setting("blaze.lines")));

		assertEquals(fallback("blaze.lines"), value("blaze.lines"));
		assertEquals(Flavor.MOCHA, value("theme.flavor"));
	}

	@Test
	void undoPutsBackWhatTheResetChanged() {
		set("theme.flavor", Flavor.MOCHA);
		set("blaze.lines", 7);
		set("blaze.nextLine", false);

		Runnable undo = Settings.reset(Settings.settings());
		assertEquals(fallback("theme.flavor"), value("theme.flavor"));

		undo.run();

		assertEquals(Flavor.MOCHA, value("theme.flavor"));
		assertEquals(7, value("blaze.lines"));
		assertEquals(false, value("blaze.nextLine"));
	}

	private static Setting<?> setting(String key) {
		return Settings.byKey(key).orElseThrow();
	}

	private static Object value(String key) {
		return setting(key).value();
	}

	private static Object fallback(String key) {
		return setting(key).fallback();
	}

	@SuppressWarnings("unchecked")
	private static <T> void set(String key, T fresh) {
		((Setting<T>) setting(key)).value(fresh);
	}
}
