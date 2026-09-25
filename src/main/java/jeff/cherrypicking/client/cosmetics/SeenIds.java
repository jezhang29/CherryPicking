package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import jeff.cherrypicking.CherryPicking;

import net.fabricmc.loader.api.FabricLoader;
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
 * <p>The ids are also saved to {@code config/cherrypicking-seen-items.json}, so the first payload
 * after a restart still has every set seen before. Without the file, a friend would lose the looks
 * of your other wardrobe sets until you opened the wardrobe again. {@link Publisher} reads and writes
 * the file on an IO thread; the map itself is client thread only.
 */
final class SeenIds {
	/** A bound far above one player's items, so a long session cannot grow this without limit. */
	private static final int MAX = 20_000;
	private static final List<EquipmentSlot> ARMOR =
			List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private static final Map<String, String> ID_BY_UUID = new HashMap<>();
	/** Goes up by one each time an id is added or changed, so the saver knows when to write. */
	private static int changes;

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

	static int changes() {
		return changes;
	}

	/** Adds ids read from the file. An id seen in this session is newer and is kept. */
	static void addSaved(Map<String, String> saved) {
		saved.forEach(ID_BY_UUID::putIfAbsent);
	}

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("cherrypicking-seen-items.json");
	}

	/**
	 * The saved ids; empty if there is no file yet. An entry that is not an item uuid and a SkyBlock
	 * id is left out, and a file that cannot be read is logged. Any thread.
	 */
	static Map<String, String> read(Path file) {
		Map<String, String> saved = new HashMap<>();
		if (!Files.isRegularFile(file)) {
			return saved;
		}
		try {
			if (!(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)) instanceof JsonObject entries)) {
				throw new JsonParseException("not a JSON object");
			}
			for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
				if (saved.size() < MAX && Payload.ITEM_UUID.matcher(entry.getKey()).matches()
						&& entry.getValue() instanceof JsonPrimitive id && id.isString()
						&& Payload.SKYBLOCK_ID.matcher(id.getAsString()).matches()) {
					saved.put(entry.getKey(), id.getAsString());
				}
			}
		} catch (IOException | JsonParseException failed) {
			CherryPicking.LOGGER.warn("Friend looks: could not read {}; it is written again from what is seen now. {}",
					file, failed.toString());
		}
		return saved;
	}

	/** Writes {@code ids} through a temporary file, so a crash never leaves half a file. Any thread. */
	static void write(Path file, Map<String, String> ids) {
		JsonObject out = new JsonObject();
		ids.forEach(out::addProperty);
		Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
				writer.write(out.toString());
			}
			Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException failed) {
			CherryPicking.LOGGER.warn("Friend looks: could not write {}.", file, failed);
		}
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
		if (!id.equals(ID_BY_UUID.put(uuid, id))) {
			changes++;
		}
	}
}
