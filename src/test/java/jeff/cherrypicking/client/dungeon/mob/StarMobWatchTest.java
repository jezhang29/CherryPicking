package jeff.cherrypicking.client.dungeon.mob;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which armor-stand names put a box on the mob under them. Only a starred mob's nametag may: a crit
 * damage tag that floats next to a mob must not make that mob look starred.
 */
class StarMobWatchTest {
	@Test
	void aStarredNametagNamesAMob() {
		assertTrue(StarMobWatch.namesStarredMob("✯ Zombie Lord 1.2M❤"));
		assertTrue(StarMobWatch.namesStarredMob("✯ Shadow Assassin 2.5M❤"));
		assertTrue(StarMobWatch.namesStarredMob("✯ Fels 450k❤"));
	}

	@Test
	void anUnstarredNametagDoesNot() {
		assertFalse(StarMobWatch.namesStarredMob("Zombie Lord 1.2M❤"));
	}

	@Test
	void aCritDamageTagDoesNot() {
		assertFalse(StarMobWatch.namesStarredMob("✯1,234✯"));
		assertFalse(StarMobWatch.namesStarredMob("✯12,345,678✯"));
		assertFalse(StarMobWatch.namesStarredMob("✧1,234✯"));
		assertFalse(StarMobWatch.namesStarredMob("✯12.5M✯"));
	}
}
