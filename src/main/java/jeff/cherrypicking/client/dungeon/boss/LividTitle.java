package jeff.cherrypicking.client.dungeon.boss;

import jeff.cherrypicking.CherryPicking;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The real Livid's colour, across the middle of the screen: {@code RED!}.
 *
 * <p>The fight is decided in the seconds while the player is blind and reading
 * nothing. A word in the wool's own colour is the fastest way to carry which
 * Livid to hit; the chat line says the same thing, but chat is the one place
 * nobody looks mid-fight.
 *
 * <p><b>The colour, not the name.</b> Hypixel calls them Frog, Hockey, Arcade
 * and so on. Those names tell a player nothing about what to look at, so this
 * shows the wool colour, which is what the Livid is actually wearing.
 *
 * <p>Drawn as this mod's own HUD element rather than through {@code Hud.setTitle},
 * so Hypixel's own titles cannot replace it halfway through and so the size and
 * the time it stays are the player's to set.
 *
 * <p>Registered the way coalroutegenerator's {@code render.LobbyMap} is.
 */
public final class LividTitle {
	public static final double MIN_SCALE = 1.0;
	public static final double MAX_SCALE = 6.0;
	public static final double MIN_SECONDS = 1.0;
	public static final double MAX_SECONDS = 10.0;

	/** How far down the screen the word sits, as a share of its height. */
	private static final double HEIGHT_FRACTION = 0.38;

	/**
	 * How long the word takes to fade away, <b>after</b> its time is up.
	 *
	 * <p>The fade runs on the end of the set time rather than inside it, so
	 * "how long it stays" is how long the colour is actually readable. Taking
	 * the fade out of the set time made a 4-second call-out start dimming at
	 * 3.5s, which reads as the word leaving early - see check S4-04.
	 */
	private static final long FADE_MILLIS = 500;

	// Settings. Defaults are here, at the field.
	private static volatile boolean enabled = true;
	private static volatile double scale = 3.0;
	private static volatile double seconds = 4.0;

	/** What to draw, or {@code null}. Written by the client thread, read by the render thread. */
	private static volatile Livid.Name showing;
	/** When {@link #showing} was set, from {@link System#currentTimeMillis()}. */
	private static volatile long shownAt;

	private LividTitle() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
				CherryPicking.id("livid_title"), LividTitle::draw);
	}

	/** Puts a Livid's colour on screen for {@link #seconds}. Client thread. */
	public static void show(Livid.Name name) {
		showing = name;
		shownAt = System.currentTimeMillis();
	}

	/** Takes it off at once: a new run must not finish the last one's title. */
	public static void clear() {
		showing = null;
	}

	private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Livid.Name name = showing;
		if (!enabled || name == null) {
			return;
		}
		long hold = Math.round(seconds * 1000);
		long total = hold + FADE_MILLIS;
		long since = System.currentTimeMillis() - shownAt;
		if (since >= total) {
			showing = null;
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.gui.hud.isHidden() || client.getDebugOverlay().showDebugScreen()) {
			return;
		}

		// Full strength for the whole of the set time; the fade is the tail past it.
		long left = total - since;
		int alpha = since < hold ? 0xFF : (int) (left * 0xFF / FADE_MILLIS);
		if (alpha <= 0) {
			return;
		}

		Font font = client.font;
		String text = name.colour() + "!";
		float size = (float) scale;
		// Work in scaled units, so the text is centred whatever size it is drawn at.
		int width = Math.round(graphics.guiWidth() / size);
		int height = Math.round(graphics.guiHeight() / size);

		graphics.pose().pushMatrix();
		graphics.pose().scale(size, size);
		graphics.text(font, text, (width - font.width(text)) / 2,
				(int) (height * HEIGHT_FRACTION), alpha << 24 | name.rgb(), true);
		graphics.pose().popMatrix();
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void enabled(boolean value) {
		enabled = value;
		if (!value) {
			showing = null;
		}
	}

	public static double scale() {
		return scale;
	}

	public static void scale(double value) {
		scale = Math.clamp(value, MIN_SCALE, MAX_SCALE);
	}

	public static double seconds() {
		return seconds;
	}

	public static void seconds(double value) {
		seconds = Math.clamp(value, MIN_SECONDS, MAX_SECONDS);
	}
}
