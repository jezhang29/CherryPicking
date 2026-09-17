package jeff.cherrypicking.client.config;

/**
 * Where a {@link Setting} sits in the config screen: a tab, and a named group
 * inside it.
 *
 * <p>Declaration order here is the order the tabs and groups appear, and the
 * order settings appear inside a group is their order in {@link Settings}. The
 * screen is generated from those two facts alone, so a new setting needs no
 * screen code - see the class note on {@link Settings}.
 *
 * <p>A group nothing is filed under is not drawn, so sections may be declared
 * ahead of the settings that will fill them.
 */
public enum Section {
	GENERAL("General", "General");

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

	/** The heading drawn above this group's settings. */
	public String group() {
		return group;
	}
}
