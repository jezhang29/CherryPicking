package jeff.cherrypicking.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import jeff.cherrypicking.client.config.ScreenOpener;
import jeff.cherrypicking.client.cosmetics.ArmorDump;
import jeff.cherrypicking.client.cosmetics.TestLooks;

import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * This mod's own way in.
 *
 * <p>{@code /cherry} opens the settings screen, and {@code /cherry config} does
 * the same for anyone who types the family's longer form. The shared hub and
 * ModMenu are additions on top: a player who installs this mod by itself, with
 * neither of those present, still reaches everything it has.
 *
 * <p>Player commands are for doing things. Settings belong to the screen, so no
 * subcommand here should ever exist only to set a value - that is what the
 * registry and {@code ConfigScreen} are for.
 *
 * <p>{@code /cherry debug ...} prints what the server sends, for questions only the live game can
 * answer. Debug commands only read.
 */
public final class CherryCommand {
	private CherryCommand() {
	}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(ClientCommands.literal("cherry")
				.executes(CherryCommand::config)
				.then(ClientCommands.literal("config")
						.executes(CherryCommand::config))
				.then(ClientCommands.literal("debug")
						.then(ClientCommands.literal("armor")
								.then(ClientCommands.argument("player", StringArgumentType.word())
										.suggests((context, builder) ->
												SharedSuggestionProvider.suggest(ArmorDump.names(), builder))
										.executes(CherryCommand::armor)))
						.then(ClientCommands.literal("looks")
								.executes(CherryCommand::looks))));
	}

	private static int config(CommandContext<FabricClientCommandSource> context) {
		// Parked rather than opened: chat is still closing itself and would
		// close the new screen with it. See ScreenOpener.
		ScreenOpener.request();
		return 1;
	}

	private static int armor(CommandContext<FabricClientCommandSource> context) {
		ArmorDump.print(StringArgumentType.getString(context, "player"));
		return 1;
	}

	private static int looks(CommandContext<FabricClientCommandSource> context) {
		TestLooks.load();
		return 1;
	}
}
