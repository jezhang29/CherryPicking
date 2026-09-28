package jeff.cherrypicking.mixin.skyblocker;

import java.util.Arrays;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import jeff.cherrypicking.client.cosmetics.HelmetSkins;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets Skyblocker's customize screen give a head skin to a helmet that is not a skull (plan section
 * 2). Skyblocker 6.10.4 shows its head picker only for a player head, and lets you edit only a player
 * head or trimmable armor, so a Lapis helmet (a sea lantern) cannot be edited at all.
 *
 * <p>A helmet that is not trimmable gets the head picker only. A trimmable one, such as a leather
 * helmet, gets a button under the armor slots that changes between the head picker and Skyblocker's
 * dye, trim and model editors. Skyblocker saves the skin under the helmet's uuid as for a skull, and
 * {@code HelmetSkins} draws it.
 *
 * <p>In {@code cherrypicking-skyblocker.mixins.json}, which is not required, with no injector
 * required: if Skyblocker is missing or renames a member used here, Mixin logs a warning, leaves the
 * screen as Skyblocker made it, and the game still starts.
 */
@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.item.custom.screen.ArmorTab")
public abstract class SkyblockerHelmetSkinMixin {
	@Shadow
	@Final
	private ItemStack[] armor;

	/** The button that changes between the skin and the armor editors; null when no piece needs it. */
	@Unique
	private Button cherrypicking$modeButton;
	/** Whether the head picker shows for {@link #cherrypicking$modeFor}. */
	@Unique
	private boolean cherrypicking$skin;
	/** The piece that {@link #cherrypicking$skin} was chosen for. */
	@Unique
	private ItemStack cherrypicking$modeFor;

	@Shadow
	private void updateWidgets() {
	}

	@ModifyReturnValue(method = "canEdit", at = @At("RETURN"))
	private static boolean cherrypicking$helmetCanHaveSkin(boolean editable, ItemStack stack) {
		return editable || HelmetSkins.canHaveSkin(stack);
	}

	/** Adds the mode button under the armor slots, the second child of the left column. */
	@WrapOperation(method = "<init>", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/gui/layouts/LinearLayout;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;)Lnet/minecraft/client/gui/layouts/LayoutElement;"))
	private LayoutElement cherrypicking$addModeButton(LinearLayout column, LayoutElement slots,
			Operation<LayoutElement> original) {
		LayoutElement added = original.call(column, slots);
		// Added only when needed, because a hidden widget still takes space in the layout.
		if (Arrays.stream(armor).anyMatch(SkyblockerHelmetSkinMixin::cherrypicking$skinAndArmor)) {
			cherrypicking$modeButton = column.addChild(Button.builder(Component.empty(), button -> {
				cherrypicking$skin = !cherrypicking$skin;
				updateWidgets();
			}).width(slots.getWidth()).tooltip(Tooltip.create(Component.literal(
					"Change between the helmet's head skin and its dye and trim. "
							+ "A skin replaces the helmet. The dye and trim stay saved.")))
					.build());
		}
		return added;
	}

	/**
	 * Replaces Skyblocker's "is a player head" test, which decides whether the head picker or the armor
	 * editors show for the chosen piece.
	 */
	@WrapOperation(method = "updateWidgets", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
	private boolean cherrypicking$showHeadPicker(ItemStack piece, Object playerHead, Operation<Boolean> original) {
		boolean skull = original.call(piece, playerHead);
		boolean both = cherrypicking$skinAndArmor(piece);
		if (cherrypicking$modeButton != null) {
			cherrypicking$modeButton.visible = both;
		}
		if (skull || !HelmetSkins.canHaveSkin(piece)) {
			return skull;
		}
		if (both && cherrypicking$modeButton == null) {
			// Without the button there is no way back to the armor editors.
			return false;
		}
		if (piece != cherrypicking$modeFor) {
			cherrypicking$modeFor = piece;
			cherrypicking$skin = !both || HelmetSkins.hasSkin(piece);
		}
		if (both) {
			cherrypicking$modeButton.setMessage(Component.literal(cherrypicking$skin ? "Head skin" : "Dye and trim"));
		}
		return cherrypicking$skin;
	}

	/** A helmet that can have a skin and is also trimmable armor, so both editors apply. */
	@Unique
	private static boolean cherrypicking$skinAndArmor(ItemStack piece) {
		return HelmetSkins.canHaveSkin(piece) && piece.is(ItemTags.TRIMMABLE_ARMOR);
	}
}
