package jeff.cherrypicking.client.dungeon.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which starred-mob names show: a near mob's name hides, so it does not cover the mob. */
class StarMobRendererTest {
	@Test
	void aNearMobHidesItsName() {
		assertFalse(StarMobRenderer.labelShows(3.0, 8));
		assertFalse(StarMobRenderer.labelShows(7.9, 8));
	}

	@Test
	void aFarMobShowsItsName() {
		assertTrue(StarMobRenderer.labelShows(8.0, 8));
		assertTrue(StarMobRenderer.labelShows(25.0, 8));
	}

	@Test
	void zeroShowsEveryName() {
		assertTrue(StarMobRenderer.labelShows(0.5, 0));
	}
}
