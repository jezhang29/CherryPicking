package jeff.cherrypicking.client.cosmetics;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The looks to draw: each friend's payload, by the friend's player UUID. The one owner of that data.
 *
 * <p>The map is replaced as a whole and never edited, so the render thread never sees a half-built
 * one.
 */
public final class FriendLooks {
	private static volatile Map<UUID, Payload> byPlayer = Map.of();

	private FriendLooks() {
	}

	static boolean isEmpty() {
		return byPlayer.isEmpty();
	}

	/** The player's payload, or null if the player is not a friend with looks. */
	static Payload of(UUID player) {
		return byPlayer.get(player);
	}

	static void replace(Map<UUID, Payload> next) {
		byPlayer = Map.copyOf(next);
	}

	/** Replaces the payloads of the players in {@code changed}, and keeps the others. */
	static void update(Map<UUID, Payload> changed) {
		Map<UUID, Payload> next = new HashMap<>(byPlayer);
		next.putAll(changed);
		byPlayer = Map.copyOf(next);
	}
}
