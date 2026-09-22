package jeff.cherrypicking.client.screen;

import java.util.List;

import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The left rail: one flat entry per tab, no nesting, no scrolling. The active tab gets a
 * two-pixel accent bar on its left edge and a highlighted fill.
 */
final class Rail {
	static final int WIDTH = 84;
	static final int ROW = 16;
	/** Space above the first tab. */
	private static final int TOP = 4;

	private Rail() {
	}

	/** @param active the selected tab, or null while a search is showing every tab at once */
	static void draw(GuiGraphicsExtractor graphics, Font font, List<String> tabs, String active,
			int x, int y, int height, double mouseX, double mouseY) {
		Chrome.fill(graphics, x, y, WIDTH, height, Role.RAIL);
		Chrome.vertical(graphics, x + WIDTH - 1, y, y + height, Role.BORDER);

		int hovered = hit(tabs, x, y, mouseX, mouseY);
		for (int i = 0; i < tabs.size(); i++) {
			int rowY = y + TOP + i * ROW;
			String tab = tabs.get(i);
			boolean selected = tab.equals(active);
			if (selected) {
				Chrome.fill(graphics, x, rowY, WIDTH - 1, ROW, Role.RAIL_ACTIVE);
				Chrome.fill(graphics, x, rowY, 2, ROW, Role.ACCENT);
			} else if (i == hovered) {
				Chrome.fill(graphics, x, rowY, WIDTH - 1, ROW, Role.HOVER);
			}
			Text.draw(graphics, font, Text.fit(font, tab, WIDTH - 14), x + 8,
					rowY + (ROW - font.lineHeight) / 2 + 1,
					Theme.of(selected ? Role.TEXT : Role.TEXT_DIM));
		}
	}

	/** @return the index of the tab under the cursor, or -1 */
	static int hit(List<String> tabs, int x, int y, double mouseX, double mouseY) {
		if (mouseX < x || mouseX >= x + WIDTH - 1 || mouseY < y + TOP) {
			return -1;
		}
		int index = (int) ((mouseY - y - TOP) / ROW);
		return index < tabs.size() ? index : -1;
	}
}
