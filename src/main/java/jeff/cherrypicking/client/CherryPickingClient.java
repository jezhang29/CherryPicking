package jeff.cherrypicking.client;

import net.fabricmc.api.ClientModInitializer;

import jeff.cherrypicking.CherryPicking;

/**
 * The single {@code client} entrypoint. Everything the mod does is registered from here, so there
 * is one place that says what is switched on.
 */
public class CherryPickingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		CherryPicking.LOGGER.info("{} loaded", CherryPicking.MOD_ID);
	}
}
