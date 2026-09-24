package jeff.cherrypicking.client.cosmetics;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which items have a SkyBlock UUID. Friend cosmetics are keyed by it, so an item with a missing or
 * malformed uuid must have none rather than a wrong one.
 */
class ItemUuidTest {
	@Test
	void aSkyBlockItemHasItsUuid() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "POWER_WITHER_CHESTPLATE");
		tag.putString("uuid", "3f1b6a52-2c4e-4d8a-9b0e-7a51c6d2e9f4");
		assertEquals("3f1b6a52-2c4e-4d8a-9b0e-7a51c6d2e9f4", ItemUuid.of(tag));
	}

	@Test
	void anItemWithNoUuidHasNone() {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", "POWER_WITHER_CHESTPLATE");
		assertEquals("", ItemUuid.of(tag));
	}

	@Test
	void aUuidThatIsNotTextIsNone() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("uuid", 7);
		assertEquals("", ItemUuid.of(tag));
	}
}
