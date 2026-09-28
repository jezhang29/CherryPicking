package jeff.cherrypicking.client.cosmetics;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * A head skin on a helmet that is not a skull, such as a leather helmet or a sea lantern. Skyblocker
 * saves the skin (plan section 2), but draws it only on a player head: the game picks the skull
 * drawing by the item type, not by a component. So a helmet with a skin is drawn as a player head.
 *
 * <p>Render thread only.
 */
public final class HelmetSkins {
	/**
	 * The player head made for each of your helmets, or {@link ItemStack#EMPTY} for a stack that cannot
	 * have a skin. Weak keys: a stack the server replaced drops out. {@code ItemStack} has no
	 * {@code equals}, so the stack object is the key.
	 */
	private static final Map<ItemStack, ItemStack> OWN = new WeakHashMap<>();

	private HelmetSkins() {
	}

	/**
	 * Whether Skyblocker's customize screen may give {@code stack} a head skin: one of your SkyBlock
	 * items, worn on the head, and not a skull already. A sea lantern has no {@code EQUIPPABLE}; the
	 * server puts it in the head slot.
	 */
	public static boolean canHaveSkin(ItemStack stack) {
		if (stack.isEmpty() || stack.is(Items.PLAYER_HEAD) || SkyblockItem.uuid(SkyblockItem.tag(stack)).isEmpty()) {
			return false;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable == null || equippable.slot() == EquipmentSlot.HEAD;
	}

	/**
	 * Whether Skyblocker has a skin, plain or animated, for your {@code helmet}. Skyblocker gives the
	 * skin to a player head with the helmet's custom data, so its saved and unsaved choices both show.
	 */
	public static boolean hasSkin(ItemStack helmet) {
		return skull(helmet).get(DataComponents.PROFILE) != null;
	}

	/**
	 * A player head with {@code helmet}'s custom data and no skin. The custom data keeps the SkyBlock
	 * id and uuid, so Skyblocker and {@code /cherry debug armor} see the same item. A skull drawn on
	 * the head has no glint, so the glint is not copied.
	 */
	static ItemStack skull(ItemStack helmet) {
		ItemStack skull = new ItemStack(Items.PLAYER_HEAD);
		skull.copyFrom(DataComponents.CUSTOM_DATA, helmet);
		return skull;
	}

	/**
	 * The stack to draw on your own head: a player head when Skyblocker has a skin for the helmet,
	 * otherwise {@code stack}. Also covers the preview player in Skyblocker's customize screen, so a
	 * skin shows there the moment it is picked. Other players' helmets have no uuid, so they never
	 * match.
	 */
	static ItemStack own(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
		if (slot != EquipmentSlot.HEAD || !(entity instanceof Player) || stack.isEmpty() || stack.is(Items.PLAYER_HEAD)) {
			return stack;
		}
		ItemStack skull = OWN.computeIfAbsent(stack, helmet -> canHaveSkin(helmet) ? skull(helmet) : ItemStack.EMPTY);
		// Asked each frame: Skyblocker's skin changes the moment a head is picked, and an animated
		// head changes its frame. Without a skin, the game would draw Steve.
		return skull.get(DataComponents.PROFILE) != null ? skull : stack;
	}
}
