package jeff.cherrypicking.client.config;

import jeff.cherrypicking.client.screen.SettingsScreen;

import net.minecraft.client.gui.screens.Screen;

/**
 * The one door every way in uses: {@code /cherry}, {@link ScreenOpener},
 * {@link ModMenuHooks} and {@link HubEntry} all call {@link #build(Screen)}.
 *
 * <p>The screen itself is {@link SettingsScreen}, which this mod draws. It has
 * no external library behind it, so it cannot fail to open.
 */
public final class ConfigScreen {
	private ConfigScreen() {
	}

	/** @param parent the screen to return to when this one closes; may be null */
	public static Screen build(Screen parent) {
		return new SettingsScreen(parent);
	}
}
