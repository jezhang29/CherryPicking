package jeff.cherrypicking.client.dungeon.room;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * A room's coordinate frame: an anchor corner plus a rotation. {@code y} is
 * always absolute.
 *
 * <p>Every position a solver names is room-relative, in {@code 0..31}, as Odin
 * writes it. This turns those into world positions and back.
 */
public record RoomFrame(int anchorX, int anchorZ, Rotation rotation) {

	/** The frame of the room centred on {@code (centreX, centreZ)}, facing {@code rotation}. */
	public static RoomFrame at(int centreX, int centreZ, Rotation rotation) {
		return new RoomFrame(centreX + rotation.dx(), centreZ + rotation.dz(), rotation);
	}

	/** Rotate, then offset by the anchor. */
	public BlockPos real(int x, int y, int z) {
		return rotation.rotate(x, y, z).offset(anchorX, 0, anchorZ);
	}

	/** The inverse of {@link #real}. */
	public BlockPos relative(BlockPos world) {
		return rotation.unrotate(world.offset(-anchorX, 0, -anchorZ));
	}

	/** The unit cube at {@code real(x, y, z)}. */
	public AABB realBox(int x, int y, int z) {
		return new AABB(real(x, y, z));
	}
}
