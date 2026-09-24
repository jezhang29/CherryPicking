package jeff.cherrypicking.client.cosmetics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.DungeonState;

import net.minecraft.client.Minecraft;

/**
 * Fetches your friends' looks from the relay into {@link FriendLooks}: on joining SkyBlock, when the
 * friend list changes, and then every {@code pollSeconds}. The relay answers "not changed" to a
 * fetch with the last {@code ETag}, which keeps the old looks.
 *
 * <p>Put your own name in the list to see your own shared looks on yourself; that is the self-test
 * in docs/friend-cosmetics-plan.md, section 11.1.
 *
 * <p>Client thread only; the fetch reports back through {@link Minecraft#execute}.
 */
public final class Poller {
	/** The relay reads at most this many names. */
	static final int MAX_FRIENDS = 10;
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern UUID_HEX = Pattern.compile("[0-9a-f]{32}");

	/** The {@code friendCosmetics.friends} setting: names, separated by commas. */
	private static volatile String friends = "";
	/** The {@code friendCosmetics.pollSeconds} setting. */
	private static volatile int pollSeconds = 60;
	/** Set when the list changes, so the next tick fetches. */
	private static volatile boolean refetch = true;

	private static int ticks;
	private static int dueAt;
	private static boolean fetching;
	private static boolean wasOnSkyBlock;
	/** The last fetch's {@code ETag}; it describes what {@link FriendLooks} holds now. */
	private static String etag;
	private static List<String> lastMissing = List.of();

	private Poller() {
	}

	public static String friends() {
		return friends;
	}

	/** A name that cannot be a Minecraft name is left out, with a warning. */
	public static void friends(String value) {
		friends = value;
		refetch = true;
		List<String> bad = new ArrayList<>();
		for (String name : value.split("[,\\s]+")) {
			if (!name.isEmpty() && !NAME.matcher(name).matches()) {
				bad.add(name);
			}
		}
		if (!bad.isEmpty()) {
			CherryPicking.LOGGER.warn("Friend looks: {} cannot be Minecraft names; left out.", bad);
		}
	}

	public static int pollSeconds() {
		return pollSeconds;
	}

	public static void pollSeconds(int value) {
		pollSeconds = value;
	}

	/** Forgets what was fetched, so turning the feature on again fetches in full. */
	static void reset() {
		etag = null;
		lastMissing = List.of();
		refetch = true;
	}

	/** Client thread, every tick. */
	static void tick(Minecraft client) {
		ticks++;
		boolean onSkyBlock = Cosmetics.enabled() && client.player != null && DungeonState.inSkyBlock();
		if (onSkyBlock && !wasOnSkyBlock) {
			refetch = true;
		}
		wasOnSkyBlock = onSkyBlock;
		if (!onSkyBlock || fetching || !RelayClient.ready() || (!refetch && ticks < dueAt)) {
			return;
		}

		refetch = false;
		dueAt = ticks + pollSeconds * 20;
		List<String> names = names(friends);
		if (names.isEmpty()) {
			FriendLooks.replace(Map.of());
			etag = null;
			return;
		}
		fetching = true;
		RelayClient.fetch(names, etag).whenComplete((fetched, error) -> client.execute(() -> {
			fetching = false;
			if (error != null || fetched.isEmpty() || !names.equals(names(friends))) {
				// A failure keeps the old looks; RelayClient logged it. A list that changed during
				// the fetch was already marked for a new one.
				return;
			}
			Players players = players(fetched.get().body());
			etag = fetched.get().etag();
			FriendLooks.replace(players.looks());
			CherryPicking.LOGGER.info("Relay: fetched looks for {} of {} friends.", players.looks().size(),
					names.size());
			if (!players.missing().equals(lastMissing) && !players.missing().isEmpty()) {
				CherryPicking.LOGGER.info("Relay: {} did not share looks yet.", players.missing());
			}
			lastMissing = players.missing();
		}));
	}

	/** The friend list as names the relay accepts: no blanks, no repeats, at most ten. */
	static List<String> names(String list) {
		Map<String, String> byLowerCase = new LinkedHashMap<>();
		for (String name : list.split("[,\\s]+")) {
			if (NAME.matcher(name).matches() && byLowerCase.size() < MAX_FRIENDS) {
				byLowerCase.putIfAbsent(name.toLowerCase(), name);
			}
		}
		return List.copyOf(byLowerCase.values());
	}

	/** What a fetch gave: each friend's payload by player UUID, and the friends with none. */
	record Players(Map<UUID, Payload> looks, List<String> missing) {
	}

	/**
	 * Reads the relay's reply (relay/worker.js, {@code fetchMany}). Each player's payload goes
	 * through {@link Payload#decode}; a player entry that is malformed is left out, with a warning.
	 */
	static Players players(JsonObject reply) {
		Map<UUID, Payload> looks = new HashMap<>();
		List<String> missing = new ArrayList<>();
		if (!(reply.get("players") instanceof JsonArray entries)) {
			CherryPicking.LOGGER.warn("Friend looks: the relay's reply has no player list.");
			return new Players(Map.of(), List.of());
		}
		for (JsonElement entry : entries) {
			if (!(entry instanceof JsonObject player)
					|| !(player.get("name") instanceof JsonPrimitive name) || !name.isString()) {
				CherryPicking.LOGGER.warn("Friend looks: a player in the relay's reply has no name; left out.");
				continue;
			}
			if (player.has("missing")) {
				missing.add(name.getAsString());
				continue;
			}
			Optional<UUID> uuid = player.get("uuid") instanceof JsonPrimitive text && text.isString()
					? uuid(text.getAsString()) : Optional.empty();
			if (uuid.isEmpty() || !(player.get("data") instanceof JsonObject data)) {
				CherryPicking.LOGGER.warn("Friend looks: the relay's entry for {} has no UUID or data; left out.",
						name.getAsString());
				continue;
			}
			Payload.decode(data.toString(), name.getAsString() + "'s looks")
					.ifPresent(payload -> looks.put(uuid.get(), payload));
		}
		return new Players(Map.copyOf(looks), List.copyOf(missing));
	}

	/** A UUID written as 32 hex digits, as Mojang and the relay write it. */
	static Optional<UUID> uuid(String hex) {
		if (!UUID_HEX.matcher(hex).matches()) {
			return Optional.empty();
		}
		return Optional.of(new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16),
				Long.parseUnsignedLong(hex.substring(16), 16)));
	}
}
