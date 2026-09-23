package jeff.cherrypicking.client.screen;

import jeff.cherrypicking.client.config.Entry;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The hovered row's description, drawn last so nothing covers it. */
final class Tooltip {
	/** The {@code screen.tooltips} flag. The default is here, at the field. */
	private static volatile boolean enabled = true;

	private static final int WRAP = 180;
	private static final int PAD = 4;
	private static final int OFFSET_X = 8;
	private static final int OFFSET_Y = 10;

	private Tooltip() {
	}

	/** Whether a hovered row explains itself. Off leaves the labels to carry it. */
	static boolean enabled() {
		return enabled;
	}

	static void enabled(boolean value) {
		enabled = value;
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, Entry entry, int mouseX, int mouseY,
			int screenWidth, int screenHeight) {
		Component blurb = Component.literal(entry.blurb());

		int textWidth = 0;
		for (FormattedCharSequence line : font.split(blurb, WRAP)) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int blurbHeight = font.wordWrapHeight(blurb, WRAP);

		int width = textWidth + 2 * PAD;
		int height = blurbHeight + 2 * PAD - 1;
		int x = Math.clamp(mouseX + OFFSET_X, 2, Math.max(2, screenWidth - width - 2));
		int y = Math.clamp(mouseY + OFFSET_Y, 2, Math.max(2, screenHeight - height - 2));

		Chrome.box(graphics, x, y, width, height, Role.HEADER, Role.BORDER);
		graphics.textWithWordWrap(font, blurb, x + PAD, y + PAD, WRAP, Theme.of(Role.TEXT), false);
	}
}
