package jeff.cherrypicking.client.screen;

/**
 * The settings screen's settings about itself, and the only part of this package the registry
 * reaches.
 *
 * <p>Everything else here - {@link Grid}, {@link Widgets}, {@link Tooltip} - stays package-private,
 * because a layout constant is not something the rest of the mod should be able to reach into. This
 * class is the face: three values, each held by the class that actually uses it, each with the
 * bounds the slider needs. See docs/dungeon-layer.md §13, Appearance · Screen.
 */
public final class ScreenSettings {
	public static final int MIN_CARD_WIDTH = Grid.MIN_CARD_WIDTH;
	public static final int MAX_CARD_WIDTH = Grid.MAX_CARD_WIDTH;

	private ScreenSettings() {
	}

	/**
	 * How narrow a column may get. Wider cards mean fewer columns, which is the trade: long
	 * labels stop being cut off and more scrolling starts.
	 */
	public static int cardWidth() {
		return Grid.cardWidth();
	}

	public static void cardWidth(int value) {
		Grid.cardWidth(value);
	}

	/** Shorter rows, so more settings fit before the scrolling starts. */
	public static boolean compact() {
		return Widgets.compact();
	}

	public static void compact(boolean value) {
		Widgets.compact(value);
	}

	/** Whether a hovered row explains itself and shows its key from the config file. */
	public static boolean tooltips() {
		return Tooltip.enabled();
	}

	public static void tooltips(boolean value) {
		Tooltip.enabled(value);
	}
}
