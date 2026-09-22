package jeff.cherrypicking.client.screen;

import java.util.Locale;

import jeff.cherrypicking.client.config.Action;
import jeff.cherrypicking.client.config.Control;
import jeff.cherrypicking.client.config.Entry;
import jeff.cherrypicking.client.config.Setting;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws and hit-tests one row per {@link Entry}: label on the left, control on the right.
 *
 * <p><b>This is the only file that knows what a {@link Control} looks like.</b> Drawing, clicking
 * and the wheel all switch over the sealed {@code Control}, so a new kind is a compile error here
 * and nowhere else. Drawing and hit-testing share the geometry helpers below, so what is clicked is
 * always what was drawn.
 *
 * <p>The unchecked casts are what each switch has just proved: {@code Control<T>} is the same
 * {@code T} as the setting's, so a {@code Control.Whole} only ever belongs to a
 * {@code Setting<Integer>}.
 */
final class Widgets {
	/** How tall a setting's row is, normally and with {@code screen.compact} on. */
	static final int ROW_NORMAL = 14;
	static final int ROW_COMPACT = 12;

	/** The {@code screen.compact} flag. The default is here, at the field. */
	private static volatile boolean compact;

	/** A row's height, as the cards and the hit tests measure it. */
	static int row() {
		return compact ? ROW_COMPACT : ROW_NORMAL;
	}

	static boolean compact() {
		return compact;
	}

	static void compact(boolean value) {
		compact = value;
	}

	private static final int PAD = 5;
	/** Space between the label and the control. */
	private static final int SPACING = 4;
	private static final int TRACK = 48;
	private static final int PILL_WIDTH = 16;
	private static final int PILL_HEIGHT = 8;
	private static final int SWATCH_WIDTH = 12;
	private static final int SWATCH_HEIGHT = 8;
	private static final int SWATCH_CHECKER = 4;
	private static final int CHIP_HEIGHT = 10;
	/** After a choice's value, to say a click opens a list. */
	private static final String ARROW = " ▾";

	private Widgets() {
	}

	/** What a click on a row asks the screen to do next. */
	sealed interface Hit {
		/** The value changed; nothing more to do. */
		record Done() implements Hit {
		}

		/** An action ran; its button should flash. */
		record Ran(Action action) implements Hit {
		}

		/** A slider was pressed; keep it captured until the button is released. */
		record Drag(Setting<?> setting, int trackX, int trackWidth) implements Hit {
		}

		/** A colour swatch was pressed; open the popover under {@code swatch}. */
		record Pick(Setting<Swatch> setting, Chrome.Rect swatch) implements Hit {
		}

		/** A choice's chip was pressed; open its dropdown under {@code chip}. */
		record Choose(Setting<?> setting, Control.Choice<?> choice, Chrome.Rect chip) implements Hit {
		}
	}

	// ------------------------------------------------------------------ drawing

	static void draw(GuiGraphicsExtractor graphics, Font font, Entry entry, int x, int y, int width,
			boolean hovered, boolean flashing) {
		boolean live = entry.available();
		if (hovered && live) {
			Chrome.fill(graphics, x, y, width, row(), Role.HOVER);
		}

		int controlLeft = switch (entry) {
			case Action action -> drawAction(graphics, font, action, x, y, width, hovered && live, flashing);
			case Setting<?> setting -> drawSetting(graphics, font, setting, x, y, width, live);
		};

		Role labelRole = !live ? Role.TEXT_FAINT : entry instanceof Action ? Role.TEXT_DIM : Role.TEXT;
		int labelX = x + PAD;
		Text.draw(graphics, font, Text.fit(font, entry.label(), controlLeft - SPACING - labelX), labelX,
				textY(font, y), Theme.of(labelRole));
	}

	/** @return the control's left edge, so the label can be fitted to what is left */
	@SuppressWarnings("unchecked")
	private static int drawSetting(GuiGraphicsExtractor graphics, Font font, Setting<?> setting,
			int x, int y, int width, boolean live) {
		int right = x + width - PAD;
		Role text = live ? Role.TEXT : Role.TEXT_FAINT;
		return switch (setting.control()) {
			case Control.Flag ignored -> {
				boolean on = ((Setting<Boolean>) setting).value();
				int pillX = right - PILL_WIDTH;
				int pillY = y + (row() - PILL_HEIGHT) / 2;
				Chrome.fill(graphics, pillX, pillY, PILL_WIDTH, PILL_HEIGHT, on ? Role.ON : Role.OFF);
				int knobX = on ? pillX + PILL_WIDTH - 7 : pillX + 1;
				Chrome.fill(graphics, knobX, pillY + 1, 6, PILL_HEIGHT - 2, Role.PANEL);
				yield pillX;
			}
			case Control.Whole control -> {
				int value = ((Setting<Integer>) setting).value();
				yield drawSlider(graphics, font, right, y, fraction(value, control.min(), control.max()),
						written(value, control), text);
			}
			case Control.Real control -> {
				double value = ((Setting<Double>) setting).value();
				yield drawSlider(graphics, font, right, y, fraction(value, control.min(), control.max()),
						written(value, control.format()), text);
			}
			case Control.Choice<?> control -> {
				Chrome.Rect chip = chip(font, setting, control, x, y, width);
				Chrome.box(graphics, chip.x(), chip.y(), chip.width(), chip.height(), Role.CARD_HEADER, Role.BORDER);
				Text.draw(graphics, font, written(setting, control) + ARROW, chip.x() + 3, textY(font, y),
						Theme.of(text));
				yield chip.x();
			}
			case Control.Colour control -> {
				Chrome.Rect swatch = swatch(x, y, width);
				Swatch value = ((Setting<Swatch>) setting).value();
				// Chequers first, so a see-through colour reads as see-through on the row too and
				// not as a darker shade of itself against whatever card it happens to sit on.
				checkers(graphics, swatch);
				if (control.fill()) {
					// A small box: the fill inside, the outline round it, each at its own opacity.
					graphics.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), Theme.resolveFill(value));
					graphics.outline(swatch.x(), swatch.y(), swatch.width(), swatch.height(), Theme.resolve(value));
				} else {
					graphics.fill(swatch.x(), swatch.y(), swatch.right(), swatch.bottom(), Theme.resolve(value));
				}
				Chrome.edge(graphics, swatch.x() - 1, swatch.y() - 1, swatch.width() + 2, swatch.height() + 2,
						Role.BORDER);
				yield swatch.x() - 1;
			}
		};
	}

	/** The two greys behind a see-through swatch, matching the picker's own chequerboard. */
	private static void checkers(GuiGraphicsExtractor graphics, Chrome.Rect rect) {
		for (int column = 0; column * SWATCH_CHECKER < rect.width(); column++) {
			int left = rect.x() + column * SWATCH_CHECKER;
			int right = Math.min(left + SWATCH_CHECKER, rect.right());
			int middle = Math.min(rect.y() + SWATCH_CHECKER, rect.bottom());
			graphics.fill(left, rect.y(), right, middle, column % 2 == 0 ? 0xFFBBBBBB : 0xFF777777);
			graphics.fill(left, middle, right, rect.bottom(), column % 2 == 0 ? 0xFF777777 : 0xFFBBBBBB);
		}
	}

	/** Value text, then a track with the filled part in the accent and a knob at the value. */
	private static int drawSlider(GuiGraphicsExtractor graphics, Font font, int right, int y,
			double fraction, String value, Role text) {
		int trackX = right - TRACK;
		int trackY = y + row() / 2 - 2;
		int filled = (int) Math.round(fraction * TRACK);
		Chrome.fill(graphics, trackX, trackY, TRACK, 4, Role.ACCENT_DIM);
		Chrome.fill(graphics, trackX, trackY, filled, 4, Role.ACCENT);
		int knobX = Math.clamp(trackX + filled - 1, trackX, trackX + TRACK - 2);
		Chrome.fill(graphics, knobX, y + 3, 2, row() - 6, Role.TEXT);

		int valueRight = trackX - SPACING;
		Text.right(graphics, font, value, valueRight, textY(font, y), Theme.of(text));
		return valueRight - font.width(value);
	}

	private static int drawAction(GuiGraphicsExtractor graphics, Font font, Action action, int x, int y,
			int width, boolean hovered, boolean flashing) {
		Chrome.Rect button = button(font, action, x, y, width);
		Chrome.button(graphics, font, verb(action), button.x(), button.y(), button.width(), button.height(),
				hovered, flashing, Role.CARD_HEADER, action.available() ? Role.TEXT : Role.TEXT_FAINT);
		return button.x();
	}

	// ------------------------------------------------------------------ input

	/**
	 * Applies a click to a row whose entry is available.
	 *
	 * @param button 0 for left, 1 for right
	 * @param fine   whether Shift is held
	 * @return what the screen should do next, or null when the click landed on nothing
	 */
	@SuppressWarnings("unchecked")
	static Hit click(Font font, Entry entry, int x, int y, int width, double mouseX, double mouseY,
			int button, boolean fine) {
		if (entry instanceof Action action) {
			if (button == 0 && button(font, action, x, y, width).contains(mouseX, mouseY)) {
				action.run().run();
				return new Hit.Ran(action);
			}
			return null;
		}

		Setting<?> setting = (Setting<?>) entry;
		return switch (setting.control()) {
			case Control.Flag ignored -> {
				if (button != 0) {
					yield null;
				}
				Setting<Boolean> flag = (Setting<Boolean>) setting;
				flag.value(!flag.value());
				yield new Hit.Done();
			}
			case Control.Whole ignored -> slide(setting, x, y, width, mouseX, mouseY, button, fine);
			case Control.Real ignored -> slide(setting, x, y, width, mouseX, mouseY, button, fine);
			case Control.Choice<?> choice -> {
				if (!chip(font, setting, choice, x, y, width).contains(mouseX, mouseY)) {
					yield null;
				}
				if (button == 0) {
					yield new Hit.Choose(setting, choice, chip(font, setting, choice, x, y, width));
				}
				if (button != 1) {
					yield null;
				}
				// Right-click still steps to the next option, for a quick flip between two.
				cycle(setting, choice, 1);
				yield new Hit.Done();
			}
			case Control.Colour ignored -> button == 0
					? new Hit.Pick((Setting<Swatch>) setting, swatch(x, y, width))
					: null;
		};
	}

	private static Hit slide(Setting<?> setting, int x, int y, int width, double mouseX, double mouseY,
			int button, boolean fine) {
		int trackX = x + width - PAD - TRACK;
		if (button != 0 || !onTrack(trackX, y, mouseX, mouseY)) {
			return null;
		}
		scrub(setting, trackX, TRACK, mouseX, fine);
		return new Hit.Drag(setting, trackX, TRACK);
	}

	/**
	 * Sets a slider from where the cursor is along its track. Snaps to the control's step, or with
	 * {@code fine} to one (whole) or a tenth of the step (real).
	 */
	@SuppressWarnings("unchecked")
	static void scrub(Setting<?> setting, int trackX, int trackWidth, double mouseX, boolean fine) {
		double fraction = Math.clamp((mouseX - trackX) / trackWidth, 0.0, 1.0);
		switch (setting.control()) {
			case Control.Whole control -> {
				int step = fine ? 1 : Math.max(1, control.step());
				long steps = Math.round(fraction * (control.max() - control.min()) / step);
				((Setting<Integer>) setting).value(Math.clamp(control.min() + steps * step,
						control.min(), control.max()));
			}
			case Control.Real control -> {
				double step = fine ? control.step() / 10 : control.step();
				double value = control.min() + Math.round(fraction * (control.max() - control.min()) / step) * step;
				((Setting<Double>) setting).value(tidy(Math.clamp(value, control.min(), control.max())));
			}
			default -> {
			}
		}
	}

	/**
	 * Steps a slider under the cursor by one notch of the wheel.
	 *
	 * @return false when the cursor is not over a slider's track, so the wheel should scroll instead
	 */
	@SuppressWarnings("unchecked")
	static boolean wheel(Entry entry, int x, int y, int width, double mouseX, double mouseY,
			double amount, boolean fine) {
		if (!(entry instanceof Setting<?> setting) || !entry.available()
				|| !onTrack(x + width - PAD - TRACK, y, mouseX, mouseY) || amount == 0) {
			return false;
		}
		int direction = amount > 0 ? 1 : -1;
		switch (setting.control()) {
			case Control.Whole control -> {
				Setting<Integer> whole = (Setting<Integer>) setting;
				int step = fine ? 1 : Math.max(1, control.step());
				whole.value(Math.clamp((long) whole.value() + direction * step, control.min(), control.max()));
				return true;
			}
			case Control.Real control -> {
				Setting<Double> real = (Setting<Double>) setting;
				double step = fine ? control.step() / 10 : control.step();
				real.value(tidy(Math.clamp(real.value() + direction * step, control.min(), control.max())));
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	@SuppressWarnings("unchecked")
	private static <E extends Enum<E>> void cycle(Setting<?> setting, Control.Choice<E> choice, int direction) {
		Setting<E> typed = (Setting<E>) setting;
		E[] constants = choice.type().getEnumConstants();
		typed.value(constants[Math.floorMod(typed.value().ordinal() + direction, constants.length)]);
	}

	// ------------------------------------------------------------------ geometry and text

	/** A slider's hit area: the track, two pixels either side, the full height of the row. */
	private static boolean onTrack(int trackX, int y, double mouseX, double mouseY) {
		return Chrome.inside(mouseX, mouseY, trackX - 2, y, TRACK + 4, row());
	}

	/** A choice's chip: its value and the dropdown arrow, right-aligned in the row. */
	private static Chrome.Rect chip(Font font, Setting<?> setting, Control.Choice<?> choice, int x, int y,
			int width) {
		int chipWidth = font.width(written(setting, choice) + ARROW) + 6;
		return new Chrome.Rect(x + width - PAD - chipWidth, y + (row() - CHIP_HEIGHT) / 2, chipWidth, CHIP_HEIGHT);
	}

	private static Chrome.Rect swatch(int x, int y, int width) {
		return new Chrome.Rect(x + width - PAD - SWATCH_WIDTH, y + (row() - SWATCH_HEIGHT) / 2,
				SWATCH_WIDTH, SWATCH_HEIGHT);
	}

	private static Chrome.Rect button(Font font, Action action, int x, int y, int width) {
		int buttonWidth = font.width(verb(action)) + 8;
		return new Chrome.Rect(x + width - PAD - buttonWidth, y + (row() - CHIP_HEIGHT) / 2,
				buttonWidth, CHIP_HEIGHT);
	}

	/** An action's button shows the first word of its label: "Reset water" draws "Reset". */
	private static String verb(Action action) {
		String label = action.label().strip();
		int space = label.indexOf(' ');
		return space < 0 ? label : label.substring(0, space);
	}

	private static int textY(Font font, int y) {
		return y + (row() - font.lineHeight) / 2 + 1;
	}

	private static double fraction(double value, double min, double max) {
		return max <= min ? 0 : Math.clamp((value - min) / (max - min), 0.0, 1.0);
	}

	/** Drops the floating-point noise that step arithmetic leaves, so 0.3 is saved as 0.3. */
	private static double tidy(double value) {
		return Math.round(value * 1e6) / 1e6;
	}

	private static String written(int value, Control.Whole control) {
		if (value == 0 && !control.zeroLabel().isEmpty()) {
			return control.zeroLabel();
		}
		return control.unit().isEmpty() ? Integer.toString(value) : value + " " + control.unit();
	}

	private static String written(double value, Control.Format format) {
		return switch (format) {
			case PLAIN -> String.format(Locale.ROOT, "%.2f", value);
			case BLOCKS -> String.format(Locale.ROOT, "%.2f blocks", value);
			case SECONDS -> String.format(Locale.ROOT, "%.1f s", value);
			case PERCENT -> Math.round(value * 100.0) + "%";
		};
	}

	@SuppressWarnings("unchecked")
	private static <E extends Enum<E>> String written(Setting<?> setting, Control.Choice<E> choice) {
		return choice.label().apply(((Setting<E>) setting).value());
	}
}
