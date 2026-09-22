package jeff.cherrypicking.mixin;

import com.mojang.blaze3d.platform.ClientShutdownWatchdog;

import jeff.cherrypicking.client.CleanExit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands the normal end of {@code Main} to {@link CleanExit}.
 *
 * <p>{@code Main} calls {@code startShutdownWatchdog("post-main", ...)} as its
 * last act after a clean close; the crash paths return before it. Injecting at
 * the tail leaves vanilla's watchdog running, so a shutdown hook that hangs is
 * no worse off than without this mod.
 */
@Mixin(ClientShutdownWatchdog.class)
public abstract class ShutdownWatchdogMixin {
	@Inject(method = "startShutdownWatchdog", at = @At("TAIL"))
	private static void cherrypicking$exitAfterMain(String name, boolean exitOnTimeout, Minecraft minecraft,
			GameConfig config, long mainThreadId, CallbackInfo ci) {
		if ("post-main".equals(name)) {
			CleanExit.afterMain();
		}
	}
}
