package jeff.cherrypicking.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;

import jeff.cherrypicking.client.cosmetics.AnimatedHeads;

import net.minecraft.world.item.component.ResolvableProfile;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops your own animated helmet from flickering through default skins the first time it is drawn in
 * a session. Skyblocker's {@code animateHeadTexture} picks the frame to draw now, and the game draws
 * Steve or Alex until that frame's skin is loaded; {@link AnimatedHeads#steady} loads all frames at
 * once and keeps the last loaded frame until then.
 *
 * <p>{@code @Pseudo} and {@code require = 0}: without Skyblocker, or after Skyblocker changes this
 * method, nothing is patched and the game still starts.
 */
@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.item.custom.CustomAnimatedHelmetTextures")
public abstract class SkyblockerAnimatedFrameMixin {
	@ModifyReturnValue(method = "animateHeadTexture", at = @At("RETURN"), require = 0)
	private static ResolvableProfile cherrypicking$steadyFrame(ResolvableProfile frame,
			@Local(argsOnly = true) String id) {
		return AnimatedHeads.steady(id, frame);
	}
}
