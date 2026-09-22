package jeff.cherrypicking.client.config;

import java.util.function.Supplier;

/**
 * A button in the config screen. It has no value, so it is not a {@link Setting} and is never
 * saved.
 *
 * <p>Start the label with the verb: the button on the right of the row shows the label's first
 * word, so "Reset water" draws as "Reset water [Reset]".
 *
 * @param key        unique, for ordering and tests; never saved
 * @param run        what the button does, on the client thread
 * @param dependency true while the button can be used; see {@link Entry#available()}
 */
public record Action(
		String key,
		String label,
		String blurb,
		Section section,
		Runnable run,
		Supplier<Boolean> dependency) implements Entry {

	@Override
	public boolean available() {
		return dependency.get();
	}
}
