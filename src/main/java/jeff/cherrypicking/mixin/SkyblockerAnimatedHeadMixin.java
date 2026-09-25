package jeff.cherrypicking.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import jeff.cherrypicking.CherryPicking;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets Skyblocker's customize screen save an animated helmet (Skyblocker 6.10.4 bug, plan section 2).
 *
 * <p>{@code HeadSelectionWidget.updateConfig} switches on the clicked button. Its first case,
 * {@code button == noneButton || button.texture == null}, also matches an animated head button,
 * which has no texture, so the click removes the helmet's look and the {@code AnimatedHeadButton}
 * case never runs. Only the none button and animated buttons have no texture, and the none button is
 * matched first, so a null here means an animated button. Giving the guard a non-null value lets the
 * switch go on to the animated case, which saves the head.
 *
 * <p>{@code @Pseudo} and {@code require = 0}: without Skyblocker, or after Skyblocker changes this
 * method, nothing is patched and the game still starts.
 */
@Pseudo
@Mixin(targets = "de.hysky.skyblocker.skyblock.item.custom.screen.HeadSelectionWidget")
public abstract class SkyblockerAnimatedHeadMixin {
	@ModifyExpressionValue(
			method = "lambda$updateConfig$0",
			at = @At(value = "FIELD", ordinal = 0,
					target = "Lde/hysky/skyblocker/skyblock/item/custom/screen/HeadSelectionWidget$HeadButton;texture:Ljava/lang/String;"),
			require = 0)
	private String cherrypicking$animatedHeadIsNotNone(String texture) {
		if (texture != null) {
			return texture;
		}
		CherryPicking.LOGGER.info("Skyblocker fix: saving the animated helmet you chose.");
		return "";
	}
}
