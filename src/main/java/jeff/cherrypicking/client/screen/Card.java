package jeff.cherrypicking.client.screen;

import java.util.List;

import jeff.cherrypicking.client.config.Entry;
import jeff.cherrypicking.client.config.Section;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * One {@link Section} drawn as a card: a title bar, and one row per entry beneath it.
 *
 * <p>Clicking the title bar folds the card to the bar alone. A search result card is not foldable:
 * folding away what was just searched for would hide the answer.
 *
 * @param title    the group name, or {@code tab · group} in search results
 * @param entries  the rows, in registry order
 * @param foldable false for search results
 */
record Card(Section section, String title, List<Entry> entries, boolean foldable) {
	static final int HEADER = 13;
	/** Space above the first row and below the last. */
	static final int INSET = 2;

	int height(boolean folded) {
		return folded ? HEADER : HEADER + INSET + entries.size() * Widgets.row() + INSET;
	}

	/** The row at {@code mouseY}, or null when it is on the title bar or the insets. */
	Entry rowAt(int top, double mouseY) {
		int offset = (int) Math.floor(mouseY) - (top + HEADER + INSET);
		if (offset < 0) {
			return null;
		}
		int index = offset / Widgets.row();
		return index < entries.size() ? entries.get(index) : null;
	}

	/**
	 * @param hovered   the entry under the cursor, or null
	 * @param flashing  the key of the action whose button is flashing, or null
	 */
	void draw(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, boolean folded,
			Entry hovered, String flashing) {
		Chrome.box(graphics, x, y, width, height(folded), Role.CARD, Role.BORDER);
		Chrome.fill(graphics, x, y, width, HEADER, Role.CARD_HEADER);

		int textY = y + (HEADER - font.lineHeight) / 2 + 1;
		int chevronWidth = foldable ? font.width("▸") + 4 : 0;
		Text.draw(graphics, font, Text.fit(font, title, width - 10 - chevronWidth), x + 5, textY,
				Theme.of(Role.TEXT));
		if (foldable) {
			Text.right(graphics, font, folded ? "▸" : "▾", x + width - 5, textY, Theme.of(Role.TEXT_DIM));
		}
		if (folded) {
			return;
		}

		int rowY = y + HEADER + INSET;
		for (Entry entry : entries) {
			Widgets.draw(graphics, font, entry, x + 1, rowY, width - 2, entry == hovered,
					entry.key().equals(flashing));
			rowY += Widgets.row();
		}
	}
}
