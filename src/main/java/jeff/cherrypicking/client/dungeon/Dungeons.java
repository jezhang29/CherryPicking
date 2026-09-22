package jeff.cherrypicking.client.dungeon;

import jeff.cherrypicking.client.dungeon.boss.Livid;
import jeff.cherrypicking.client.dungeon.boss.LividTitle;
import jeff.cherrypicking.client.dungeon.draw.MarkRenderer;
import jeff.cherrypicking.client.dungeon.mob.StarMobRenderer;
import jeff.cherrypicking.client.dungeon.mob.StarMobWatch;
import jeff.cherrypicking.client.dungeon.puzzle.Puzzles;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * The one line the client entrypoint knows about the dungeon layer.
 *
 * <p>Everything the layer registers, registers here. Adding a feature adds a
 * line; deleting the layer is deleting this package and the one call to it.
 * The gate is {@link DungeonState}: outside the Catacombs every listener here
 * is one volatile read and a return.
 */
public final class Dungeons {
	private Dungeons() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DungeonState::tick);
		ClientTickEvents.END_CLIENT_TICK.register(RoomWatch::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Puzzles::tick);
		ClientTickEvents.END_CLIENT_TICK.register(Livid::tick);
		ClientTickEvents.END_CLIENT_TICK.register(StarMobWatch::tick);
		Puzzles.register();
		MarkRenderer.register();
		StarMobRenderer.register();
		LividTitle.register();
	}
}
