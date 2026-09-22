package jeff.cherrypicking.client.config;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One thing the player can change.
 *
 * <p>A setting does not store its own value. It reads and writes the live field
 * that already owns it, so there is exactly one copy of every value and no way
 * for the screen and the feature to disagree. What a setting adds is the name,
 * the one-sentence description, the widget, the default to reset to, and the
 * key it is saved under.
 *
 * @param key      the name this is saved under in the config file; never change
 *                 one after release, or saved values are silently dropped
 * @param label    the name shown in the screen
 * @param blurb    <b>one sentence</b> saying what it does, in plain words
 * @param section  the tab and group it is drawn in
 * @param control  the widget and its limits
 * @param fallback the value Reset restores
 * @param read     reads the live value from whatever owns it
 * @param write    pushes a new value into whatever owns it
 * @param dependency true while the feature this belongs to is on; see
 *                 {@link Entry#available()}
 */
public record Setting<T>(
		String key,
		String label,
		String blurb,
		Section section,
		Control<T> control,
		T fallback,
		Supplier<T> read,
		Consumer<T> write,
		Supplier<Boolean> dependency) implements Entry {

	@Override
	public boolean available() {
		return dependency.get();
	}

	/** The value in force right now. */
	public T value() {
		return read.get();
	}

	/** Puts the value into force. Clamping is the owner's job, not this one's. */
	public void value(T fresh) {
		write.accept(fresh);
	}

	/** Restores {@link #fallback}. */
	public void reset() {
		write.accept(fallback);
	}
}
