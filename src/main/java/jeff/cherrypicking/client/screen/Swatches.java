package jeff.cherrypicking.client.screen;

import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;

import jeff.cherrypicking.client.config.Control;
import jeff.cherrypicking.client.config.Setting;
import jeff.cherrypicking.client.theme.Palette;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The colour popover: the live flavour's 26 colours, a shade square and hue bar for any other
 * colour, an opacity slider (two for a box colour: outline and fill), and a hex field.
 *
 * <pre>
 * ┌ First blaze ───────────────┐
 * │ ●●●●●●●  the 14 accents    │
 * │ ●●●●●●●                    │
 * │ ▁▁▁▁▁▁▁▁▁▁▁▁  the 12       │
 * │ ┌──────────────────┐ ┌┐    │
 * │ │ shade: saturation│ ││hue │
 * │ │ across, bright up│ ││    │
 * │ └──────────────────┘ └┘    │
 * │ ▓▓▓▓▓▒▒ Outline 70% ░░░░░  │  opacity
 * │ ▓▓▒▒░░░ Fill 35% ░░░░░░░░  │  a box colour's fill only
 * │ hex     [ #40a02b       ]  │
 * └────────────────────────────┘
 * </pre>
 *
 * <p>Picking from the grid commits a {@link Swatch.Named}, which follows the flavour. Typing a
 * valid 6- or 8-digit hex, or dragging in the shade square or the hue bar, commits a
 * {@link Swatch.Literal}, which does not. Every commit is saved at once, so a player who quits
 * straight after picking keeps the colour.
 *
 * <p><b>Opacity is the one control that never changes which kind a colour is.</b> It rides on the
 * colour itself ({@link Swatch#withAlpha}), so a palette colour dragged down to 40% is still that
 * palette colour and still re-tints with the flavour. That is why no feature needs an opacity
 * setting of its own next to its colours. A box colour's fill opacity rides on it the same way
 * ({@link Swatch#withFill}), so each box decides how solid its inside is (check S4-09).
 *
 * <p>The palette grid comes first, so the palette stays the easy path; the shade square and the
 * hue bar reach every other colour without typing hex. The hue is held here rather than read back
 * from the colour, so dragging to grey or black and back does not lose it.
 */
final class Swatches implements Popover {
	static final int WIDTH = 150;

	private static final int PAD = 6;
	private static final int TITLE = 12;
	private static final int CELL = 14;
	private static final int GAP = 2;
	private static final int NEUTRAL_WIDTH = 10;
	private static final int FIELD_HEIGHT = 12;
	/** Where the hex field starts, after its label. */
	private static final int LABEL_WIDTH = 34;

	private static final int ACCENTS_TOP = TITLE + 4;
	private static final int NEUTRALS_TOP = ACCENTS_TOP + 2 * (CELL + GAP) + 2;
	private static final int PICKER_TOP = NEUTRALS_TOP + CELL + 6;
	private static final int PICKER_HEIGHT = 56;
	private static final int HUE_WIDTH = 10;
	private static final int ALPHA_TOP = PICKER_TOP + PICKER_HEIGHT + 6;
	/** Thick enough to hold the percentage, to read the colour through, and to grab without aiming. */
	private static final int ALPHA_HEIGHT = 11;
	private static final int ALPHA_CHECKER = 4;
	/** The gap between the outline bar and the fill bar. */
	private static final int ALPHA_GAP = 4;

	/** The two greys of the chequerboard drawn behind a see-through colour. */
	private static final int CHECKER_LIGHT = 0xFFBBBBBB;
	private static final int CHECKER_DARK = 0xFF777777;

	/** What a held mouse button is dragging. */
	private enum Knob {
		NONE,
		SHADE,
		HUE,
		ALPHA,
		FILL
	}

	private final Setting<Swatch> setting;
	private final Runnable save;
	private final EditBox hex;
	/** True for a box colour, which gets the fill bar under the outline bar. */
	private final boolean fill;

	private final int x;
	private final int y;
	private final int height;

	private Knob dragging = Knob.NONE;
	/** The picker's position, each in {@code [0,1]}. */
	private float hue;
	private float saturation;
	private float brightness;
	/** True while this class sets the hex field's text, so the field's responder ignores it. */
	private boolean syncing;

	/**
	 * @param anchor the row's swatch; the popover opens under it, or above it if there is no room
	 * @param bounds the panel; the popover is kept inside it
	 * @param save   writes the config file
	 */
	Swatches(Font font, Setting<Swatch> setting, Chrome.Rect anchor, Chrome.Rect bounds, Runnable save) {
		this.setting = setting;
		this.save = save;
		this.fill = setting.control() instanceof Control.Colour colour && colour.fill();
		this.height = hexTop() + FIELD_HEIGHT + PAD;

		this.x = Math.clamp(anchor.right() - WIDTH, bounds.x(), Math.max(bounds.x(), bounds.right() - WIDTH));
		int below = anchor.bottom() + 2;
		int top = below + height <= bounds.bottom() ? below : anchor.y() - 2 - height;
		this.y = Math.clamp(top, bounds.y(), Math.max(bounds.y(), bounds.bottom() - height));

		Chrome.Rect field = fieldRect();
		hex = new EditBox(font, field.x() + 3, field.y() + 2, field.width() - 6, FIELD_HEIGHT - 2,
				Component.literal("Hex colour"));
		hex.setBordered(false);
		hex.setMaxLength(9);
		hex.setTextShadow(false);
		sync();
		readShade();
		hex.setResponder(this::typed);
	}

	/** The hex field. The screen adds it as a widget while the popover is open, so it gets keys. */
	@Override
	public EditBox field() {
		return hex;
	}

	boolean contains(double mouseX, double mouseY) {
		return Chrome.inside(mouseX, mouseY, x, y, WIDTH, height);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		Chrome.box(graphics, x, y, WIDTH, height, Role.PANEL, Role.BORDER_STRONG);
		Chrome.fill(graphics, x + 1, y + 1, WIDTH - 2, TITLE - 1, Role.HEADER);
		Chrome.horizontal(graphics, x + 1, x + WIDTH - 1, y + TITLE, Role.BORDER);
		Text.draw(graphics, font, Text.fit(font, setting.label(), WIDTH - 2 * PAD), x + PAD,
				y + (TITLE - font.lineHeight) / 2 + 2, Theme.of(Role.TEXT));

		Palette palette = Theme.palette();
		Swatch current = setting.value();
		List<String> accents = palette.accents();
		for (int i = 0; i < accents.size(); i++) {
			cell(graphics, palette, accents.get(i), accentCell(i), current, mouseX, mouseY);
		}
		List<String> neutrals = palette.neutrals();
		for (int i = 0; i < neutrals.size(); i++) {
			cell(graphics, palette, neutrals.get(i), neutralCell(i), current, mouseX, mouseY);
		}

		drawPicker(graphics);
		Swatch value = setting.value();
		drawAlpha(graphics, font, alphaTrack(), value.alpha(), fill ? "Outline " : "");
		if (fill) {
			drawAlpha(graphics, font, fillTrack(), value.fill(), "Fill ");
		}

		int rowY = y + hexTop();
		Text.draw(graphics, font, "hex", x + PAD, rowY + 2, Theme.of(Role.TEXT_DIM));
		Chrome.Rect field = fieldRect();
		Chrome.box(graphics, field.x(), field.y(), field.width(), field.height(), Role.CARD,
				hex.isFocused() ? Role.ACCENT : Role.BORDER);
		hex.setTextColor(Theme.of(Role.TEXT));
		hex.extractRenderState(graphics, mouseX, mouseY, 0);
	}

	/** The shade square as one vertical gradient per column, then the hue bar, then both markers. */
	private void drawPicker(GuiGraphicsExtractor graphics) {
		Chrome.Rect shade = shadeRect();
		for (int column = 0; column < shade.width(); column++) {
			float across = column / (float) Math.max(1, shade.width() - 1);
			graphics.fillGradient(shade.x() + column, shade.y(), shade.x() + column + 1, shade.bottom(),
					Mth.hsvToArgb(hue, across, 1.0f, 255), 0xFF000000);
		}
		Chrome.Rect bar = hueRect();
		for (int step = 0; step < 6; step++) {
			int top = bar.y() + bar.height() * step / 6;
			int bottom = bar.y() + bar.height() * (step + 1) / 6;
			graphics.fillGradient(bar.x(), top, bar.right(), bottom, Mth.hsvToArgb(step / 6.0f, 1.0f, 1.0f, 255),
					Mth.hsvToArgb(((step + 1) % 6) / 6.0f, 1.0f, 1.0f, 255));
		}
		Chrome.edge(graphics, shade.x() - 1, shade.y() - 1, shade.width() + 2, shade.height() + 2, Role.BORDER);
		Chrome.edge(graphics, bar.x() - 1, bar.y() - 1, bar.width() + 2, bar.height() + 2, Role.BORDER);

		int markX = shade.x() + Math.round(saturation * (shade.width() - 1));
		int markY = shade.y() + Math.round((1.0f - brightness) * (shade.height() - 1));
		// Fixed black and white, not roles: the marker sits on the colour, not on the panel.
		ring(graphics, markX - 2, markY - 2, 5, 5, brightness > 0.5f ? 0xFF000000 : 0xFFFFFFFF);
		int hueY = bar.y() + Math.round(hue * (bar.height() - 1));
		ring(graphics, bar.x() - 1, hueY - 1, bar.width() + 2, 3, 0xFFFFFFFF);
	}

	/**
	 * The opacity slider: a chequerboard, the colour laid over it from clear to solid, a knob at
	 * the current opacity, and the percentage inside the bar. The track shows the answer rather
	 * than a number, so "how see-through is 40%" is read off the bar instead of guessed and then
	 * tried in the world.
	 *
	 * <p><b>No label beside it.</b> A word in the margin cut the bar short and read as a bite out
	 * of the chequerboard; the chequers say "opacity" on their own, so the bar takes the whole
	 * content width and lines up with the shade square above it - see check S4-07. A box colour
	 * has two bars, so each names itself inside the bar, next to its percentage.
	 *
	 * @param name "Outline ", "Fill ", or empty for a colour with one bar
	 */
	private void drawAlpha(GuiGraphicsExtractor graphics, Font font, Chrome.Rect track, int alpha,
			String name) {
		checkerboard(graphics, track);
		// One fill per column: fillGradient only runs top to bottom, and this ramp runs across.
		int rgb = Theme.resolve(setting.value()) & 0x00FFFFFF;
		for (int column = 0; column < track.width(); column++) {
			int over = Math.round(column / (float) Math.max(1, track.width() - 1) * 255);
			graphics.fill(track.x() + column, track.y(), track.x() + column + 1, track.bottom(),
					over << 24 | rgb);
		}
		Chrome.edge(graphics, track.x() - 1, track.y() - 1, track.width() + 2, track.height() + 2, Role.BORDER);

		int knobX = track.x() + Math.round(alpha / 255.0f * (track.width() - 1));
		// White with a black core, so the knob reads over both ends of the track.
		graphics.fill(knobX - 2, track.y() - 2, knobX + 3, track.bottom() + 2, 0xFFFFFFFF);
		graphics.fill(knobX - 1, track.y() - 1, knobX + 2, track.bottom() + 1, 0xFF000000);

		// The one shadowed text in the screen. It sits on the player's own colour rather than on a
		// panel, so no theme role can be trusted to stay readable behind it; white on a shadow can.
		String percent = name + Math.round(alpha / 255.0f * 100) + "%";
		graphics.text(font, percent, track.x() + (track.width() - font.width(percent)) / 2,
				track.y() + (ALPHA_HEIGHT - font.lineHeight) / 2 + 1, 0xFFFFFFFF, true);
	}

	private static void checkerboard(GuiGraphicsExtractor graphics, Chrome.Rect rect) {
		for (int row = 0; row * ALPHA_CHECKER < rect.height(); row++) {
			int top = rect.y() + row * ALPHA_CHECKER;
			int bottom = Math.min(top + ALPHA_CHECKER, rect.bottom());
			for (int column = 0; column * ALPHA_CHECKER < rect.width(); column++) {
				int left = rect.x() + column * ALPHA_CHECKER;
				int right = Math.min(left + ALPHA_CHECKER, rect.right());
				graphics.fill(left, top, right, bottom,
						(row + column) % 2 == 0 ? CHECKER_LIGHT : CHECKER_DARK);
			}
		}
	}

	private static void ring(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int argb) {
		graphics.fill(x, y, x + width, y + 1, argb);
		graphics.fill(x, y + height - 1, x + width, y + height, argb);
		graphics.fill(x, y + 1, x + 1, y + height - 1, argb);
		graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, argb);
	}

	private void cell(GuiGraphicsExtractor graphics, Palette palette, String name, Chrome.Rect cell,
			Swatch current, int mouseX, int mouseY) {
		graphics.fill(cell.x(), cell.y(), cell.right(), cell.bottom(), palette.of(name));
		if (current instanceof Swatch.Named named && named.name().equals(name)) {
			Chrome.focusRing(graphics, cell.x(), cell.y(), cell.width(), cell.height());
		} else if (cell.contains(mouseX, mouseY)) {
			Chrome.edge(graphics, cell.x(), cell.y(), cell.width(), cell.height(), Role.TEXT);
		}
	}

	@Override
	public boolean click(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (!contains(mouseX, mouseY)) {
			return false;
		}
		if (event.button() != 0) {
			return true;
		}

		Palette palette = Theme.palette();
		for (int i = 0; i < palette.accents().size(); i++) {
			if (accentCell(i).contains(mouseX, mouseY)) {
				commit(Swatch.of(palette.accents().get(i), setting.value().alpha()).withFill(setting.value().fill()));
				return true;
			}
		}
		for (int i = 0; i < palette.neutrals().size(); i++) {
			if (neutralCell(i).contains(mouseX, mouseY)) {
				commit(Swatch.of(palette.neutrals().get(i), setting.value().alpha()).withFill(setting.value().fill()));
				return true;
			}
		}
		if (shadeRect().contains(mouseX, mouseY)) {
			dragging = Knob.SHADE;
			drag(mouseX, mouseY);
			return true;
		}
		if (hueRect().contains(mouseX, mouseY)) {
			dragging = Knob.HUE;
			drag(mouseX, mouseY);
			return true;
		}
		if (alphaGrab().contains(mouseX, mouseY)) {
			dragging = Knob.ALPHA;
			drag(mouseX, mouseY);
			return true;
		}
		if (fill && grab(fillTrack()).contains(mouseX, mouseY)) {
			dragging = Knob.FILL;
			drag(mouseX, mouseY);
			return true;
		}
		if (fieldRect().contains(mouseX, mouseY)) {
			hex.mouseClicked(event, doubleClick);
		}
		return true;
	}

	@Override
	public void drag(double mouseX, double mouseY) {
		switch (dragging) {
			case NONE -> {
			}
			case SHADE -> {
				Chrome.Rect shade = shadeRect();
				saturation = (float) Math.clamp((mouseX - shade.x()) / (shade.width() - 1), 0.0, 1.0);
				brightness = 1.0f - (float) Math.clamp((mouseY - shade.y()) / (shade.height() - 1), 0.0, 1.0);
				commitShade();
			}
			case HUE -> {
				Chrome.Rect bar = hueRect();
				hue = (float) Math.clamp((mouseY - bar.y()) / (bar.height() - 1), 0.0, 1.0);
				commitShade();
			}
			case ALPHA -> slideAlpha(mouseX);
			case FILL -> slideFill(mouseX);
		}
	}

	/** Ends a drag; the value is saved once here rather than on every frame of the drag. */
	@Override
	public void release() {
		if (dragging != Knob.NONE) {
			dragging = Knob.NONE;
			save.run();
		}
	}

	/** Puts the picker's colour into force, keeping the current opacity. Saved on release. */
	private void commitShade() {
		Swatch kept = setting.value();
		setting.value(Swatch.of(Mth.hsvToArgb(Math.min(hue, 0.9999f), saturation, brightness, kept.alpha()))
				.withFill(kept.fill()));
		sync();
	}

	/** Moves the picker to the current colour. The hue is kept when the colour has none. */
	private void readShade() {
		int argb = Theme.resolve(setting.value());
		float r = ((argb >> 16) & 0xFF) / 255.0f;
		float g = ((argb >> 8) & 0xFF) / 255.0f;
		float b = (argb & 0xFF) / 255.0f;
		float max = Math.max(r, Math.max(g, b));
		float range = max - Math.min(r, Math.min(g, b));
		brightness = max;
		saturation = max == 0.0f ? 0.0f : range / max;
		if (range > 0.0f) {
			float sixths = max == r ? (g - b) / range : max == g ? 2.0f + (b - r) / range : 4.0f + (r - g) / range;
			hue = ((sixths / 6.0f) % 1.0f + 1.0f) % 1.0f;
		}
	}

	/** Opacity alone, so a palette colour dragged see-through stays a palette colour. */
	private void slideAlpha(double mouseX) {
		Chrome.Rect track = alphaTrack();
		double fraction = Math.clamp((mouseX - track.x()) / (track.width() - 1), 0.0, 1.0);
		setting.value(setting.value().withAlpha((int) Math.round(fraction * 255)));
		sync();
	}

	/** The fill alone, on its own bar; the outline and the colour stay where they are. */
	private void slideFill(double mouseX) {
		Chrome.Rect track = fillTrack();
		double fraction = Math.clamp((mouseX - track.x()) / (track.width() - 1), 0.0, 1.0);
		setting.value(setting.value().withFill((int) Math.round(fraction * 255)));
	}

	/** A pick keeps the fill that was set, the way it keeps the opacity. */
	private void commit(Swatch swatch) {
		setting.value(swatch);
		sync();
		readShade();
		save.run();
	}

	/** The hex field's responder: commits as soon as what is typed is a whole colour. */
	private void typed(String text) {
		if (syncing) {
			return;
		}
		OptionalInt argb = Swatch.hex(text);
		if (argb.isPresent()) {
			setting.value(Swatch.of(argb.getAsInt()).withFill(setting.value().fill()));
			readShade();
			save.run();
		}
	}

	/** Shows the current colour in the hex field; six digits when it is opaque. */
	private void sync() {
		int argb = Theme.resolve(setting.value());
		syncing = true;
		hex.setValue(argb >>> 24 == 0xFF
				? String.format(Locale.ROOT, "#%06x", argb & 0x00FFFFFF)
				: String.format(Locale.ROOT, "#%08x", argb));
		syncing = false;
	}

	// ------------------------------------------------------------------ geometry

	private Chrome.Rect accentCell(int index) {
		int left = x + (WIDTH - (7 * CELL + 6 * GAP)) / 2;
		return new Chrome.Rect(left + (index % 7) * (CELL + GAP), y + ACCENTS_TOP + (index / 7) * (CELL + GAP),
				CELL, CELL);
	}

	private Chrome.Rect neutralCell(int index) {
		int left = x + (WIDTH - 12 * NEUTRAL_WIDTH) / 2;
		return new Chrome.Rect(left + index * NEUTRAL_WIDTH, y + NEUTRALS_TOP, NEUTRAL_WIDTH, CELL);
	}

	private Chrome.Rect shadeRect() {
		return new Chrome.Rect(x + PAD, y + PICKER_TOP, WIDTH - 2 * PAD - HUE_WIDTH - 4, PICKER_HEIGHT);
	}

	private Chrome.Rect hueRect() {
		return new Chrome.Rect(x + WIDTH - PAD - HUE_WIDTH, y + PICKER_TOP, HUE_WIDTH, PICKER_HEIGHT);
	}

	/** The full content width, so the bar's ends line up with the shade square and the hue bar. */
	private Chrome.Rect alphaTrack() {
		return new Chrome.Rect(x + PAD, y + ALPHA_TOP, WIDTH - 2 * PAD, ALPHA_HEIGHT);
	}

	/** The fill bar, under the outline bar and the same size. Only a box colour has one. */
	private Chrome.Rect fillTrack() {
		return new Chrome.Rect(x + PAD, y + ALPHA_TOP + ALPHA_HEIGHT + ALPHA_GAP, WIDTH - 2 * PAD, ALPHA_HEIGHT);
	}

	private Chrome.Rect alphaGrab() {
		return grab(alphaTrack());
	}

	/**
	 * A track, grown by the knob's overhang, so the ends are as easy to hit as the middle. The
	 * outline and fill bars sit closer than two overhangs, so their grabs share the gap; the
	 * outline bar is tested first and wins it.
	 */
	private static Chrome.Rect grab(Chrome.Rect track) {
		return new Chrome.Rect(track.x() - 3, track.y() - 3, track.width() + 6, track.height() + 6);
	}

	private int hexTop() {
		int bars = fill ? 2 * ALPHA_HEIGHT + ALPHA_GAP : ALPHA_HEIGHT;
		return ALPHA_TOP + bars + 5;
	}

	private Chrome.Rect fieldRect() {
		int left = x + PAD + LABEL_WIDTH;
		return new Chrome.Rect(left, y + hexTop(), x + WIDTH - PAD - left, FIELD_HEIGHT);
	}
}
