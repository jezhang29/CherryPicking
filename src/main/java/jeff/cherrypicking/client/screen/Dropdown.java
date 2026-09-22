package jeff.cherrypicking.client.screen;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import jeff.cherrypicking.client.config.Control;
import jeff.cherrypicking.client.config.Setting;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A choice's dropdown: every option in a list under the chip, the current one marked.
 *
 * <pre>
 * ┌──────────────────────────────┐
 * │ Catppuccin Mocha   ■■■■■■■   │  a preview, when the choice has one
 * │▌GitHub Dark Dimmed ■■■■■■■  ▐│  the current option; a scroll bar when the list is long
 * │ Nord               ■■■■■■■   │
 * └──────────────────────────────┘
 * </pre>
 *
 * <p>A click on an option sets it, saves, and closes the list. The Up and Down keys step through
 * the options with the list open, and each step takes effect at once, so a player can walk
 * through the themes and watch the screen change under the list. The wheel scrolls a list that
 * is too long for the panel.
 */
final class Dropdown<E extends Enum<E>> implements Popover {
	private static final int ROW = 12;
	private static final int PAD = 4;
	private static final int MAX_ROWS = 14;
	private static final int PREVIEW = 7;
	private static final int PREVIEW_GAP = 1;
	/** Between the label column and the preview. */
	private static final int SPACING = 8;
	private static final int BAR = 2;

	private final Setting<E> setting;
	private final Control.Choice<E> choice;
	private final E[] options;
	private final Runnable save;

	private final int x;
	private final int y;
	private final int width;
	private final int rows;
	/** The first option shown, when the list scrolls. */
	private int first;

	/**
	 * @param chip   the row's chip; the list opens under it, or above it if there is no room
	 * @param bounds the panel; the list is kept inside it
	 * @param save   writes the config file
	 */
	@SuppressWarnings("unchecked")
	static <E extends Enum<E>> Dropdown<E> of(Font font, Setting<?> setting, Control.Choice<E> choice,
			Chrome.Rect chip, Chrome.Rect bounds, Runnable save) {
		return new Dropdown<>(font, (Setting<E>) setting, choice, chip, bounds, save);
	}

	private Dropdown(Font font, Setting<E> setting, Control.Choice<E> choice, Chrome.Rect chip,
			Chrome.Rect bounds, Runnable save) {
		this.setting = setting;
		this.choice = choice;
		this.options = choice.type().getEnumConstants();
		this.save = save;

		int labels = 0;
		int swatches = 0;
		for (E option : options) {
			labels = Math.max(labels, font.width(choice.label().apply(option)));
			swatches = Math.max(swatches, choice.preview().apply(option).size());
		}
		int preview = swatches == 0 ? 0 : SPACING + swatches * (PREVIEW + PREVIEW_GAP) - PREVIEW_GAP;
		this.width = Math.min(bounds.width(), Math.max(chip.width(), 2 * PAD + labels + preview + BAR + 2));
		this.rows = Math.max(1, Math.min(Math.min(options.length, MAX_ROWS), (bounds.height() - 2) / ROW));
		int height = height();

		this.x = Math.clamp(chip.right() - width, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
		int below = chip.bottom() + 1;
		int top = below + height <= bounds.bottom() ? below : chip.y() - 1 - height;
		this.y = Math.clamp(top, bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));
		show(setting.value().ordinal());
	}

	private int height() {
		return rows * ROW + 2;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Chrome.box(graphics, x, y, width, height(), Role.PANEL, Role.BORDER_STRONG);
		E current = setting.value();
		for (int row = 0; row < rows; row++) {
			E option = options[first + row];
			int rowY = y + 1 + row * ROW;
			if (option == current) {
				Chrome.fill(graphics, x + 1, rowY, width - 2, ROW, Role.RAIL_ACTIVE);
				Chrome.fill(graphics, x + 1, rowY, 2, ROW, Role.ACCENT);
			} else if (Chrome.inside(mouseX, mouseY, x + 1, rowY, width - 2, ROW)) {
				Chrome.fill(graphics, x + 1, rowY, width - 2, ROW, Role.HOVER);
			}
			Text.draw(graphics, font, choice.label().apply(option), x + PAD, rowY + (ROW - font.lineHeight) / 2 + 1,
					Theme.of(Role.TEXT));

			List<Integer> preview = choice.preview().apply(option);
			int right = x + width - PAD - BAR;
			int left = right - preview.size() * (PREVIEW + PREVIEW_GAP) + PREVIEW_GAP;
			int top = rowY + (ROW - PREVIEW) / 2;
			for (int i = 0; i < preview.size(); i++) {
				int cellX = left + i * (PREVIEW + PREVIEW_GAP);
				graphics.fill(cellX, top, cellX + PREVIEW, top + PREVIEW, preview.get(i));
			}
			if (!preview.isEmpty()) {
				// One edge round the whole strip, so a background colour close to the panel's own
				// still reads as a square.
				Chrome.edge(graphics, left - 1, top - 1, right - left + 2, PREVIEW + 2, Role.BORDER);
			}
		}

		if (rows < options.length) {
			int track = height() - 2;
			int thumb = Math.max(6, track * rows / options.length);
			int thumbY = y + 1 + (track - thumb) * first / (options.length - rows);
			Chrome.fill(graphics, x + width - 1 - BAR, thumbY, BAR, thumb, Role.TEXT_FAINT);
		}
	}

	@Override
	public boolean click(MouseButtonEvent event, boolean doubleClick) {
		if (!Chrome.inside(event.x(), event.y(), x, y, width, height())) {
			return false;
		}
		int row = (int) ((event.y() - y - 1) / ROW);
		if (event.button() != 0 || row < 0 || row >= rows) {
			return true;
		}
		setting.value(options[first + row]);
		save.run();
		return false;
	}

	@Override
	public void scrolled(double amount) {
		if (amount != 0) {
			first = Math.clamp(first - (amount > 0 ? 1 : -1), 0, options.length - rows);
		}
	}

	/** Up and Down step to the neighbouring option and apply it at once; the list stays open. */
	@Override
	public boolean key(KeyEvent event) {
		int step = switch (event.key()) {
			case InputConstants.KEY_UP -> -1;
			case InputConstants.KEY_DOWN -> 1;
			default -> 0;
		};
		if (step == 0) {
			return false;
		}
		int index = Math.clamp(setting.value().ordinal() + step, 0, options.length - 1);
		setting.value(options[index]);
		save.run();
		show(index);
		return true;
	}

	/** Scrolls just far enough that option {@code index} is in view. */
	private void show(int index) {
		if (index < first) {
			first = index;
		} else if (index >= first + rows) {
			first = index - rows + 1;
		}
		first = Math.clamp(first, 0, options.length - rows);
	}
}
