package jeff.cherrypicking.client.screen;

import jeff.cherrypicking.client.config.Entry;
import jeff.cherrypicking.client.config.Setting;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The hovered row's description, drawn last so nothing covers it.
 *
 * <p>A setting's key goes underneath in faint text. It is the setting's name in
 * {@code cherrypicking.json}, and having it on screen is what makes editing that file by hand
 * possible. An action has no saved name, so it shows none.
 */
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
		String key = entry instanceof Setting<?> ? entry.key() : null;

		int textWidth = 0;
		for (FormattedCharSequence line : font.split(blurb, WRAP)) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int blurbHeight = font.wordWrapHeight(blurb, WRAP);
		if (key != null) {
			textWidth = Math.max(textWidth, font.width(key));
		}

		int width = textWidth + 2 * PAD;
		int height = blurbHeight + (key == null ? 0 : font.lineHeight + 2) + 2 * PAD - 1;
		int x = Math.clamp(mouseX + OFFSET_X, 2, Math.max(2, screenWidth - width - 2));
		int y = Math.clamp(mouseY + OFFSET_Y, 2, Math.max(2, screenHeight - height - 2));

		Chrome.box(graphics, x, y, width, height, Role.HEADER, Role.BORDER);
		graphics.textWithWordWrap(font, blurb, x + PAD, y + PAD, WRAP, Theme.of(Role.TEXT), false);
		if (key != null) {
			Text.draw(graphics, font, key, x + PAD, y + PAD + blurbHeight + 2, Theme.of(Role.TEXT_FAINT));
		}
	}
}
