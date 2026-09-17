package jeff.cherrypicking.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Puts the config screen behind the mod's Settings button in ModMenu.
 *
 * <p>ModMenu is optional. Fabric only loads the classes under an entrypoint key
 * when the mod that owns that key asks for them, and nothing asks for
 * {@code modmenu} when ModMenu is absent, so this class is never loaded and its
 * missing imports never matter. {@code /cherry} is the way in either way.
 */
public final class ModMenuHooks implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::build;
	}
}
