package jeff.cherrypicking.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import jeff.cherrypicking.client.cosmetics.Looks;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives a friend's skull helmet its skin, and draws a helmet with a skin (a friend's or your own) as
 * a skull. The head slot's stack sets the render state's {@code wornHeadType} and
 * {@code wornHeadProfile}, which the skull layer draws. This is the only {@code getItemBySlot} call in
 * that method.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class FriendHeadMixin {
	@ModifyExpressionValue(
			method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack cherrypicking$friendHead(ItemStack original, @Local(argsOnly = true) LivingEntity entity) {
		return Looks.styled(entity, EquipmentSlot.HEAD, original);
	}
}
