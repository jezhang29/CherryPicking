package jeff.cherrypicking.client.cosmetics;

import net.minecraft.nbt.CompoundTag;

/**
 * The SkyBlock UUID that Hypixel puts on an item, as the {@code uuid} string in its
 * {@code minecraft:custom_data}. Skyblocker keys every cosmetic by it, so this is the key a friend's
 * cosmetics are looked up by.
 *
 * <p>This mod does not depend on Skyblocker, so it reads the tag itself rather than calling
 * Skyblocker's {@code getUuid()}. The tag is server text: an item with no uuid, or a uuid that is
 * not a string, has none.
 */
public final class ItemUuid {
	private ItemUuid() {
	}

	/** The item's SkyBlock UUID, or an empty string if it has none. */
	static String of(CompoundTag customData) {
		return customData.getStringOr("uuid", "");
	}
}
