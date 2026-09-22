package jeff.cherrypicking.client.screen;

import jeff.cherrypicking.client.theme.Role;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Scroll state for content whose height is only known once it has been drawn. Copied from
 * skyblock-flipper's {@code gui/Scroller}, not imported: the mods share no code.
 *
 * <p>The body draws itself at {@code -offset()} inside a scissor and reports back what it needed,
 * and the range is clamped from that. It is only ever wrong for the one frame after the content
 * changes, and corrects itself on the next.
 */
final class Scroll {
	private static final int STEP = 14;

	private int offset;
	private int contentHeight;
	private int viewHeight;

	int offset() {
		return offset;
	}

	/** Called after drawing, with what the content actually needed and what there was room for. */
	void measured(int contentHeight, int viewHeight) {
		this.contentHeight = contentHeight;
		this.viewHeight = viewHeight;
		clamp();
	}

	/** @return true when the wheel moved something, so the event should not fall through */
	boolean scroll(double amount) {
		if (!overflows()) {
			return false;
		}
		offset -= (int) Math.signum(amount) * STEP;
		clamp();
		return true;
	}

	void reset() {
		offset = 0;
	}

	boolean overflows() {
		return contentHeight > viewHeight;
	}

	/** A two-pixel bar on the right edge, drawn only when there is something out of sight. */
	void renderBar(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		if (!overflows()) {
			return;
		}
		int thumbHeight = Math.max(8, height * viewHeight / contentHeight);
		int travel = height - thumbHeight;
		int thumbTop = y + travel * offset / Math.max(1, contentHeight - viewHeight);

		Chrome.fill(graphics, x + width - 2, y, 2, height, Role.BORDER);
		Chrome.fill(graphics, x + width - 2, thumbTop, 2, thumbHeight, Role.TEXT_DIM);
	}

	private void clamp() {
		offset = Math.clamp(offset, 0, Math.max(0, contentHeight - viewHeight));
	}
}
