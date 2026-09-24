package jeff.cherrypicking.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import net.minecraft.world.entity.EquipmentSlot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A friend's payload comes from the network. These tests check what reaches the renderer: which look
 * a worn piece gets, and that bad data is dropped rather than drawn.
 */
class PayloadTest {
	private static final String RED_WISE = "11111111-1111-1111-1111-111111111111";
	private static final String BLUE_WISE = "22222222-2222-2222-2222-222222222222";
	private static final String GREEN_BOOTS = "33333333-3333-3333-3333-333333333333";

	private static final Cosmetic RED = look("WISE_WITHER_CHESTPLATE", 0xFF0000);
	private static final Cosmetic BLUE = look("WISE_WITHER_CHESTPLATE", 0x0000FF);
	private static final Cosmetic GREEN = look("WISE_WITHER_BOOTS", 0x00FF00);

	@Test
	void theWornItemsLookWins() {
		Payload payload = new Payload(Map.of(RED_WISE, RED, BLUE_WISE, BLUE),
				Map.of(EquipmentSlot.CHEST, BLUE_WISE));
		assertEquals(Optional.of(BLUE), payload.look(EquipmentSlot.CHEST, "WISE_WITHER_CHESTPLATE"));
	}

	@Test
	void afterAWardrobeSwapTheOnlyLookWithThatIdIsUsed() {
		// equipped still names the chestplate worn before the swap.
		Payload payload = new Payload(Map.of(RED_WISE, RED, GREEN_BOOTS, GREEN),
				Map.of(EquipmentSlot.FEET, "44444444-4444-4444-4444-444444444444"));
		assertEquals(Optional.of(GREEN), payload.look(EquipmentSlot.FEET, "WISE_WITHER_BOOTS"));
	}

	@Test
	void twoLooksWithTheSameIdAndNoWornMatchGiveNone() {
		Payload payload = new Payload(Map.of(RED_WISE, RED, BLUE_WISE, BLUE),
				Map.of(EquipmentSlot.CHEST, GREEN_BOOTS));
		assertEquals(Optional.empty(), payload.look(EquipmentSlot.CHEST, "WISE_WITHER_CHESTPLATE"));
	}

	@Test
	void armorWithNoLookOrNoIdGetsNone() {
		Payload payload = new Payload(Map.of(RED_WISE, RED), Map.of(EquipmentSlot.CHEST, RED_WISE));
		assertEquals(Optional.empty(), payload.look(EquipmentSlot.CHEST, "SHADOW_ASSASSIN_CHESTPLATE"));
		assertEquals(Optional.empty(), payload.look(EquipmentSlot.CHEST, ""));
	}

	@Test
	void aGoodPayloadDecodes() {
		String texture = texture("http://textures.minecraft.net/texture/abc123");
		Payload payload = Payload.decode("""
				{"format": 1,
				 "looks": {
				   "%s": {"id": "WISE_WITHER_CHESTPLATE", "dye": 16711680,
				          "trim": {"material": "minecraft:gold", "pattern": "minecraft:sentry"}, "glint": true},
				   "%s": {"id": "WISE_WITHER_HELMET", "helmetTexture": "%s"}},
				 "equipped": {"chest": "%s"}}
				""".formatted(RED_WISE, BLUE_WISE, texture, RED_WISE), "test").orElseThrow();

		Cosmetic chest = payload.looks().get(RED_WISE);
		assertEquals("WISE_WITHER_CHESTPLATE", chest.id());
		assertEquals(OptionalInt.of(0xFF0000), chest.dye());
		assertEquals("minecraft:gold", chest.trim().orElseThrow().material().toString());
		assertEquals("minecraft:sentry", chest.trim().orElseThrow().pattern().toString());
		assertEquals(Optional.of(true), chest.glint());
		assertEquals(Optional.of(texture), payload.looks().get(BLUE_WISE).helmetTexture());
		assertEquals(Map.of(EquipmentSlot.CHEST, RED_WISE), payload.equipped());
	}

	@Test
	void anUnusableDocumentIsIgnored() {
		assertTrue(Payload.decode("not json", "test").isEmpty());
		assertTrue(Payload.decode("[1, 2]", "test").isEmpty());
		assertTrue(Payload.decode("{\"format\": 2, \"looks\": {}}", "test").isEmpty());
		assertTrue(Payload.decode("{\"looks\": {}}", "test").isEmpty());
		assertTrue(Payload.decode("{\"format\": 1, \"pad\": \"" + "x".repeat(70_000) + "\"}", "test").isEmpty());
	}

	@Test
	void aLookWithABadKeyOrIdIsDropped() {
		Payload payload = Payload.decode("""
				{"format": 1, "looks": {
				  "not-a-uuid": {"id": "WISE_WITHER_CHESTPLATE", "dye": 1},
				  "%s": {"dye": 1},
				  "%s": {"id": "wise wither", "dye": 1},
				  "%s": {"id": "WISE_WITHER_BOOTS", "dye": 1}}}
				""".formatted(RED_WISE, BLUE_WISE, GREEN_BOOTS), "test").orElseThrow();
		assertEquals(Set.of(GREEN_BOOTS), payload.looks().keySet());
	}

	@Test
	void aBadFieldIsLeftOutAndTheRestOfTheLookKept() {
		Payload payload = Payload.decode("""
				{"format": 1, "looks": {"%s": {
				  "id": "WISE_WITHER_CHESTPLATE",
				  "dye": "red",
				  "trim": {"material": "Not An Id!", "pattern": "minecraft:sentry"},
				  "helmetTexture": "%s",
				  "glint": "yes"}}}
				""".formatted(RED_WISE, texture("https://example.com/skin.png")), "test").orElseThrow();
		assertEquals(new Cosmetic("WISE_WITHER_CHESTPLATE", OptionalInt.empty(), Optional.empty(),
				Optional.empty(), Optional.empty()), payload.looks().get(RED_WISE));
	}

	@Test
	void aDyeKeepsOnlyItsColor() {
		Payload payload = Payload.decode("""
				{"format": 1, "looks": {"%s": {"id": "WISE_WITHER_CHESTPLATE", "dye": -1}}}
				""".formatted(RED_WISE), "test").orElseThrow();
		assertEquals(OptionalInt.of(0xFFFFFF), payload.looks().get(RED_WISE).dye());
	}

	@Test
	void equippedKeepsOnlyArmorSlotsWithAUuid() {
		Payload payload = Payload.decode("""
				{"format": 1, "looks": {},
				 "equipped": {"head": "%s", "chest": "nope", "mainhand": "%s", "feet": 5}}
				""".formatted(RED_WISE, BLUE_WISE), "test").orElseThrow();
		assertEquals(Map.of(EquipmentSlot.HEAD, RED_WISE), payload.equipped());
	}

	@Test
	void onlyMojangSkinsAreAllowed() {
		assertTrue(Payload.skinOnMojang(texture("http://textures.minecraft.net/texture/abc123")));
		assertTrue(Payload.skinOnMojang(texture("https://textures.minecraft.net/texture/abc123")));
		assertFalse(Payload.skinOnMojang(texture("http://textures.minecraft.net.example.com/texture/abc")));
		assertFalse(Payload.skinOnMojang(texture("https://example.com/texture/abc")));
		assertFalse(Payload.skinOnMojang("%%% not base64 %%%"));
		assertFalse(Payload.skinOnMojang(Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8))));
	}

	private static Cosmetic look(String id, int dye) {
		return new Cosmetic(id, OptionalInt.of(dye), Optional.empty(), Optional.empty(), Optional.empty());
	}

	/** A texture property as Mojang writes one, pointing at {@code url}. */
	private static String texture(String url) {
		String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
		return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}
}
