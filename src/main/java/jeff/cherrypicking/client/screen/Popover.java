package jeff.cherrypicking.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * What floats over the screen while one row is being edited: the colour picker or a choice's
 * dropdown. The screen holds at most one, sends it every click, drag, wheel and key first, and
 * draws it last.
 */
sealed interface Popover permits Swatches, Dropdown {
	void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY);

	/**
	 * Every click lands here while the popover is open.
	 *
	 * @return false to close the popover: the click was outside, or it finished the edit
	 */
	boolean click(MouseButtonEvent event, boolean doubleClick);

	default void drag(double mouseX, double mouseY) {
	}

	/** Ends a drag. */
	default void release() {
	}

	/** One notch of the wheel. The popover swallows it either way, so the page does not scroll. */
	default void scrolled(double amount) {
	}

	/** @return true when the popover used the key */
	default boolean key(KeyEvent event) {
		return false;
	}

	/** @return a text field that needs keys while the popover is open, or null */
	default EditBox field() {
		return null;
	}
}
