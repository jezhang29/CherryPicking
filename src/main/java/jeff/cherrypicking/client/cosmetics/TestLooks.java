package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import jeff.cherrypicking.client.dungeon.Chat;
import jeff.cherrypicking.client.dungeon.DungeonState;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Step 1 scaffolding (docs/friend-cosmetics-plan.md, section 11): {@code /cherry debug looks} loads
 * {@code config/cherrypicking-test-looks.json} as your own player's payload. That tests the whole
 * drawing path on yourself, with no relay. Step 4 removes this class, when the poller fills
 * {@link FriendLooks}.
 *
 * <p>With no file, it clears the looks. Client thread only.
 */
public final class TestLooks {
	private static final String FILE = "cherrypicking-test-looks.json";

	private TestLooks() {
	}

	public static void load() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		Path file = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		if (!Files.isRegularFile(file)) {
			FriendLooks.replace(Map.of());
			Chat.note("Looks", "No " + FILE + " in the config folder. Test looks cleared.");
			return;
		}

		Optional<Payload> payload;
		try {
			payload = Payload.decode(Files.readString(file, StandardCharsets.UTF_8), file.toString());
		} catch (IOException failed) {
			Chat.note("Looks", "Could not read " + FILE + ": " + failed.getMessage());
			return;
		}
		if (payload.isEmpty()) {
			Chat.note("Looks", FILE + " is not usable. The log says why.");
			return;
		}

		FriendLooks.replace(Map.of(player.getUUID(), payload.get()));
		Chat.note("Looks", "Loaded " + payload.get().looks().size() + " looks for "
				+ player.getGameProfile().name() + ". Press F5 to see them."
				+ (DungeonState.inSkyBlock() ? "" : " Looks show only on SkyBlock."));
	}
}
