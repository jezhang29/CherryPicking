package jeff.cherrypicking.client.cosmetics;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What an item's custom data says about it. Friend cosmetics are keyed by these values, so a missing
 * or malformed value must be empty rather than wrong.
 */
class SkyblockItemTest {
	@Test
	void yourOwnItemHasItsIdAndUuid() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "POWER_WITHER_CHESTPLATE");
		tag.putString("uuid", "3f1b6a52-2c4e-4d8a-9b0e-7a51c6d2e9f4");
		assertEquals("POWER_WITHER_CHESTPLATE", SkyblockItem.id(tag));
		assertEquals("3f1b6a52-2c4e-4d8a-9b0e-7a51c6d2e9f4", SkyblockItem.uuid(tag));
	}

	@Test
	void anotherPlayersArmorHasOnlyItsId() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "WISE_WITHER_CHESTPLATE");
		assertEquals("WISE_WITHER_CHESTPLATE", SkyblockItem.id(tag));
		assertEquals("", SkyblockItem.uuid(tag));
	}

	@Test
	void valuesThatAreNotTextAreEmpty() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("id", 3);
		tag.putInt("uuid", 7);
		assertEquals("", SkyblockItem.id(tag));
		assertEquals("", SkyblockItem.uuid(tag));
	}
}
