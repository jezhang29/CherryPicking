package jeff.cherrypicking.client;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.command.CherryCommand;
import jeff.cherrypicking.client.config.ConfigFile;
import jeff.cherrypicking.client.config.ScreenOpener;
import jeff.cherrypicking.client.dungeon.Dungeons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * The single {@code client} entrypoint. Everything the mod does is registered from here, so there
 * is one place that says what is switched on.
 */
public class CherryPickingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// First, before anything can read a setting. Loading is applying: the
		// file is pushed straight into the fields that own the values, so
		// everything after this point sees the player's choices and not the
		// defaults.
		ConfigFile.load();

		// A command cannot open a screen while chat is still closing itself, so
		// the request is parked and picked up on a later tick.
		ClientTickEvents.END_CLIENT_TICK.register(ScreenOpener::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> CherryCommand.register(dispatcher));

		Dungeons.register();

		CherryPicking.LOGGER.info("{} loaded", CherryPicking.MOD_ID);
	}
}
