package jeff.cherrypicking.client.theme;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A swatch's written form is what {@code config/cherrypicking.json} stores for every colour
 * setting. If a spelling a player already has on disk stops reading, their colour silently falls
 * back to the default, so each accepted spelling is pinned here with its expected value written out
 * by hand, not computed by the code under test.
 */
class SwatchTest {
	private static final int DEFAULT_FILL = 0x59;

	@Test
	void readsEverySavedSpelling() {
		assertEquals(Optional.of(new Swatch.Named("green", 0xFF, DEFAULT_FILL)), Swatch.read("green"));
		assertEquals(Optional.of(new Swatch.Named("green", 0x80, DEFAULT_FILL)), Swatch.read("green@80"));
		assertEquals(Optional.of(new Swatch.Literal(0xFF40A02B, DEFAULT_FILL)), Swatch.read("#40a02b"));
		assertEquals(Optional.of(new Swatch.Literal(0x8040A02B, DEFAULT_FILL)), Swatch.read("#8040a02b"));
		assertEquals(Optional.of(new Swatch.Named("green", 0xFF, 0x20)), Swatch.read("green/20"));
		assertEquals(Optional.of(new Swatch.Named("green", 0x80, 0x20)), Swatch.read("green@80/20"));
		assertEquals(Optional.of(new Swatch.Literal(0xFF40A02B, 0x20)), Swatch.read("#40a02b/20"));
	}

	@Test
	void writesWithoutDefaultSuffixes() {
		assertEquals("green", new Swatch.Named("green", 0xFF, DEFAULT_FILL).written());
		assertEquals("green@80", new Swatch.Named("green", 0x80, DEFAULT_FILL).written());
		assertEquals("green/20", new Swatch.Named("green", 0xFF, 0x20).written());
		assertEquals("#ff40a02b", new Swatch.Literal(0xFF40A02B, DEFAULT_FILL).written());
		assertEquals("#8040a02b/20", new Swatch.Literal(0x8040A02B, 0x20).written());
	}

	@Test
	void readsBackWhatItWrites() {
		List<Swatch> swatches = List.of(
				new Swatch.Named("mauve", 0xFF, DEFAULT_FILL),
				new Swatch.Named("crust", 0x00, 0xFF),
				new Swatch.Literal(0xFF000000, 0x00),
				new Swatch.Literal(0x01ABCDEF, DEFAULT_FILL));
		for (Swatch swatch : swatches) {
			assertEquals(Optional.of(swatch), Swatch.read(swatch.written()), swatch.written());
		}
	}

	@Test
	void rejectsTextThatIsNoColour() {
		for (String text : List.of("", "grean", "green@8", "green@zz", "#12345", "#1234567",
				"#40a02g", "green/2", "green/zz", "@80")) {
			assertEquals(Optional.empty(), Swatch.read(text), text);
		}
	}
}
