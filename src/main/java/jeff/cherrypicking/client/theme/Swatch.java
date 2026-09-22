package jeff.cherrypicking.client.theme;

import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A colour setting's value: either a palette name, which follows the flavour, or a literal ARGB,
 * which does not. Both carry their own opacity.
 *
 * <p>Every feature colour ships as a {@link Named}, so switching flavour re-tints every colour the
 * player has not overridden, with nothing saved and nothing reset. {@link Theme#resolve(Swatch)}
 * turns either kind into an {@code int} at read time.
 *
 * <p><b>Opacity belongs to the colour.</b> A {@link Named} keeps an alpha of its own rather than
 * borrowing the palette's, so dragging the picker's opacity slider does not quietly turn a palette
 * colour into a literal one and cut it loose from the flavour.
 *
 * <p><b>So does the fill.</b> Every swatch also carries a {@link #fill()} opacity, apart from its
 * outline's, so each box colour decides for itself how solid its inside is (check S4-09). A
 * colour that is never used for a fill simply never reads it.
 */
public sealed interface Swatch {
	/** How solid a box's inside is when nobody has said otherwise: about 35%. */
	int DEFAULT_FILL = 0x59;

	/** A palette name and an opacity: the colour follows the flavour, the opacity does not. */
	record Named(String name, int alpha, int fill) implements Swatch {
		public Named {
			alpha = Math.clamp(alpha, 0, 255);
			fill = Math.clamp(fill, 0, 255);
		}

		public Named(String name, int alpha) {
			this(name, alpha, DEFAULT_FILL);
		}

		public Named(String name) {
			this(name, 0xFF);
		}
	}

	/** A literal ARGB, and a fill opacity: follows nothing. */
	record Literal(int argb, int fill) implements Swatch {
		public Literal {
			fill = Math.clamp(fill, 0, 255);
		}

		public Literal(int argb) {
			this(argb, DEFAULT_FILL);
		}
	}

	static Swatch of(String paletteName) {
		return new Named(paletteName);
	}

	static Swatch of(String paletteName, int alpha) {
		return new Named(paletteName, alpha);
	}

	static Swatch of(int argb) {
		return new Literal(argb);
	}

	/** This colour's opacity, {@code 0}-{@code 255}. It is the outline's opacity on a box. */
	default int alpha() {
		return Theme.resolve(this) >>> 24;
	}

	/** How solid a box's inside is in this colour, {@code 0}-{@code 255}. */
	int fill();

	/** The same colour at a different opacity, keeping its kind and its fill. */
	default Swatch withAlpha(int alpha) {
		return switch (this) {
			case Named named -> new Named(named.name(), alpha, named.fill());
			case Literal literal -> new Literal(Math.clamp(alpha, 0, 255) << 24 | literal.argb() & 0x00FFFFFF,
					literal.fill());
		};
	}

	/** The same colour with a different fill opacity, keeping its kind and its outline. */
	default Swatch withFill(int fill) {
		return switch (this) {
			case Named named -> new Named(named.name(), named.alpha(), fill);
			case Literal literal -> new Literal(literal.argb(), fill);
		};
	}

	/**
	 * The config file's spelling: {@code "green"}, {@code "green@80"} when it is see-through, or
	 * {@code "#aarrggbb"}; then {@code "/ff"} when the fill is not {@link #DEFAULT_FILL}.
	 */
	default String written() {
		String colour = switch (this) {
			case Named named -> named.alpha() == 0xFF
					? named.name()
					: String.format(Locale.ROOT, "%s@%02x", named.name(), named.alpha());
			case Literal literal -> String.format(Locale.ROOT, "#%08x", literal.argb());
		};
		return fill() == DEFAULT_FILL ? colour : String.format(Locale.ROOT, "%s/%02x", colour, fill());
	}

	/**
	 * True when {@code text} names its own fill, so a file saved before fills were per colour can
	 * be told apart from one that chose the default.
	 */
	static boolean namesFill(String text) {
		return text.indexOf('/') >= 0;
	}

	/**
	 * The inverse of {@link #written()}. A palette name, with an optional {@code @aa} opacity,
	 * reads as {@link Named}; {@code #rrggbb} or {@code #aarrggbb} reads as {@link Literal}. Either
	 * may end in {@code /ff}, the fill; without it the fill is {@link #DEFAULT_FILL}.
	 *
	 * @return empty when the text is none of these
	 */
	static Optional<Swatch> read(String text) {
		String trimmed = text.strip();
		int slash = trimmed.lastIndexOf('/');
		if (slash >= 0) {
			OptionalInt fill = twoHexDigits(trimmed.substring(slash + 1));
			if (fill.isEmpty()) {
				return Optional.empty();
			}
			return colour(trimmed.substring(0, slash)).map(swatch -> swatch.withFill(fill.getAsInt()));
		}
		return colour(trimmed);
	}

	private static Optional<Swatch> colour(String trimmed) {
		int at = trimmed.lastIndexOf('@');
		if (at > 0) {
			String name = trimmed.substring(0, at);
			OptionalInt alpha = twoHexDigits(trimmed.substring(at + 1));
			if (Palette.NAMES.contains(name) && alpha.isPresent()) {
				return Optional.of(new Named(name, alpha.getAsInt()));
			}
		}
		if (Palette.NAMES.contains(trimmed)) {
			return Optional.of(new Named(trimmed));
		}
		OptionalInt argb = hex(trimmed);
		return argb.isPresent() ? Optional.of(new Literal(argb.getAsInt())) : Optional.empty();
	}

	/**
	 * Six hex digits as an opaque colour, or eight as {@code AARRGGBB}. The {@code #} is optional.
	 *
	 * @return empty for anything else
	 */
	static OptionalInt hex(String text) {
		String digits = text.strip();
		if (digits.startsWith("#")) {
			digits = digits.substring(1);
		}
		if (digits.length() != 6 && digits.length() != 8) {
			return OptionalInt.empty();
		}
		if (!hexDigits(digits)) {
			return OptionalInt.empty();
		}
		int value = Integer.parseUnsignedInt(digits, 16);
		return OptionalInt.of(digits.length() == 6 ? 0xFF000000 | value : value);
	}

	private static OptionalInt twoHexDigits(String digits) {
		return digits.length() == 2 && hexDigits(digits)
				? OptionalInt.of(Integer.parseUnsignedInt(digits, 16))
				: OptionalInt.empty();
	}

	private static boolean hexDigits(String digits) {
		for (int i = 0; i < digits.length(); i++) {
			if (Character.digit(digits.charAt(i), 16) < 0) {
				return false;
			}
		}
		return true;
	}
}
