package jeff.cherrypicking.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Drawing and fitting text in a space measured in pixels. The peer of skyblock-flipper's
 * {@code Labels}, copied rather than imported: the mods share no code.
 *
 * <p>Everything is drawn without a shadow. Latte is a light theme, and vanilla's drop shadow on a
 * light panel reads as a smudge rather than as depth.
 */
final class Text {
	private static final String ELLIPSIS = "...";

	private Text() {
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int argb) {
		graphics.text(font, text, x, y, argb, false);
	}

	/** Draws {@code text} ending at {@code right}. */
	static void right(GuiGraphicsExtractor graphics, Font font, String text, int right, int y, int argb) {
		draw(graphics, font, text, right - font.width(text), y, argb);
	}

	/** Draws {@code text} centred on {@code centre}. */
	static void centred(GuiGraphicsExtractor graphics, Font font, String text, int centre, int y, int argb) {
		draw(graphics, font, text, centre - font.width(text) / 2, y, argb);
	}

	/** @return {@code text}, or as much of it as fits followed by an ellipsis */
	static String fit(Font font, String text, int available) {
		if (font.width(text) <= available) {
			return text;
		}
		int budget = available - font.width(ELLIPSIS);
		if (budget <= 0) {
			return "";
		}
		return font.plainSubstrByWidth(text, budget) + ELLIPSIS;
	}
}
