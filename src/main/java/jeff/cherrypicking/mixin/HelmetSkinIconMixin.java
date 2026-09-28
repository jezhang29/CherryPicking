package jeff.cherrypicking.mixin;

import jeff.cherrypicking.client.cosmetics.HelmetSkins;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Draws the icon of your helmet with a skin as that player head: in the inventory, the hotbar and your
 * hand. Every item model is built here from the stack's {@code ITEM_MODEL}, and the player head model
 * reads the skin from {@code PROFILE}, which Skyblocker gives only to a player head. The count and the
 * durability bar are drawn from the real stack, not from this one.
 */
@Mixin(ItemModelResolver.class)
public abstract class HelmetSkinIconMixin {
	@ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true)
	private ItemStack cherrypicking$skinnedHelmetIcon(ItemStack stack) {
		return HelmetSkins.drawn(stack);
	}
}
