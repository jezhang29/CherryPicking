package jeff.cherrypicking.client.dungeon.draw;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * The vertex writers every mark shares: fill, lines, box and label.
 *
 * <p>Nothing here reads a setting, a location or a gate. It is given geometry
 * and a colour and writes vertices. No allocation per call beyond the lambdas
 * the collector needs; the geometry arrays were meshed on the client thread.
 *
 * <p>After coalroutegenerator's {@code glacite.GlaciteDraw}.
 */
public final class DrawKit {
	/** Vanilla's directional shading, so a translucent fill reads as a solid. */
	public static final float[] SHADE = {0.5f, 1.0f, 0.8f, 0.8f, 0.6f, 0.6f};

	/** Face order used by {@link BoxMesh}: down, up, north, south, west, east. */
	public static final int FACES = 6;

	/** Box edges are this wide. */
	public static final float EDGE_WIDTH = 2.0f;

	private static final int FULL_BRIGHT = 0xF000F0;

	/** Past this a label is scaled with distance, so it holds its size on screen. */
	private static final double LABEL_REFERENCE_DISTANCE = 12.0;
	private static final double LABEL_MAX_SCALE = 6.0;

	/** What a caller's own scale is allowed to be; the settings sliders use these. */
	public static final double MIN_LABEL_SCALE = 0.25;
	public static final double MAX_LABEL_SCALE = 4.0;

	/** How bright a quad of a mesh is, in {@code [0,1]}. */
	@FunctionalInterface
	public interface Shading {
		float of(int quad);
	}

	private DrawKit() {
	}

	/**
	 * A box in {@code style}. The pose must already be in world coordinates.
	 *
	 * @param argb the outline's colour
	 * @param fill the inside's colour, with its own alpha
	 * @param throughWalls false to let terrain hide it
	 */
	public static void box(PoseStack poseStack, OrderedSubmitNodeCollector collector, BoxMesh mesh,
			int argb, int fill, Style style, boolean throughWalls) {
		// A clear fill is an outline-only box, so skip the geometry rather than draw nothing.
		if (style.fill() && fill >>> 24 > 0) {
			collector.submitCustomGeometry(poseStack, CherryRenderTypes.quads(throughWalls),
					(pose, out) -> fill(pose, out, mesh.quads(), FACES, mesh::shade, fill));
		}
		if (style.outline()) {
			collector.submitCustomGeometry(poseStack, CherryRenderTypes.lines(throughWalls),
					(pose, out) -> lines(pose, out, mesh.edges(), argb, EDGE_WIDTH));
		}
	}

	/**
	 * Translucent faces.
	 *
	 * @param vertices flat {@code x,y,z} per corner, four corners per quad, world
	 *     coordinates
	 * @param quads how many quads {@code vertices} holds
	 * @param shade the per-quad brightness
	 * @param argb the colour; its alpha is the opacity
	 */
	public static void fill(PoseStack.Pose pose, VertexConsumer out, float[] vertices, int quads,
			Shading shade, int argb) {
		int alpha = argb & 0xFF000000;
		for (int quad = 0; quad < quads; quad++) {
			float brightness = shade.of(quad);
			int shaded = alpha
					| (channel(((argb >> 16) & 0xFF) * brightness) << 16)
					| (channel(((argb >> 8) & 0xFF) * brightness) << 8)
					| channel((argb & 0xFF) * brightness);

			int at = quad * 12;
			for (int corner = 0; corner < 4; corner++) {
				out.addVertex(pose, vertices[at + corner * 3], vertices[at + corner * 3 + 1],
						vertices[at + corner * 3 + 2]).setColor(shaded);
			}
		}
	}

	private static int channel(float value) {
		return Math.clamp(Math.round(value), 0, 255);
	}

	/** @param segments flat {@code x1,y1,z1,x2,y2,z2} per segment, world coordinates */
	public static void lines(PoseStack.Pose pose, VertexConsumer out, float[] segments, int argb,
			float width) {
		for (int at = 0; at + 5 < segments.length; at += 6) {
			line(pose, out, segments[at], segments[at + 1], segments[at + 2],
					segments[at + 3], segments[at + 4], segments[at + 5], argb, width);
		}
	}

	/**
	 * One line segment.
	 *
	 * <p>The format is {@code POSITION_COLOR_NORMAL_LINE_WIDTH} - four attributes,
	 * every one of which must be written or the frame throws. The normal is the
	 * direction of travel: the shader widens the line across it, so a zero normal
	 * draws nothing, which is why a degenerate segment is dropped rather than
	 * written.
	 */
	public static void line(PoseStack.Pose pose, VertexConsumer out,
			float x1, float y1, float z1, float x2, float y2, float z2, int argb, float width) {
		float nx = x2 - x1;
		float ny = y2 - y1;
		float nz = z2 - z1;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0.0f) {
			return;
		}

		nx /= length;
		ny /= length;
		nz /= length;

		out.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, nx, ny, nz)
				.setLineWidth(width);
		out.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, nx, ny, nz)
				.setLineWidth(width);
	}

	/** One segment between two world points. {@code throughWalls} false lets terrain hide it. */
	public static void segment(PoseStack poseStack, OrderedSubmitNodeCollector collector,
			Vec3 from, Vec3 to, int argb, float width, boolean throughWalls) {
		collector.submitCustomGeometry(poseStack, CherryRenderTypes.lines(throughWalls),
				(pose, out) -> line(pose, out, (float) from.x, (float) from.y, (float) from.z,
						(float) to.x, (float) to.y, (float) to.z, argb, width));
	}

	/**
	 * A name tag at {@code at}, held at a readable size however far off it is.
	 *
	 * @param text carries its own colour, in its style
	 * @param scale a multiplier on the size distance already picked; 1 is that size
	 */
	public static void label(PoseStack poseStack, OrderedSubmitNodeCollector collector,
			CameraRenderState camera, Vec3 at, Component text, double scale) {
		double distance = camera.pos.distanceTo(at);
		double picked = Math.clamp(distance / LABEL_REFERENCE_DISTANCE, 1.0, LABEL_MAX_SCALE);
		double scaled = picked * Math.clamp(scale, MIN_LABEL_SCALE, MAX_LABEL_SCALE);

		// submitNameTag translates by the Vec3 itself, so the scale has to go in
		// around it: pre-scale, then hand it the position divided by the scale.
		poseStack.pushPose();
		poseStack.scale((float) scaled, (float) scaled, (float) scaled);
		collector.submitNameTag(poseStack, at.scale(1.0 / scaled), 0, text, true, FULL_BRIGHT, camera);
		poseStack.popPose();
	}
}
