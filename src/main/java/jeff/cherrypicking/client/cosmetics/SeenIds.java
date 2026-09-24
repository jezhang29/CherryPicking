package jeff.cherrypicking.client.cosmetics;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * The SkyBlock id of each of your own items that this client has seen, by item uuid.
 *
 * <p>Other clients match a look by the id (docs/friend-cosmetics-plan.md, section 7), but
 * {@code skyblocker.json} has only uuids. So a look can be shared only after its item was seen: worn,
 * in the inventory, or in an open menu. Opening the wardrobe shows every set at once.
 *
 * <p>Memory only. Client thread only.
 */
final class SeenIds {
	/** A bound far above one player's items, so a long session cannot grow this without limit. */
	private static final int MAX = 20_000;
	private static final List<EquipmentSlot> ARMOR =
			List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private static final Map<String, String> ID_BY_UUID = new HashMap<>();

	private SeenIds() {
	}

	/** Records the items in the open menu (the inventory when none is open) and the worn armor. */
	static void scan(LocalPlayer player) {
		for (ItemStack stack : player.containerMenu.getItems()) {
			record(stack);
		}
		for (EquipmentSlot slot : ARMOR) {
			record(player.getItemBySlot(slot));
		}
	}

	/** The uuid worn in each armor slot now; a slot without a SkyBlock item is left out. */
	static Map<EquipmentSlot, String> equipped(LocalPlayer player) {
		Map<EquipmentSlot, String> equipped = new HashMap<>();
		for (EquipmentSlot slot : ARMOR) {
			String uuid = SkyblockItem.uuid(SkyblockItem.tag(player.getItemBySlot(slot)));
			if (!uuid.isEmpty()) {
				equipped.put(slot, uuid);
			}
		}
		return equipped;
	}

	static Map<String, String> all() {
		return Collections.unmodifiableMap(ID_BY_UUID);
	}

	static void clear() {
		ID_BY_UUID.clear();
	}

	private static void record(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		CompoundTag tag = SkyblockItem.tag(stack);
		String uuid = SkyblockItem.uuid(tag);
		String id = SkyblockItem.id(tag);
		if (uuid.isEmpty() || id.isEmpty()) {
			return;
		}
		if (ID_BY_UUID.size() >= MAX && !ID_BY_UUID.containsKey(uuid)) {
			ID_BY_UUID.clear();
		}
		ID_BY_UUID.put(uuid, id);
	}
}
