package jeff.cherrypicking.client.cosmetics;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.equipment.Equippable;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which helmets Skyblocker's customize screen may give a head skin, and the player head drawn in
 * their place.
 */
class HelmetSkinsTest {
	private static final String UUID = "3f1b6a52-2c4e-4d8a-9b0e-7a51c6d2e9f4";

	/**
	 * Item defaults come from the game's data, which a unit test does not load. So each item gets the
	 * one default that matters here, written by hand: where it is worn. A sea lantern and a player
	 * head get none, which is what the server's item has for the sea lantern.
	 */
	@BeforeAll
	static void loadItems() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		bind(Items.LEATHER_HELMET, DataComponentMap.builder()
				.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).build()));
		bind(Items.LEATHER_CHESTPLATE, DataComponentMap.builder()
				.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build()));
		bind(Items.SEA_LANTERN, DataComponentMap.builder());
		bind(Items.PLAYER_HEAD, DataComponentMap.builder());
	}

	private static void bind(Item item, DataComponentMap.Builder components) {
		Holder.Reference<Item> holder = item.builtInRegistryHolder();
		if (!holder.areComponentsBound()) {
			holder.bindComponents(components.set(DataComponents.MAX_STACK_SIZE, 1).build());
		}
	}

	private static ItemStack item(Item item, String id, String uuid) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		if (!uuid.isEmpty()) {
			tag.putString("uuid", uuid);
		}
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	@Test
	void aLeatherHelmetCanHaveASkin() {
		assertTrue(HelmetSkins.canHaveSkin(item(Items.LEATHER_HELMET, "TARANTULA_HELMET", UUID)));
	}

	@Test
	void aSeaLanternHelmetCanHaveASkin() {
		assertTrue(HelmetSkins.canHaveSkin(item(Items.SEA_LANTERN, "LAPIS_ARMOR_HELMET", UUID)));
	}

	@Test
	void aSkullIsLeftToSkyblocker() {
		assertFalse(HelmetSkins.canHaveSkin(item(Items.PLAYER_HEAD, "DIVAN_HELMET", UUID)));
	}

	@Test
	void armorForAnotherSlotCannotHaveASkin() {
		assertFalse(HelmetSkins.canHaveSkin(item(Items.LEATHER_CHESTPLATE, "TARANTULA_CHESTPLATE", UUID)));
	}

	@Test
	void anotherPlayersHelmetHasNoUuidSoNoSkin() {
		assertFalse(HelmetSkins.canHaveSkin(item(Items.LEATHER_HELMET, "TARANTULA_HELMET", "")));
	}

	@Test
	void theSkullKeepsTheHelmetsIdAndUuidAndHasNoSkinYet() {
		ItemStack skull = HelmetSkins.skull(item(Items.LEATHER_HELMET, "TARANTULA_HELMET", UUID));
		assertTrue(skull.is(Items.PLAYER_HEAD));
		assertEquals("TARANTULA_HELMET", SkyblockItem.id(SkyblockItem.tag(skull)));
		assertEquals(UUID, SkyblockItem.uuid(SkyblockItem.tag(skull)));
		assertNull(skull.get(DataComponents.PROFILE));
	}
}
