package jeff.cherrypicking.client.cosmetics;

import java.util.Map;

import jeff.cherrypicking.CherryPicking;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Friend cosmetics as a whole: registers its tick callback, and owns the switch that turns the
 * feature on. Off by default, because it talks to the relay.
 */
public final class Cosmetics {
	/** The {@code friendCosmetics.enabled} setting. */
	private static volatile boolean enabled;

	private Cosmetics() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Publisher::tick);
		// The link first, so the poller sees it closed in the same tick the feature turns off.
		ClientTickEvents.END_CLIENT_TICK.register(LiveLink::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Poller::tick);
	}

	public static boolean enabled() {
		return enabled;
	}

	/** Turning it off clears the looks drawn and the relay login, and stops sharing and fetching. */
	public static void enabled(boolean value) {
		enabled = value;
		if (!value) {
			FriendLooks.replace(Map.of());
			RelayClient.forget();
			Publisher.reset();
			Poller.reset();
		}
	}

	/**
	 * The "Reconnect relay" button: logs in again, opens a new live link and gets friends' looks now.
	 * It also ends the stop after the relay refused this player. Client thread.
	 */
	public static void reconnect() {
		CherryPicking.LOGGER.info("Relay: reconnecting.");
		RelayClient.forget();
		LiveLink.close();
		Poller.reset();
	}
}
