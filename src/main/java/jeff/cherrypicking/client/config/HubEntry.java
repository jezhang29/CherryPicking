package jeff.cherrypicking.client.config;

import java.util.function.Function;

import net.minecraft.client.gui.screens.Screen;

/**
 * Lists this mod in the shared config hub that skyblock-flipper draws.
 *
 * <p>The contract is {@code Function<Screen, Screen>} - a JDK type and a
 * Minecraft type, so nothing is imported across mods. A provider may return
 * {@code null} to say "an optional dependency is missing"; this one never does,
 * because YACL is a hard {@code depends} here. If the flipper is absent nothing
 * claims the {@code jeffhub} key and this class is simply never loaded.
 * {@code /cherry} is the way in either way.
 *
 * <p>This class must stay loadable with every optional dependency absent, so it
 * names no YACL or ModMenu type. {@link ConfigScreen} is where those live, and
 * it is only reached after the hub has decided to open this mod.
 *
 * <p>See {@code ../skyblock-flipper-26.2/docs/config-hub.md} for the contract.
 */
public final class HubEntry implements Function<Screen, Screen> {
	/** @param parent the screen to return to on close; may be null */
	@Override
	public Screen apply(Screen parent) {
		return ConfigScreen.build(parent);
	}
}
