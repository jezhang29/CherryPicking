package jeff.cherrypicking.client.cosmetics;

import java.util.Optional;
import java.util.OptionalInt;

import net.minecraft.resources.Identifier;

/**
 * One item's look, as Skyblocker stores it, plus the SkyBlock {@code id} of the item it belongs to.
 * Built only by {@link Payload}, which checks every field, so the values here are trusted.
 *
 * @param id            the SkyBlock item id; other clients match a friend's armor by it
 * @param dye           RGB, without alpha
 * @param helmetTexture a base64 texture property whose skin is on textures.minecraft.net
 */
record Cosmetic(String id, OptionalInt dye, Optional<Trim> trim, Optional<String> helmetTexture,
		Optional<Boolean> glint) {
	/** Names in the trim registries. Whether the client has them is checked when drawing. */
	record Trim(Identifier material, Identifier pattern) {
	}
}
