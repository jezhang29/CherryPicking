package jeff.cherrypicking.client.config;

/**
 * Owns the one setting the mod ships with while it has no features yet.
 *
 * <p>It exists so the settings screen has something to draw, and so the whole
 * path - registry, screen, widget, save, load - is proven in the real client
 * before a feature depends on it. It controls nothing, and it is meant to be
 * deleted in the same change that registers the first real setting.
 *
 * <p>The default lives here, at the field, rather than in {@link Settings}. That
 * is the rule every setting follows: the registry takes whatever its owner
 * holds at registration time, so Reset restores what the code actually ships
 * and there is never a second copy of a default to fall out of step.
 */
public final class Placeholder {
	private static volatile boolean enabled = true;

	private Placeholder() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void enabled(boolean on) {
		enabled = on;
	}
}
