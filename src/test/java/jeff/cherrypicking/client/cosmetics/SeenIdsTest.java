package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The saved item ids, which let the first payload after a restart keep the looks of every wardrobe
 * set seen before.
 */
class SeenIdsTest {
	private static final String CHEST = "accac1fb-a17b-46f7-9925-6aa2aa8e35a6";
	private static final String HELMET = "fc1925d4-f545-46f5-8427-1b9d94d3db8c";

	@TempDir
	Path folder;

	@Test
	void savedIdsReadBack() {
		Path file = folder.resolve("config/cherrypicking-seen-items.json");
		SeenIds.write(file, Map.of(CHEST, "WISE_WITHER_CHESTPLATE", HELMET, "FROZEN_BLAZE_HELMET"));

		assertEquals(Map.of(CHEST, "WISE_WITHER_CHESTPLATE", HELMET, "FROZEN_BLAZE_HELMET"), SeenIds.read(file));
	}

	@Test
	void aBadEntryOrFileGivesNoIds() throws IOException {
		Path file = folder.resolve("seen.json");
		assertEquals(Map.of(), SeenIds.read(file));

		Files.writeString(file, """
				{"%s": "WISE_WITHER_CHESTPLATE", "not-a-uuid": "WISE_WITHER_BOOTS",
				 "%s": "lower case", "11111111-1111-1111-1111-111111111111": 5}
				""".formatted(CHEST, HELMET), StandardCharsets.UTF_8);
		assertEquals(Map.of(CHEST, "WISE_WITHER_CHESTPLATE"), SeenIds.read(file));

		Files.writeString(file, "[1, 2", StandardCharsets.UTF_8);
		assertEquals(Map.of(), SeenIds.read(file));
	}

	@Test
	void anIdSeenThisSessionWinsOverTheSavedOne() {
		SeenIds.clear();
		SeenIds.addSaved(Map.of(CHEST, "WISE_WITHER_CHESTPLATE"));
		SeenIds.addSaved(Map.of(CHEST, "OLD_NAME", HELMET, "FROZEN_BLAZE_HELMET"));

		assertEquals(Map.of(CHEST, "WISE_WITHER_CHESTPLATE", HELMET, "FROZEN_BLAZE_HELMET"), SeenIds.all());
		SeenIds.clear();
	}
}
