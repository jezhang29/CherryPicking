package jeff.cherrypicking.client.dungeon;

import java.util.Map;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads coalroutegenerator's {@code /locraw} reply from Fabric Loader's object
 * share. This mod never sends {@code /locraw} itself: two mods asking at every
 * join trips Hypixel's command rate limit.
 *
 * <p>The contract is coalroutegenerator's {@code location.LocrawProbe}: under
 * {@value #KEY}, an immutable {@code Map<String, String>} of the reply's fields
 * plus {@code receivedAt} in epoch millis, and an empty map after a reset. JDK
 * types only, so nothing is imported from that mod. Without it the share is
 * empty, and {@link DungeonState} runs on the sidebar alone.
 */
public final class SharedLocraw {
	private static final String KEY = "coalroutegenerator:locraw";

	/** Replies that arrived before this are about a level we have left. */
	private static long notBefore;

	private SharedLocraw() {
	}

	/** Client thread, on a new level: ignore every reply that came before it. */
	public static void invalidate() {
		notBefore = System.currentTimeMillis();
	}

	/** The reply's {@code mode}, e.g. {@code dungeon}. Empty when there is no current reply. */
	public static String mode() {
		return field("mode");
	}

	private static String field(String name) {
		if (!(FabricLoader.getInstance().getObjectShare().get(KEY) instanceof Map<?, ?> reply)) {
			return "";
		}
		if (!(reply.get("receivedAt") instanceof String at) || parse(at) < notBefore) {
			return "";
		}
		return reply.get(name) instanceof String value ? value : "";
	}

	private static long parse(String millis) {
		try {
			return Long.parseLong(millis);
		} catch (NumberFormatException e) {
			return Long.MIN_VALUE;
		}
	}
}
