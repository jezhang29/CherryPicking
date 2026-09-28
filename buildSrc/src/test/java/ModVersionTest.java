import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The version the built jar carries, from the base and git's commit count. */
class ModVersionTest {
	@Test
	void commitCountBecomesThePatchNumber() {
		assertEquals(Optional.of("1.0.64"), ModVersion.of("1.0", "64\n"));
	}

	@Test
	void noCountWhenGitGaveNothingUsable() {
		assertEquals(Optional.empty(), ModVersion.of("1.0", null));
		assertEquals(Optional.empty(), ModVersion.of("1.0", ""));
		assertEquals(Optional.empty(), ModVersion.of("1.0", "fatal: not a git repository"));
	}
}
