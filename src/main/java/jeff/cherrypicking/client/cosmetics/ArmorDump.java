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
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * {@code /cherry debug armor <player>}: prints what Hypixel sends about one nearby player's armor.
 *
 * <p>Friend cosmetics depend on what Hypixel sends about other players' armor. This command shows
 * it: the answer, no uuid and only the SkyBlock id, picked Plan B in
 * {@code docs/friend-cosmetics-plan.md}. It also shows the vanilla dye, and, for a player with friend
 * looks, which look matches each piece.
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
		Payload payload = FriendLooks.of(player.getUUID());
		for (EquipmentSlot slot : SLOTS) {
			print(player, slot, player.getItemBySlot(slot), payload);
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

	private static void print(AbstractClientPlayer player, EquipmentSlot slot, ItemStack stack, Payload payload) {
		String label = slot.getName().toUpperCase();
		if (stack.isEmpty()) {
			Chat.note("Armor", label + ": empty");
			return;
		}
		CompoundTag tag = SkyblockItem.tag(stack);
		String uuid = SkyblockItem.uuid(tag);
		DyedItemColor dye = stack.get(DataComponents.DYED_COLOR);
		String look = payload == null ? ""
				: ", look " + payload.look(slot, SkyblockItem.id(tag)).map(ArmorDump::describe).orElse("NONE");
		Chat.note("Armor", label + ": " + BuiltInRegistries.ITEM.getKey(stack.getItem())
				+ ", uuid " + (uuid.isEmpty() ? "NONE" : uuid)
				+ ", dye " + (dye == null ? "NONE" : hex(dye.rgb())) + skin(stack) + look + drawn(player, slot, stack));
		CherryPicking.LOGGER.info("Armor: {} custom_data {}", label, tag);
		Chat.say(Component.literal(tag.toString()).withStyle(ChatFormatting.DARK_GRAY));
	}

	/**
	 * What the game draws for this piece: the real item, or the styled copy of a friend look. The
	 * copy's skin and dye are read as the renderer reads them, so another mod's change shows here.
	 */
	private static String drawn(AbstractClientPlayer player, EquipmentSlot slot, ItemStack stack) {
		ItemStack drawn = Looks.styled(player, slot, stack);
		if (drawn == stack) {
			return ", drawn: the real item";
		}
		DyedItemColor dye = drawn.get(DataComponents.DYED_COLOR);
		return ", drawn: a copy, uuid " + (SkyblockItem.uuid(SkyblockItem.tag(drawn)).isEmpty() ? "NONE" : "KEPT")
				+ ", dye " + (dye == null ? "NONE" : hex(dye.rgb())) + skin(drawn);
	}

	/** A head's skin as the game reads it, by the start of its texture hash; empty for other items. */
	private static String skin(ItemStack stack) {
		ResolvableProfile profile = stack.get(DataComponents.PROFILE);
		if (profile == null) {
			return "";
		}
		return ", skin " + profile.partialProfile().properties().get("textures").stream().findFirst()
				.flatMap(property -> Payload.skinUrl(property.value()))
				.map(ArmorDump::hash)
				.orElse("NONE");
	}

	/** The first 8 characters of a skin URL's hash, enough to tell two skins apart. */
	private static String hash(String url) {
		String name = url.substring(url.lastIndexOf('/') + 1);
		return name.substring(0, Math.min(8, name.length()));
	}

	/** The look's fields, short enough for chat: a skin shows by the start of its hash. */
	private static String describe(Cosmetic look) {
		StringBuilder text = new StringBuilder(look.id());
		look.dye().ifPresent(rgb -> text.append(" dye ").append(hex(rgb)));
		look.trim().ifPresent(trim -> text.append(" trim ").append(trim.material()).append('/').append(trim.pattern()));
		look.helmetTexture().ifPresent(texture -> text.append(" skin ")
				.append(Payload.skinUrl(texture).map(ArmorDump::hash).orElse("?")));
		look.glint().ifPresent(glint -> text.append(" glint ").append(glint));
		return text.toString();
	}

	private static String hex(int rgb) {
		return String.format("#%06x", rgb & 0xFFFFFF);
	}
}
