package jeff.cherrypicking;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants. The mod is client-only; the entry point is
 * {@link jeff.cherrypicking.client.CherryPickingClient}.
 */
public final class CherryPicking {
	public static final String MOD_ID = "cherrypicking";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private CherryPicking() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
