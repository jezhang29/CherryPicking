package jeff.cherrypicking.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import jeff.cherrypicking.client.cosmetics.Looks;

import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives a friend's worn armor its look. {@code getEquipmentIfRenderable} fills the armor stacks of
 * the render state, which the armor layer draws from: dye, trim, glint and model. Players reach it
 * through {@code AvatarRenderer}.
 */
@Mixin(HumanoidMobRenderer.class)
public abstract class FriendArmorMixin {
	@ModifyReturnValue(method = "getEquipmentIfRenderable", at = @At("RETURN"))
	private static ItemStack cherrypicking$friendArmor(ItemStack original, LivingEntity entity, EquipmentSlot slot) {
		return Looks.styled(entity, slot, original);
	}
}
