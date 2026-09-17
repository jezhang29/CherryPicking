package jeff.cherrypicking.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens the config screen from a chat command.
 *
 * <p>A client command runs while the chat screen is still up, and chat closes
 * itself afterwards by setting the screen to null - so opening a screen inside
 * the command handler opens it and then has it closed out from under you. The
 * request is parked here instead and honoured on the first tick where nothing
 * else is on screen.
 */
public final class ScreenOpener {
	private static volatile boolean wanted;

	private ScreenOpener() {
	}

	/** Asks for the config screen the next time the game is not showing one. */
	public static void request() {
		wanted = true;
	}

	public static void tick(Minecraft client) {
		// 26.2 moved the current screen off Minecraft and onto its Gui, so this
		// is Gui.screen() rather than the client.screen every older example uses.
		if (!wanted || client.gui.screen() != null) {
			return;
		}
		wanted = false;
		Screen config = ConfigScreen.build(null);
		client.gui.setScreen(config);
	}
}
