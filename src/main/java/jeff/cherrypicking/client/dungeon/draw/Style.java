package jeff.cherrypicking.client.dungeon.draw;

/**
 * How a box is drawn.
 *
 * <p><b>The edge and the fill have separate opacities</b>, and both belong to
 * the box's colour: its picker has an outline slider and a fill slider (see
 * {@link jeff.cherrypicking.client.theme.Swatch#fill()}). So a bright outline
 * around a nearly clear fill, or a solid block, is set per colour, without
 * either choice dragging the other with it (check S4-09).
 */
public enum Style {
	FILLED("Filled", true, false),
	OUTLINE("Outline", false, true),
	FILLED_OUTLINE("Filled and outline", true, true);

	private final String label;
	private final boolean fill;
	private final boolean outline;

	Style(String label, boolean fill, boolean outline) {
		this.label = label;
		this.fill = fill;
		this.outline = outline;
	}

	/** What the settings screen calls it. */
	public String label() {
		return label;
	}

	public boolean fill() {
		return fill;
	}

	public boolean outline() {
		return outline;
	}
}
