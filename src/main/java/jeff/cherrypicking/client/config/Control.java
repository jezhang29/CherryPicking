package jeff.cherrypicking.client.config;

import java.util.function.Function;

/**
 * What kind of widget a {@link Setting} is edited with, and the limits that
 * widget enforces.
 *
 * <p>Sealed so the screen builder's switch is exhaustive: adding a control kind
 * that the screen cannot draw is a compile error rather than a setting that
 * silently goes missing.
 *
 * <p>All four kinds are here from the start even though the mod only uses one
 * so far. They cost nothing while unused, and they are what lets the first
 * slider or dropdown arrive as a line in {@link Settings} and no screen code.
 */
public sealed interface Control<T> {
	/** An on/off switch. */
	record Flag() implements Control<Boolean> {
	}

	/**
	 * A whole-number slider.
	 *
	 * @param zeroLabel what to show instead of {@code 0}, or empty for none;
	 *                  this is how "0 means work it out yourself" reads as
	 *                  "Auto" rather than as a number
	 */
	record Whole(int min, int max, int step, String unit, String zeroLabel) implements Control<Integer> {
		public Whole(int min, int max, int step, String unit) {
			this(min, max, step, unit, "");
		}
	}

	/** A decimal slider. */
	record Real(double min, double max, double step, Format format) implements Control<Double> {
	}

	/** A cycle through the constants of an enum. */
	record Choice<E extends Enum<E>>(Class<E> type, Function<E, String> label) implements Control<E> {
	}

	/** How a {@link Real} value is written out next to its slider. */
	enum Format {
		/** Two decimals, no unit. */
		PLAIN,
		/** Two decimals followed by "blocks". */
		BLOCKS,
		/** One decimal followed by "s". */
		SECONDS,
		/** The value times 100, followed by "%". */
		PERCENT
	}
}
