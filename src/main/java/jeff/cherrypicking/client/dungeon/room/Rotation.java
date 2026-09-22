package jeff.cherrypicking.client.dungeon.room;

import net.minecraft.core.BlockPos;

/**
 * The four ways a dungeon room can face.
 *
 * <p>Odin's {@code RoomRotation} and {@code VecUtils.rotateAroundNorth}: each
 * rotation names the corner its anchor sits in, as an offset from the room's
 * centre, and a turn of the room-relative {@code x} and {@code z}. {@code y} is
 * never turned; dungeon heights are absolute. See docs/dungeon-layer.md §3.1.
 */
public enum Rotation {
	NORTH(15, 15),
	SOUTH(-15, -15),
	WEST(15, -15),
	EAST(-15, 15);

	private final int dx;
	private final int dz;

	Rotation(int dx, int dz) {
		this.dx = dx;
		this.dz = dz;
	}

	/** The anchor corner's x offset from the room centre. */
	public int dx() {
		return dx;
	}

	/** The anchor corner's z offset from the room centre. */
	public int dz() {
		return dz;
	}

	/** Room-relative to a world offset from the anchor. */
	public BlockPos rotate(int x, int y, int z) {
		return switch (this) {
			case NORTH -> new BlockPos(-x, y, -z);
			case SOUTH -> new BlockPos(x, y, z);
			case WEST -> new BlockPos(-z, y, x);
			case EAST -> new BlockPos(z, y, -x);
		};
	}

	/** The inverse of {@link #rotate}: the WEST and EAST rows swap. */
	public BlockPos unrotate(BlockPos pos) {
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		return switch (this) {
			case NORTH -> new BlockPos(-x, y, -z);
			case SOUTH -> new BlockPos(x, y, z);
			case WEST -> new BlockPos(z, y, -x);
			case EAST -> new BlockPos(-z, y, x);
		};
	}
}
