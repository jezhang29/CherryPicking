import java.util.Optional;

/**
 * The mod's version: the hand-set {@code major.minor} from gradle.properties, then the number of
 * commits on the checked-out branch. Each commit raises the version with no manual bump, which a
 * fixed version in gradle.properties never did.
 */
public final class ModVersion {
	private ModVersion() {
	}

	/**
	 * @param base        {@code major.minor}, such as {@code 1.0}
	 * @param commitCount the output of {@code git rev-list --count HEAD}, or {@code null} when git failed
	 * @return {@code base.count}, or empty when {@code commitCount} is not a whole number
	 */
	public static Optional<String> of(String base, String commitCount) {
		String count = commitCount == null ? "" : commitCount.trim();
		if (!count.matches("\\d+")) {
			return Optional.empty();
		}
		return Optional.of(base + "." + count);
	}
}
