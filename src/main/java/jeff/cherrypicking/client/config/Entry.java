package jeff.cherrypicking.client.config;

/**
 * One row in the config screen: a {@link Setting}, which holds a value, or an {@link Action},
 * which does something.
 *
 * <p>Sealed so the screen's switch over it is exhaustive. {@link Settings#all()} returns entries,
 * which is the screen's view; {@link Settings#settings()} returns only settings, which is the
 * config file's view, so an action is never saved.
 */
public sealed interface Entry permits Setting, Action {
	/** Unique. A setting's is its name in the config file; an action's is never saved. */
	String key();

	/** The name shown in the screen. */
	String label();

	/** <b>One sentence</b> saying what it does, in plain words. */
	String blurb();

	/** The tab and group it is drawn in. */
	Section section();

	/**
	 * False while the feature this belongs to is switched off elsewhere. The row is then drawn
	 * greyed out and ignores clicks.
	 */
	boolean available();
}
