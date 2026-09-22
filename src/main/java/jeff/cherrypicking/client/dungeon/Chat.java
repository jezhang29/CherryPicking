package jeff.cherrypicking.client.dungeon;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The one way this mod puts a line in the player's chat.
 *
 * <p><b>Nothing here reaches the server.</b> {@code sendSystemMessage} writes into the client's
 * own chat box, the way a mod's own line appears; the mod never sends chat <i>as</i> the player,
 * which would be it acting for them. That is the reason this class exists rather than each
 * feature calling the player itself: one place to read, and one place a wrong call would show up.
 * See CLAUDE.md's display-only constraint.
 *
 * <p>Client thread only.
 */
public final class Chat {
	private Chat() {
	}

	/** A feature's own line, styled by the feature. Does nothing before the player exists. */
	public static void say(Component message) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player != null) {
			player.sendSystemMessage(message);
		}
	}

	/**
	 * A solver telling the player why it is not helping: the puzzle was already started, or its
	 * shape is not in the bundled file. Dimmed and named, because it is the mod talking about
	 * itself rather than about the fight.
	 *
	 * <p>It also goes to the log, so a player who has chat buried can still be asked what it said.
	 */
	public static void note(String feature, String message) {
		CherryPicking.LOGGER.info("{}: {}", feature, message);
		say(Component.literal(feature + ": ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(message).withStyle(ChatFormatting.GRAY)));
	}
}
