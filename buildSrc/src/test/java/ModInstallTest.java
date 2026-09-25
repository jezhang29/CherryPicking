import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What the player's mods folder holds after {@code installMod}: one copy of this mod, the new one,
 * and every other file as it was.
 */
class ModInstallTest {
	@TempDir
	Path directory;

	private Path mods;
	private Path built;

	@BeforeEach
	void start() throws IOException {
		mods = Files.createDirectory(directory.resolve("mods"));
		built = jar(directory.resolve("built.jar"), "{\"id\": \"cherrypicking\", \"version\": \"1.0.1\"}");
	}

	@Test
	void anUpgradeLeavesOnlyTheNewJar() throws IOException {
		jar(mods.resolve("cherrypicking-1.0.0.jar"), "{\"id\": \"cherrypicking\", \"version\": \"1.0.0\"}");
		jar(mods.resolve("coalroutegenerator-1.0.0.jar"), "{\"id\": \"coalroutegenerator\"}");

		List<String> removed = ModInstall.install(built, mods, "cherrypicking-1.0.1.jar");

		assertEquals(List.of("cherrypicking-1.0.0.jar"), removed);
		assertEquals(List.of("cherrypicking-1.0.1.jar", "coalroutegenerator-1.0.0.jar"), names());
		assertArrayEquals(Files.readAllBytes(built), Files.readAllBytes(mods.resolve("cherrypicking-1.0.1.jar")));
	}

	@Test
	void aRenamedCopyOfThisModIsRemovedToo() throws IOException {
		jar(mods.resolve("CherryPicking old.jar"), "{\"id\": \"cherrypicking\"}");

		List<String> removed = ModInstall.install(built, mods, "cherrypicking-1.0.1.jar");

		assertEquals(List.of("CherryPicking old.jar"), removed);
		assertEquals(List.of("cherrypicking-1.0.1.jar"), names());
	}

	@Test
	void theSameVersionAgainReplacesTheFile() throws IOException {
		jar(mods.resolve("cherrypicking-1.0.1.jar"), "{\"id\": \"cherrypicking\", \"version\": \"old build\"}");

		List<String> removed = ModInstall.install(built, mods, "cherrypicking-1.0.1.jar");

		assertEquals(List.of(), removed);
		assertEquals(List.of("cherrypicking-1.0.1.jar"), names());
		assertArrayEquals(Files.readAllBytes(built), Files.readAllBytes(mods.resolve("cherrypicking-1.0.1.jar")));
	}

	@Test
	void filesThatAreNotThisModStay() throws IOException {
		// A mod whose own settings mention this id, only below the top level.
		jar(mods.resolve("addon.jar"), "{\"id\": \"addon\", \"custom\": {\"id\": \"cherrypicking\"}}");
		// Fabric does not load a disabled jar, so it is not a second copy.
		jar(mods.resolve("cherrypicking-0.9.0.jar.disabled"), "{\"id\": \"cherrypicking\"}");
		jar(mods.resolve("no-metadata.jar"), null);
		jar(mods.resolve("broken-metadata.jar"), "{\"id\": ");
		Files.writeString(mods.resolve("not-a-zip.jar"), "text");
		Files.writeString(mods.resolve("notes.txt"), "text");

		List<String> removed = ModInstall.install(built, mods, "cherrypicking-1.0.1.jar");

		assertEquals(List.of(), removed);
		assertEquals(List.of("addon.jar", "broken-metadata.jar", "cherrypicking-0.9.0.jar.disabled",
				"cherrypicking-1.0.1.jar", "no-metadata.jar", "not-a-zip.jar", "notes.txt"), names());
	}

	@Test
	void aBuiltJarWithoutAModIdIsNotInstalled() throws IOException {
		Path noId = jar(directory.resolve("no-id.jar"), "{\"version\": \"1.0.1\"}");
		jar(mods.resolve("cherrypicking-1.0.0.jar"), "{\"id\": \"cherrypicking\"}");

		assertThrows(IllegalArgumentException.class, () -> ModInstall.install(noId, mods, "cherrypicking-1.0.1.jar"));
		assertEquals(List.of("cherrypicking-1.0.0.jar"), names());
	}

	private List<String> names() throws IOException {
		try (Stream<Path> files = Files.list(mods)) {
			return files.map(file -> file.getFileName().toString()).sorted().toList();
		}
	}

	/** A jar with {@code modJson} as its fabric.mod.json, or with no fabric.mod.json if null. */
	private static Path jar(Path file, String modJson) throws IOException {
		try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("jeff/Example.class"));
			zip.write(new byte[] {(byte) 0xCA, (byte) 0xFE});
			zip.closeEntry();
			if (modJson != null) {
				zip.putNextEntry(new ZipEntry("fabric.mod.json"));
				zip.write(modJson.getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return file;
	}
}
