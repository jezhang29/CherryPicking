package jeff.cherrypicking.client.screen;

import com.mojang.blaze3d.platform.InputConstants;

import jeff.cherrypicking.client.config.Control;
import jeff.cherrypicking.client.config.Setting;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The popover for a {@link Control.Text} setting: the setting's name and one text field.
 *
 * <pre>
 * ┌─ Relay address ──────────────┐
 * │ [ https://cherry-relay...  ] │
 * │ Enter keeps it. Esc cancels. │
 * └──────────────────────────────┘
 * </pre>
 *
 * <p>The value changes only when the edit is kept, with Enter or a click outside. A half-typed
 * address or name list never goes into force. Escape closes the popover in the screen, so it drops
 * the edit.
 */
final class Typing implements Popover {
	private static final int WIDTH = 190;
	private static final int PAD = 5;
	private static final int TITLE = 14;
	private static final int FIELD_HEIGHT = 12;
	private static final String HINT = "Enter keeps it. Esc cancels.";

	private final Setting<String> setting;
	private final Runnable save;
	private final EditBox field;

	private final int x;
	private final int y;
	private final int width;
	private final int height;

	/**
	 * @param anchor the row's chip; the popover opens under it, or above it if there is no room
	 * @param bounds the panel; the popover is kept inside it
	 * @param save   writes the config file
	 */
	Typing(Font font, Setting<String> setting, Control.Text text, Chrome.Rect anchor, Chrome.Rect bounds,
			Runnable save) {
		this.setting = setting;
		this.save = save;
		this.height = TITLE + PAD + FIELD_HEIGHT + 3 + font.lineHeight + PAD;

		this.width = Math.min(WIDTH, bounds.width());
		this.x = Math.clamp(anchor.right() - width, bounds.x(), Math.max(bounds.x(), bounds.right() - width));
		int below = anchor.bottom() + 2;
		int top = below + height <= bounds.bottom() ? below : anchor.y() - 2 - height;
		this.y = Math.clamp(top, bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));

		Chrome.Rect box = fieldRect();
		field = new EditBox(font, box.x() + 3, box.y() + 2, box.width() - 6, FIELD_HEIGHT - 2,
				Component.literal(setting.label()));
		field.setBordered(false);
		field.setMaxLength(text.maxLength());
		field.setTextShadow(false);
		field.setValue(setting.value());
	}

	/** The screen adds the field as a widget while the popover is open, so it gets keys. */
	@Override
	public EditBox field() {
		return field;
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Chrome.box(graphics, x, y, width, height, Role.PANEL, Role.BORDER_STRONG);
		Chrome.fill(graphics, x + 1, y + 1, width - 2, TITLE - 1, Role.HEADER);
		Chrome.horizontal(graphics, x + 1, x + width - 1, y + TITLE, Role.BORDER);
		Text.draw(graphics, font, Text.fit(font, setting.label(), width - 2 * PAD), x + PAD,
				y + (TITLE - font.lineHeight) / 2 + 2, Theme.of(Role.TEXT));

		Chrome.Rect box = fieldRect();
		Chrome.box(graphics, box.x(), box.y(), box.width(), box.height(), Role.CARD,
				field.isFocused() ? Role.ACCENT : Role.BORDER);
		field.setTextColor(Theme.of(Role.TEXT));
		field.extractRenderState(graphics, mouseX, mouseY, 0);

		Text.draw(graphics, font, Text.fit(font, HINT, width - 2 * PAD), x + PAD, box.bottom() + 3,
				Theme.of(Role.TEXT_FAINT));
	}

	/** A click outside keeps the edit and closes the popover. */
	@Override
	public boolean click(MouseButtonEvent event, boolean doubleClick) {
		if (!Chrome.inside(event.x(), event.y(), x, y, width, height)) {
			keep();
			return false;
		}
		if (fieldRect().contains(event.x(), event.y())) {
			field.mouseClicked(event, doubleClick);
		}
		return true;
	}

	/** Enter keeps the edit; the screen then closes the popover. */
	@Override
	public boolean key(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			keep();
		}
		return false;
	}

	private void keep() {
		String typed = field.getValue().strip();
		if (!typed.equals(setting.value())) {
			setting.value(typed);
			save.run();
		}
	}

	private Chrome.Rect fieldRect() {
		return new Chrome.Rect(x + PAD, y + TITLE + PAD, width - 2 * PAD, FIELD_HEIGHT);
	}
}
