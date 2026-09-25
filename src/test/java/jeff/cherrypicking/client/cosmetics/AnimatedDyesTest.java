package jeff.cherrypicking.client.cosmetics;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The color of a friend's animated dye over time. Each expected color is a keyframe the animation
 * must be on at that time, except the blend test, whose value is worked out by hand below.
 */
class AnimatedDyesTest {
	private static final int RED = 0xFF0000;
	private static final int LIME = 0x00FF00;
	private static final int BLUE = 0x0000FF;

	/** Red at the start, lime halfway, blue at the end, in 4 s. */
	private static final List<Cosmetic.Keyframe> FRAMES = List.of(
			new Cosmetic.Keyframe(RED, 0f), new Cosmetic.Keyframe(LIME, 0.5f), new Cosmetic.Keyframe(BLUE, 1f));

	@Test
	void withoutCycleBackItStartsAgainAtTheEnd() {
		Cosmetic.AnimatedDye dye = new Cosmetic.AnimatedDye(FRAMES, false, 0f, 4f);
		assertEquals(RED, AnimatedDyes.color(dye, 0));
		assertEquals(LIME, AnimatedDyes.color(dye, 2));
		assertEquals(RED, AnimatedDyes.color(dye, 4));
		assertEquals(LIME, AnimatedDyes.color(dye, 6));
	}

	@Test
	void withCycleBackItGoesBackFromTheEnd() {
		Cosmetic.AnimatedDye dye = new Cosmetic.AnimatedDye(FRAMES, true, 0f, 4f);
		assertEquals(BLUE, AnimatedDyes.color(dye, 4));
		assertEquals(LIME, AnimatedDyes.color(dye, 6));
		assertEquals(RED, AnimatedDyes.color(dye, 8));
		assertEquals(LIME, AnimatedDyes.color(dye, 10));
	}

	@Test
	void aDelayStartsPartWayAsInSkyblocker() {
		// Skyblocker: with cycle back, a 1 s delay of 4 s starts on the way back at 1/4.
		List<Cosmetic.Keyframe> limeAtAQuarter = List.of(
				new Cosmetic.Keyframe(RED, 0f), new Cosmetic.Keyframe(LIME, 0.25f), new Cosmetic.Keyframe(BLUE, 1f));
		assertEquals(LIME, AnimatedDyes.color(new Cosmetic.AnimatedDye(limeAtAQuarter, true, 1f, 4f), 0));
		assertEquals(RED, AnimatedDyes.color(new Cosmetic.AnimatedDye(limeAtAQuarter, true, 1f, 4f), 1));
		// Without cycle back, it starts at 1 - 1/4.
		List<Cosmetic.Keyframe> limeAtThreeQuarters = List.of(
				new Cosmetic.Keyframe(RED, 0f), new Cosmetic.Keyframe(LIME, 0.75f), new Cosmetic.Keyframe(BLUE, 1f));
		assertEquals(LIME, AnimatedDyes.color(new Cosmetic.AnimatedDye(limeAtThreeQuarters, false, 1f, 4f), 0));
	}

	@Test
	void colorsBlendInOkLab() {
		// Halfway from black to white, OkLab lightness is 0.5. Linear light is 0.5^3 = 0.125, and
		// sRGB is 1.055 * 0.125^(1/2.4) - 0.055 = 0.3886, which is 99 of 255: #636363. A plain RGB
		// blend would give #808080.
		Cosmetic.AnimatedDye dye = new Cosmetic.AnimatedDye(
				List.of(new Cosmetic.Keyframe(0x000000, 0f), new Cosmetic.Keyframe(0xFFFFFF, 1f)), false, 0f, 2f);
		assertEquals(0x636363, AnimatedDyes.color(dye, 1));
	}
}
