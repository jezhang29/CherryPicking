package jeff.cherrypicking.client.config;

import java.util.List;
import java.util.function.Function;

import jeff.cherrypicking.client.theme.Swatch;

/**
 * What kind of widget a {@link Setting} is edited with, and the limits that
 * widget enforces.
 *
 * <p>Sealed so the screen builder's switch is exhaustive: adding a control kind
 * that the screen cannot draw is a compile error rather than a setting that
 * silently goes missing.
 *
 * <p>Every kind is here even while the mod uses only some of them. They cost
 * nothing while unused, and they are what lets the first slider or colour
 * arrive as a line in {@link Settings} and no screen code.
 *
 * <p>Only value kinds belong here. A button has no value, so it is an
 * {@link Action} rather than a {@code Control}.
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

	/**
	 * One of the constants of an enum, picked from a dropdown.
	 *
	 * @param preview a few ARGB colours drawn beside each option in the dropdown, or none; this is
	 *                how a theme shows what it looks like before it is picked
	 */
	record Choice<E extends Enum<E>>(Class<E> type, Function<E, String> label,
			Function<E, List<Integer>> preview) implements Control<E> {
		public Choice(Class<E> type, Function<E, String> label) {
			this(type, label, constant -> List.of());
		}
	}

	/**
	 * A colour, picked from the live flavour's palette, dragged from the shade square, or typed as
	 * hex. Its popover carries the colour's own opacity slider, so a feature never needs a separate
	 * opacity setting beside it.
	 *
	 * @param fill true for a box colour: the popover then has a second slider, for how solid the
	 *             box's inside is, apart from its outline
	 */
	record Colour(boolean fill) implements Control<Swatch> {
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
