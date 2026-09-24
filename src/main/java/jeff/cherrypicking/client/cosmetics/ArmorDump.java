package jeff.cherrypicking.client.cosmetics;

import java.util.List;
import java.util.Optional;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.Chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * {@code /cherry debug armor <player>}: prints what Hypixel sends about one nearby player's armor.
 *
 * <p>Friend cosmetics look up a friend's armor by its SkyBlock UUID. That works only if Hypixel
 * sends the UUID on other players' armor, not only on your own. This command shows it, and the
 * answer picks Plan A or Plan B in {@code docs/friend-cosmetics-plan.md}. It also shows whether the
 * armor carries the vanilla dye component, which that plan's dye hook depends on.
 *
 * <p>It only reads. The full tags also go to the log, where they are easier to copy than from chat.
 * Client thread only.
 */
public final class ArmorDump {
	private static final List<EquipmentSlot> SLOTS =
			List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	private ArmorDump() {
	}

	/** The names of the players the client can see now, for the command's suggestions. */
	public static List<String> names() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? List.of()
				: level.players().stream().map(player -> player.getGameProfile().name()).toList();
	}

	public static void print(String name) {
		Optional<AbstractClientPlayer> found = find(name);
		if (found.isEmpty()) {
			Chat.note("Armor", "No player named " + name + " is near you.");
			return;
		}
		AbstractClientPlayer player = found.get();
		Chat.note("Armor", "Armor of " + player.getGameProfile().name() + ":");
		for (EquipmentSlot slot : SLOTS) {
			print(slot, player.getItemBySlot(slot));
		}
	}

	private static Optional<AbstractClientPlayer> find(String name) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return Optional.empty();
		}
		return level.players().stream()
				.filter(player -> player.getGameProfile().name().equalsIgnoreCase(name))
				.findFirst();
	}

	private static void print(EquipmentSlot slot, ItemStack stack) {
		String label = slot.getName().toUpperCase();
		if (stack.isEmpty()) {
			Chat.note("Armor", label + ": empty");
			return;
		}
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		String uuid = ItemUuid.of(tag);
		DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
		Chat.note("Armor", label + ": " + BuiltInRegistries.ITEM.getKey(stack.getItem())
				+ ", uuid " + (uuid.isEmpty() ? "NONE" : uuid)
				+ ", dye " + (dye == null ? "NONE" : String.format("#%06x", dye.rgb() & 0xFFFFFF)));
		CherryPicking.LOGGER.info("Armor: {} custom_data {}", label, tag);
		Chat.say(Component.literal(tag.toString()).withStyle(ChatFormatting.DARK_GRAY));
	}
}
