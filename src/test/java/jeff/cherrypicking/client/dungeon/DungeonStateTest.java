package jeff.cherrypicking.client.dungeon;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What every dungeon feature relies on from the gate: a warp into a new run, even on the same floor,
 * moves {@code generation} so rooms, puzzles and mobs from the last run are dropped.
 *
 * <p>The gate is global, so every test starts on a fresh level with the developer override off.
 */
class DungeonStateTest {
	private static final List<String> F5 = List.of("Cleared: 0%", "⏣ The Catacombs (F5)");
	private static final List<String> HUB = List.of("⏣ Village");
	private static final String TITLE = "SKYBLOCK";

	@BeforeEach
	void start() {
		DungeonState.forced(false);
		DungeonState.world(new Object());
	}

	@AfterEach
	void finish() {
		DungeonState.forced(false);
		DungeonState.world(new Object());
	}

	@Test
	void aNewLevelOnTheSameFloorIsANewRun() {
		inF5(new Object());
		int firstRun = DungeonState.generation();

		DungeonState.world(new Object());
		DungeonState.gate(null);

		assertNotEquals(firstRun, DungeonState.generation());
		assertFalse(DungeonState.inCatacombs());
		assertEquals(OptionalInt.empty(), DungeonState.floor());
		assertEquals(List.of(), DungeonState.sidebar());
	}

	@Test
	void theNewRunStartsWhenItsSidebarArrives() {
		inF5(new Object());

		DungeonState.world(new Object());
		DungeonState.read(List.of(), "", "");
		DungeonState.gate(null);
		assertFalse(DungeonState.inCatacombs());

		DungeonState.read(F5, TITLE, "");
		DungeonState.gate(null);
		assertTrue(DungeonState.inClear());
		assertEquals(OptionalInt.of(5), DungeonState.floor());
	}

	@Test
	void theSidebarTitleSaysSkyBlockWithoutLocraw() {
		DungeonState.read(HUB, "SKYBLOCK CO-OP", "");
		assertTrue(DungeonState.inSkyBlock());

		DungeonState.read(List.of("Players: 12"), "BED WARS", "");
		assertFalse(DungeonState.inSkyBlock());
	}

	@Test
	void aNewLevelIsNotSkyBlockUntilItsSidebarArrives() {
		DungeonState.read(HUB, TITLE, "");

		DungeonState.world(new Object());
		assertFalse(DungeonState.inSkyBlock());
	}

	@Test
	void theSameLevelKeepsTheRun() {
		Object level = new Object();
		inF5(level);
		int run = DungeonState.generation();

		DungeonState.world(level);
		DungeonState.read(F5, TITLE, "");
		DungeonState.gate(null);

		assertEquals(run, DungeonState.generation());
		assertTrue(DungeonState.inClear());
	}

	@Test
	void leavingForTheHubEndsTheRun() {
		inF5(new Object());

		DungeonState.world(new Object());
		DungeonState.read(HUB, TITLE, "");
		DungeonState.gate(null);

		assertFalse(DungeonState.inCatacombs());
		assertEquals(OptionalInt.empty(), DungeonState.floor());
	}

	@Test
	void theLocrawReplyAloneSaysDungeon() {
		DungeonState.world(new Object());
		DungeonState.read(List.of(), TITLE, "dungeon");
		DungeonState.gate(null);

		assertTrue(DungeonState.inCatacombs());
		assertEquals(OptionalInt.empty(), DungeonState.floor());
	}

	@Test
	void aForcedRunStillStartsOverOnANewLevel() {
		DungeonState.forced(true);
		DungeonState.world(new Object());
		DungeonState.gate(null);
		int firstRun = DungeonState.generation();

		DungeonState.world(new Object());
		DungeonState.gate(null);

		assertNotEquals(firstRun, DungeonState.generation());
		assertTrue(DungeonState.inCatacombs());
	}

	private static void inF5(Object level) {
		DungeonState.world(level);
		DungeonState.read(F5, TITLE, "");
		DungeonState.gate(null);
		assertTrue(DungeonState.inClear());
		assertEquals(OptionalInt.of(5), DungeonState.floor());
	}
}
