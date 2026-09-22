package jeff.cherrypicking.client.theme;

import java.util.Locale;

/**
 * The live theme: which flavour, which accent, and the lookups that turn a {@link Role} or a
 * {@link Swatch} into an ARGB {@code int}.
 *
 * <p>This is the owner of the {@code theme.flavour} and {@code theme.accent} settings, so their
 * defaults live here. Nothing caches a resolved colour across frames: every caller resolves at
 * draw time, which is what makes a change take effect on the next frame everywhere at once.
 */
public final class Theme {
	/** The default, because it is what this machine's editors are set to. */
	private static volatile Flavour flavour = Flavour.LATTE;
	/** Catppuccin's own default accent. */
	private static volatile Accent accent = Accent.MAUVE;

	private Theme() {
	}

	/** The colour a role takes in the live flavour and accent. */
	public static int of(Role role) {
		String name = role.paletteName() == null ? accent.paletteName() : role.paletteName();
		return withAlpha(palette().of(name), role.alphaPercent());
	}

	/**
	 * A {@link Swatch.Named} looked up in the live flavour, at its own opacity; a
	 * {@link Swatch.Literal} as it is. The palette is opaque, so the name's alpha replaces it.
	 */
	public static int resolve(Swatch swatch) {
		return switch (swatch) {
			case Swatch.Named named -> named.alpha() << 24 | palette().of(named.name()) & 0x00FFFFFF;
			case Swatch.Literal literal -> literal.argb();
		};
	}

	/** The colour a box's inside takes in {@code swatch}: its colour at its own fill opacity. */
	public static int resolveFill(Swatch swatch) {
		return swatch.fill() << 24 | resolve(swatch) & 0x00FFFFFF;
	}

	/** The live flavour's colours. */
	public static Palette palette() {
		return Palettes.of(flavour);
	}

	public static Flavour flavour() {
		return flavour;
	}

	public static void flavour(Flavour chosen) {
		if (chosen != null) {
			flavour = chosen;
		}
	}

	public static Accent accent() {
		return accent;
	}

	public static void accent(Accent chosen) {
		if (chosen != null) {
			accent = chosen;
		}
	}

	private static int withAlpha(int argb, int percent) {
		if (percent >= 100) {
			return argb;
		}
		int alpha = Math.round(255 * percent / 100f);
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	/** Catppuccin's fourteen accents, as a choice for the {@code theme.accent} setting. */
	public enum Accent {
		ROSEWATER, FLAMINGO, PINK, MAUVE, RED, MAROON, PEACH,
		YELLOW, GREEN, TEAL, SKY, SAPPHIRE, BLUE, LAVENDER;

		/** The name in the palette, e.g. {@code "sapphire"}. */
		public String paletteName() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** What the screen shows, e.g. {@code "Sapphire"}. */
		public String label() {
			String name = paletteName();
			return Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}
	}
}
