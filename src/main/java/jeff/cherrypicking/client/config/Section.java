package jeff.cherrypicking.client.config;

/**
 * Where an {@link Entry} sits in the config screen: a tab, and a named group
 * inside it.
 *
 * <p>Declaration order here is the order the tabs and groups appear, and the
 * order entries appear inside a group is their order in {@link Settings}. The
 * screen is generated from those two facts alone, so a new setting needs no
 * screen code - see the class note on {@link Settings}.
 *
 * <p>A group nothing is filed under is not drawn, and neither is a tab with no
 * drawn groups, so sections may be declared ahead of the settings that will
 * fill them.
 */
public enum Section {
	THEME("Appearance", "Theme"),
	SCREEN("Appearance", "Screen"),
	PUZZLES("Puzzles", "All puzzles"),
	BLAZE("Puzzles", "Blaze"),
	BEAMS("Puzzles", "Creeper Beams"),
	LIVID("Boss", "Livid"),
	LIVID_TITLE("Boss", "Livid title"),
	STAR_MOBS("Mobs", "Starred mobs"),
	QUITTING("Advanced", "Quitting"),
	DEVELOPER("Advanced", "Developer");

	private final String tab;
	private final String group;

	Section(String tab, String group) {
		this.tab = tab;
		this.group = group;
	}

	/** The screen tab this group is drawn under. */
	public String tab() {
		return tab;
	}

	/** The heading drawn above this group's entries. */
	public String group() {
		return group;
	}
}
