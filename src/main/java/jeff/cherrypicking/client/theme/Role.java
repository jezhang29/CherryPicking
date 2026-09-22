package jeff.cherrypicking.client.theme;

/**
 * What a colour is <i>for</i>, rather than which colour it is.
 *
 * <p>The screen names roles and never palette colours, so re-flavouring is one lookup in
 * {@link Theme#of(Role)} and no screen edits. A role with no palette name follows the player's
 * chosen accent.
 */
public enum Role {
	/** Behind the whole panel, over the world. */
	BACKDROP("crust", 60),
	/** The panel fill. */
	PANEL("base"),
	/** The title and search strip. */
	HEADER("mantle"),
	/** The count and buttons strip. */
	FOOTER("mantle"),
	/** The tab rail. */
	RAIL("crust"),
	/** The selected tab's fill. */
	RAIL_ACTIVE("surface0"),
	/** A card's body. */
	CARD("surface0"),
	/** A card's title bar. */
	CARD_HEADER("surface1"),
	/** One-pixel edges. */
	BORDER("surface1"),
	/** The panel's outer edge. */
	BORDER_STRONG("surface2"),
	/** The hovered row's fill. */
	HOVER("surface2"),
	/** Labels and values. */
	TEXT("text"),
	/** Units, group titles, the search hint. */
	TEXT_DIM("subtext0"),
	/** A disabled row. */
	TEXT_FAINT("overlay1"),
	/** The active tab bar, a slider's fill, a focus ring. */
	ACCENT(null),
	/** A slider's track. */
	ACCENT_DIM(null, 40),
	/** A flag that is on. */
	ON("green"),
	/** A flag that is off. */
	OFF("overlay0"),
	/** "Solver stood down" states. */
	WARNING("yellow"),
	/** Reset all. */
	DANGER("red");

	/** Null means the chosen accent. */
	private final String paletteName;
	private final int alphaPercent;

	Role(String paletteName) {
		this(paletteName, 100);
	}

	Role(String paletteName, int alphaPercent) {
		this.paletteName = paletteName;
		this.alphaPercent = alphaPercent;
	}

	/** @return the palette colour this role takes, or null when it follows the accent */
	public String paletteName() {
		return paletteName;
	}

	public int alphaPercent() {
		return alphaPercent;
	}
}
