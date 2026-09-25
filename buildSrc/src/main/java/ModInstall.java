import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import groovy.json.JsonSlurper;

/**
 * Puts a mod jar into a mods folder as the only copy of that mod. Fabric refuses to start with two
 * jars of the same mod id, and the jar name carries the version, so each version bump used to leave
 * the old jar beside the new one.
 */
public final class ModInstall {
	private ModInstall() {
	}

	/**
	 * Copies {@code jar} into {@code modsDir} as {@code fileName}, then deletes every other jar there
	 * with the same mod id, whatever its name. Other mods, and files that are not a readable mod jar,
	 * stay. The copy comes first, so a failure leaves the mod installed at least once.
	 *
	 * @return the names of the jars deleted
	 */
	public static List<String> install(Path jar, Path modsDir, String fileName) throws IOException {
		String id = modId(jar).orElseThrow(() -> new IllegalArgumentException(jar + " has no fabric.mod.json id"));
		Path target = modsDir.resolve(fileName);
		Files.copy(jar, target, StandardCopyOption.REPLACE_EXISTING);

		List<String> removed = new ArrayList<>();
		try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
			for (Path other : jars) {
				if (!other.equals(target) && Files.isRegularFile(other) && modId(other).filter(id::equals).isPresent()) {
					Files.delete(other);
					removed.add(other.getFileName().toString());
				}
			}
		}
		removed.sort(null);
		return removed;
	}

	/** The top-level {@code id} in the jar's fabric.mod.json; empty if the jar has none or cannot be read. */
	static Optional<String> modId(Path jar) {
		try (ZipFile zip = new ZipFile(jar.toFile())) {
			ZipEntry entry = zip.getEntry("fabric.mod.json");
			if (entry == null) {
				return Optional.empty();
			}
			try (InputStream in = zip.getInputStream(entry)) {
				return new JsonSlurper().parse(in, "UTF-8") instanceof Map<?, ?> json && json.get("id") instanceof String id
						? Optional.of(id)
						: Optional.empty();
			}
		} catch (IOException | RuntimeException unreadable) {
			// Not a mod jar this build can read, so not one it may delete.
			return Optional.empty();
		}
	}
}
