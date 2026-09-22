package jeff.cherrypicking.client.dungeon.draw;

import net.minecraft.world.phys.AABB;

/**
 * One box, meshed once: six shaded faces and twelve edges.
 *
 * <p>Built on the client thread when a feature publishes its {@link Marks}, and
 * never rebuilt; the render thread only reads the arrays. A moving entity's box
 * is the one exception, meshed per frame by {@link MarkRenderer} so it does not
 * lag the mob.
 *
 * <p>Copied from coalroutegenerator's {@code glacite.BoxMesh}.
 *
 * @param quads flat {@code x,y,z} per corner, four corners per face, in
 *     {@link DrawKit}'s face order
 * @param edges flat {@code x1,y1,z1,x2,y2,z2} per edge
 */
public record BoxMesh(float[] quads, float[] edges) {

	public static BoxMesh of(AABB box) {
		float x0 = (float) box.minX;
		float y0 = (float) box.minY;
		float z0 = (float) box.minZ;
		float x1 = (float) box.maxX;
		float y1 = (float) box.maxY;
		float z1 = (float) box.maxZ;

		// Each face winds counter-clockwise seen from outside the box, so a culled
		// render type draws only the faces turned to the camera.
		float[] quads = {
				// down, up
				x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
				x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
				// north, south
				x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
				x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
				// west, east
				x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
				x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
		};

		float[] edges = {
				// The bottom square.
				x0, y0, z0, x1, y0, z0,
				x1, y0, z0, x1, y0, z1,
				x1, y0, z1, x0, y0, z1,
				x0, y0, z1, x0, y0, z0,
				// The top square.
				x0, y1, z0, x1, y1, z0,
				x1, y1, z0, x1, y1, z1,
				x1, y1, z1, x0, y1, z1,
				x0, y1, z1, x0, y1, z0,
				// The four uprights.
				x0, y0, z0, x0, y1, z0,
				x1, y0, z0, x1, y1, z0,
				x1, y0, z1, x1, y1, z1,
				x0, y0, z1, x0, y1, z1,
		};

		return new BoxMesh(quads, edges);
	}

	/** Face {@code index} is its own shade index; see {@link DrawKit#SHADE}. */
	public float shade(int index) {
		return DrawKit.SHADE[index];
	}
}
