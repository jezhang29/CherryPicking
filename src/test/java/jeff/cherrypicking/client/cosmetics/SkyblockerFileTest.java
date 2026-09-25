package jeff.cherrypicking.client.cosmetics;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which of your Skyblocker looks become shared looks. The samples are cut down from a real
 * {@code skyblocker.json}; a map this mod does not share, item names, is in them too.
 */
class SkyblockerFileTest {
	private static final String CHEST = "accac1fb-a17b-46f7-9925-6aa2aa8e35a6";
	private static final String HELMET = "fc1925d4-f545-46f5-8427-1b9d94d3db8c";

	@Test
	void eachItemGetsOneLookWithEveryKindItHas() {
		JsonObject skyblocker = JsonParser.parseString("""
				{"general": {
				  "customDyeColors": {"%1$s": 16711680},
				  "customArmorTrims": {"%1$s": {"material": "minecraft:netherite", "pattern": "minecraft:tide"}},
				  "customHelmetTextures": {"%2$s": "dGV4dHVyZQ=="},
				  "customAnimatedHelmetTextures": {"%2$s": "SENTINEL_WARDEN_RED"},
				  "customGlint": {"%1$s": true},
				  "customArmorModel": {"%1$s": "minecraft:netherite"},
				  "customAnimatedDyes": {"%1$s": {"keyframes": [], "cycleBack": true, "delay": 0.0, "duration": 10.0}},
				  "customItemNames": {"%2$s": "Hat"}}}
				""".formatted(CHEST, HELMET)).getAsJsonObject();

		Map<String, JsonObject> looks = SkyblockerFile.looks(skyblocker);

		assertEquals(List.of(CHEST, HELMET), List.copyOf(looks.keySet()));
		assertEquals(JsonParser.parseString("""
				{"dye": 16711680, "trim": {"material": "minecraft:netherite", "pattern": "minecraft:tide"},
				 "glint": true, "armorModel": "minecraft:netherite",
				 "animatedDye": {"keyframes": [], "cycleBack": true, "delay": 0.0, "duration": 10.0}}"""),
				looks.get(CHEST));
		assertEquals(JsonParser.parseString("{\"helmetTexture\": \"dGV4dHVyZQ==\", \"animatedHelmet\": \"SENTINEL_WARDEN_RED\"}"),
				looks.get(HELMET));
	}

	@Test
	void aFileWithoutLooksHasNone() {
		assertEquals(Map.of(), SkyblockerFile.looks(JsonParser.parseString("{}").getAsJsonObject()));
		assertEquals(Map.of(), SkyblockerFile.looks(JsonParser.parseString(
				"{\"general\": {\"customDyeColors\": []}}").getAsJsonObject()));
	}
}
