package jeff.cherrypicking.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Skyblocker's animated head list, as the NEU repo writes it, and which frame shows when. The sample
 * has the shape of {@code constants/animatedskulls.json}, with short made-up textures.
 */
class AnimatedHeadsTest {
	private static final String FIRST = texture("http://textures.minecraft.net/texture/aaa");
	private static final String SECOND = texture("http://textures.minecraft.net/texture/bbb");
	private static final String ELSEWHERE = texture("https://example.com/texture/ccc");

	@Test
	void eachHeadKeepsItsMojangFramesInOrder() {
		Map<String, AnimatedHeads.Head> heads = AnimatedHeads.parse(JsonParser.parseString("""
				{"help": "not a head", "skins": {
				  "SENTINEL_WARDEN_RED": {"ticks": 2, "textures": [
				    "381bf95a-8f72-39fe-b4b4-6b338eeefa6a:%1$s",
				    "381bf95a-8f72-39fe-b4b4-6b338eeefa6a:%3$s",
				    "381bf95a-8f72-39fe-b4b4-6b338eeefa6a:%2$s"]},
				  "TEST_HEAD": {"ticks": 0, "textures": ["00000000-0000-0000-0000-000000000000:%1$s"]},
				  "ONLY_BAD": {"ticks": 2, "textures": ["00000000-0000-0000-0000-000000000000:%3$s"]},
				  "NO_TICKS": {"textures": ["00000000-0000-0000-0000-000000000000:%1$s"]}}}
				""".formatted(FIRST, SECOND, ELSEWHERE)).getAsJsonObject());

		assertEquals(Map.of(
				"SENTINEL_WARDEN_RED", new AnimatedHeads.Head(2, List.of(FIRST, SECOND)),
				"TEST_HEAD", new AnimatedHeads.Head(1, List.of(FIRST))), heads);
	}

	@Test
	void eachFrameShowsForItsTicksThenTheNext() {
		AnimatedHeads.Head head = new AnimatedHeads.Head(2, List.of(FIRST, SECOND));
		// 2 ticks are 100 ms.
		assertEquals(FIRST, AnimatedHeads.frame(head, 0));
		assertEquals(FIRST, AnimatedHeads.frame(head, 99));
		assertEquals(SECOND, AnimatedHeads.frame(head, 100));
		assertEquals(FIRST, AnimatedHeads.frame(head, 200));
		assertEquals(SECOND, AnimatedHeads.frame(head, -100));
	}

	private static String texture(String url) {
		String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
		return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}
}
