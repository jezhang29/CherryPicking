package jeff.cherrypicking.client.cosmetics;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * What Hypixel puts in an item's {@code minecraft:custom_data}: the SkyBlock {@code id}, such as
 * {@code WISE_WITHER_CHESTPLATE}, and the item's own {@code uuid}.
 *
 * <p>Skyblocker keys every cosmetic by the uuid. Other players' armor comes with the id only, not the
 * uuid (docs/friend-cosmetics-plan.md, section 4.4), so a friend's armor is matched by the id.
 *
 * <p>This mod does not depend on Skyblocker, so it reads the tag itself. The tag is server text: a
 * value that is missing, or that is not a string, is empty.
 */
public final class SkyblockItem {
	private SkyblockItem() {
	}

	/** A copy of the stack's custom data; empty for an item with none. */
	static CompoundTag tag(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
	}

	/** The item's own SkyBlock UUID, or an empty string. */
	static String uuid(CompoundTag customData) {
		return customData.getStringOr("uuid", "");
	}

	/** The SkyBlock item id, or an empty string. */
	static String id(CompoundTag customData) {
		return customData.getStringOr("id", "");
	}
}
