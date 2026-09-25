package jeff.cherrypicking.client.cosmetics;

import java.util.List;

/**
 * The color of an animated dye at a given time, as Skyblocker draws it. Ported from Skyblocker's
 * {@code CustomArmorAnimatedDyes} and {@code OkLabColor} (LGPL-3.0).
 *
 * <p>Skyblocker moves each animation a step every frame. Here the color is a function of the time.
 * The colors are the same, and every piece with the same dye stays in step with no stored state.
 */
final class AnimatedDyes {
	private AnimatedDyes() {
	}

	/** The RGB color of {@code dye} at {@code seconds} on a clock that only moves forward. */
	static int color(Cosmetic.AnimatedDye dye, double seconds) {
		double position = start(dye) + seconds / dye.duration();
		float progress;
		if (dye.cycleBack()) {
			// From 0 to 1 the animation goes forward; from 1 to 2 it goes back.
			double cycle = position % 2;
			progress = (float) (cycle < 1 ? cycle : 2 - cycle);
		} else {
			progress = (float) (position % 1);
		}
		return gradient(dye.keyframes(), progress);
	}

	/**
	 * Where the animation starts, in the positions of {@link #color}. Skyblocker's delay starts it on
	 * the way back at {@code delay / duration} when it cycles back, and at {@code 1 - delay / duration}
	 * when it does not.
	 */
	private static double start(Cosmetic.AnimatedDye dye) {
		if (dye.delay() <= 0) {
			return 0;
		}
		float offset = Math.clamp(dye.delay() / dye.duration(), 0f, 1f);
		return dye.cycleBack() ? 2 - offset : 1 - offset;
	}

	/** The color at {@code progress}, blended between the two keyframes on each side of it. */
	private static int gradient(List<Cosmetic.Keyframe> keyframes, float progress) {
		int index = 0;
		while (index < keyframes.size() - 2 && keyframes.get(index + 1).time() < progress) {
			index++;
		}
		Cosmetic.Keyframe from = keyframes.get(index);
		Cosmetic.Keyframe to = keyframes.get(index + 1);
		float span = to.time() - from.time();
		float blend = span > 0 ? Math.clamp((progress - from.time()) / span, 0f, 1f) : 1f;
		return blend(from.color(), to.color(), blend);
	}

	/**
	 * Blends two RGB colors in the OkLab color space, which keeps the brightness even across the
	 * blend. See https://bottosson.github.io/posts/oklab.
	 */
	private static int blend(int first, int second, float amount) {
		float[] a = okLab(first);
		float[] b = okLab(second);
		float l = Math.fma(amount, b[0] - a[0], a[0]);
		float m = Math.fma(amount, b[1] - a[1], a[1]);
		float s = Math.fma(amount, b[2] - a[2], a[2]);

		float l_ = l + 0.3963377774f * m + 0.2158037573f * s;
		float m_ = l - 0.1055613458f * m - 0.0638541728f * s;
		float s_ = l - 0.0894841775f * m - 1.2914855480f * s;
		float lc = l_ * l_ * l_;
		float mc = m_ * m_ * m_;
		float sc = s_ * s_ * s_;
		float red = Math.fma(4.0767416621f, lc, Math.fma(-3.3077115913f, mc, 0.2309699292f * sc));
		float green = Math.fma(-1.2684380046f, lc, Math.fma(2.6097574011f, mc, -0.3413193965f * sc));
		float blue = Math.fma(-0.0041960863f, lc, Math.fma(-0.7034186147f, mc, 1.7076147010f * sc));
		return channel(red) << 16 | channel(green) << 8 | channel(blue);
	}

	private static float[] okLab(int rgb) {
		float r = linear((rgb >> 16 & 0xFF) / 255f);
		float g = linear((rgb >> 8 & 0xFF) / 255f);
		float b = linear((rgb & 0xFF) / 255f);
		float l = (float) Math.cbrt(Math.fma(0.4122214708f, r, Math.fma(0.5363325363f, g, 0.0514459929f * b)));
		float m = (float) Math.cbrt(Math.fma(0.2119034982f, r, Math.fma(0.6806995451f, g, 0.1073969566f * b)));
		float s = (float) Math.cbrt(Math.fma(0.0883024619f, r, Math.fma(0.2817188376f, g, 0.6299787005f * b)));
		return new float[] {
				Math.fma(0.2104542553f, l, Math.fma(0.7936177850f, m, -0.0040720468f * s)),
				Math.fma(1.9779984951f, l, Math.fma(-2.4285922050f, m, 0.4505937099f * s)),
				Math.fma(0.0259040371f, l, Math.fma(0.7827717662f, m, -0.8086757660f * s))};
	}

	/** An sRGB channel from 0 to 1, made linear. */
	private static float linear(float channel) {
		return channel <= 0.04045f ? channel / 12.92f : (float) Math.pow((channel + 0.055f) / 1.055f, 2.4f);
	}

	/** A linear channel back to sRGB, from 0 to 255. */
	private static int channel(float linear) {
		float srgb = linear <= 0.0031308f ? linear * 12.92f
				: Math.fma(1.055f, (float) Math.pow(linear, 1.0f / 2.4f), -0.055f);
		return Math.round(Math.clamp(srgb * 255f, 0, 255));
	}
}
