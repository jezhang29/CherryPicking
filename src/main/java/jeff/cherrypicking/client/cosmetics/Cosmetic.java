package jeff.cherrypicking.client.cosmetics;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import net.minecraft.resources.Identifier;

/**
 * One item's look, as Skyblocker stores it, plus the SkyBlock {@code id} of the item it belongs to.
 * Built only by {@link Payload}, which checks every field, so the values here are trusted.
 *
 * @param id            the SkyBlock item id; other clients match a friend's armor by it
 * @param dye           RGB, without alpha
 * @param animatedDye   drawn in place of {@code dye}, as Skyblocker does
 * @param helmetTexture a base64 texture property whose skin is on textures.minecraft.net
 * @param armorModel    an equipment asset, such as {@code minecraft:netherite}, drawn in place of the
 *                      armor's own; whether the client has it is checked when drawing
 * @param animatedHelmet the id of an animated head in Skyblocker's list, drawn when there is no
 *                      {@code helmetTexture}, as Skyblocker does
 */
record Cosmetic(String id, OptionalInt dye, Optional<AnimatedDye> animatedDye, Optional<Trim> trim,
		Optional<String> helmetTexture, Optional<Boolean> glint, Optional<Identifier> armorModel,
		Optional<String> animatedHelmet) {
	/** Names in the trim registries. Whether the client has them is checked when drawing. */
	record Trim(Identifier material, Identifier pattern) {
	}

	/**
	 * Skyblocker's animated dye: the color moves through the keyframes in {@code duration} seconds.
	 *
	 * @param keyframes at least two, with times from 0 to 1 in order
	 * @param cycleBack at the end, go back through the keyframes, not start again
	 * @param delay     seconds that move the start of the animation, as Skyblocker reads them
	 */
	record AnimatedDye(List<Keyframe> keyframes, boolean cycleBack, float delay, float duration) {
	}

	/** One color of an animated dye, RGB without alpha, at {@code time} from 0 to 1. */
	record Keyframe(int color, float time) {
	}
}
