package jeff.cherrypicking.client.screen;

import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The drawing primitives every part of the screen is made of: filled boxes, edges, dividers,
 * focus rings and small text buttons. Colours are always {@link Role}s, never palette names.
 */
final class Chrome {
	private Chrome() {
	}

	static void fill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Role role) {
		graphics.fill(x, y, x + width, y + height, Theme.of(role));
	}

	static void edge(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Role role) {
		graphics.outline(x, y, width, height, Theme.of(role));
	}

	/** A filled box with a one-pixel edge. */
	static void box(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Role fill, Role edge) {
		fill(graphics, x, y, width, height, fill);
		edge(graphics, x, y, width, height, edge);
	}

	static void horizontal(GuiGraphicsExtractor graphics, int x1, int x2, int y, Role role) {
		graphics.fill(x1, y, x2, y + 1, Theme.of(role));
	}

	static void vertical(GuiGraphicsExtractor graphics, int x, int y1, int y2, Role role) {
		graphics.fill(x, y1, x + 1, y2, Theme.of(role));
	}

	/** One pixel outside the box, so it does not cover what it rings. */
	static void focusRing(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		edge(graphics, x - 1, y - 1, width + 2, height + 2, Role.ACCENT);
	}

	/** A small text button: {@code face} fill, {@code HOVER} while hovered, {@code ACCENT} while flashing. */
	static void button(GuiGraphicsExtractor graphics, Font font, String label, int x, int y, int width,
			int height, boolean hovered, boolean flashing, Role face, Role text) {
		Role fill = flashing ? Role.ACCENT : hovered ? Role.HOVER : face;
		box(graphics, x, y, width, height, fill, Role.BORDER);
		Text.centred(graphics, font, label, x + width / 2, y + (height - font.lineHeight) / 2 + 1,
				Theme.of(flashing ? Role.PANEL : text));
	}

	static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** A box on screen, for the places that hit-test what they drew. */
	record Rect(int x, int y, int width, int height) {
		int right() {
			return x + width;
		}

		int bottom() {
			return y + height;
		}

		boolean contains(double mouseX, double mouseY) {
			return inside(mouseX, mouseY, x, y, width, height);
		}
	}
}
