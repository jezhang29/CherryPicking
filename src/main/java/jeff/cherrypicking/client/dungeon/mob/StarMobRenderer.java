package jeff.cherrypicking.client.dungeon.mob;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.draw.BoxMesh;
import jeff.cherrypicking.client.dungeon.draw.CherryRenderTypes;
import jeff.cherrypicking.client.dungeon.draw.DrawKit;
import jeff.cherrypicking.client.theme.Theme;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the mobs {@link StarMobWatch} found.
 *
 * <p>Built to the shape of coalroutegenerator's {@code glacite.CorpseRenderer}:
 * its own {@code COLLECT_SUBMITS} listener, six shaded quads and twelve edges,
 * and a label with the name and the distance. All of it draws through terrain:
 * a starred mob is usually round a corner, and a depth-tested box shows up only
 * once you can already see it.
 *
 * <p><b>The opacity is the colour's own.</b> Each {@code mobs.*Colour} carries
 * an outline opacity and a fill opacity, set on the two sliders in its colour
 * picker, the same split {@link DrawKit#box} makes everywhere else. So there is
 * no separate opacity setting to keep in step, and one kind of mob can be
 * louder than another.
 *
 * <p>The one difference from a corpse: a mob moves, so its box is taken from
 * where the entity is this frame and meshed per frame. A room holds a handful.
 *
 * <p>Nothing is depth-tested, so the draw order decides which box is in front.
 * Each box gets its own submit order, farthest first, which puts a near box and
 * its edges over a far one when they overlap on screen.
 */
public final class StarMobRenderer {
	/** The last submit orders, one per box; the nearest box takes the very last. */
	private static final int ORDER_LAST = Integer.MAX_VALUE;

	/**
	 * Milder than {@link DrawKit#SHADE}: enough to show the box's shape, without
	 * turning the side faces of a dark colour darker still.
	 */
	private static final float[] SHADE = {0.8f, 1.0f, 0.92f, 0.92f, 0.85f, 0.85f};

	/** Widen the mob's own box a little, so the edges sit just outside the model. */
	private static final double WIDEN = 0.1;

	/** How far above the box the label floats. */
	private static final double LABEL_LIFT = 0.4;

	private StarMobRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(StarMobRenderer::collect);
	}

	private static void collect(LevelRenderContext context) {
		StarMob[] mobs = StarMobWatch.mobs();
		if (mobs.length == 0 || !DungeonState.inCatacombs()) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 eye = camera.pos;
		PoseStack poseStack = context.poseStack();
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		boolean labels = StarMobWatch.labels();

		poseStack.pushPose();
		// The level pose is identity at the camera; bring world coordinates back to it.
		poseStack.translate(-eye.x, -eye.y, -eye.z);

		List<Drawn> drawn = new ArrayList<>(mobs.length);
		for (StarMob mob : mobs) {
			Entity entity = level.getEntity(mob.mobId());
			if (entity == null || entity.isRemoved()) {
				continue;
			}
			// Where the entity is this frame, not where it was last tick.
			Vec3 at = entity.getPosition(partialTick);
			AABB box = entity.getBoundingBox().move(at.subtract(entity.position()))
					.inflate(WIDEN, 0.0, WIDEN);
			drawn.add(new Drawn(mob, box, box.getCenter().distanceToSqr(eye)));
		}
		drawn.sort(Comparator.comparingDouble(Drawn::distanceSqr).reversed());

		for (int i = 0; i < drawn.size(); i++) {
			StarMob mob = drawn.get(i).mob();
			AABB box = drawn.get(i).box();
			OrderedSubmitNodeCollector collector = context.submitNodeCollector()
					.order(ORDER_LAST - (drawn.size() - 1 - i));
			BoxMesh mesh = BoxMesh.of(box);
			int edge = Theme.resolve(mob.kind().colour());
			int fill = Theme.resolveFill(mob.kind().colour());

			if (fill >>> 24 > 0) {
				collector.submitCustomGeometry(poseStack, CherryRenderTypes.quadsThroughWalls(),
						(pose, out) -> DrawKit.fill(pose, out, mesh.quads(), DrawKit.FACES,
								face -> SHADE[face], fill));
			}
			collector.submitCustomGeometry(poseStack, CherryRenderTypes.linesThroughWalls(),
					(pose, out) -> DrawKit.lines(pose, out, mesh.edges(), edge, DrawKit.EDGE_WIDTH));

			if (labels) {
				Vec3 top = new Vec3((box.minX + box.maxX) / 2.0, box.maxY + LABEL_LIFT,
						(box.minZ + box.maxZ) / 2.0);
				String text = mob.name() + "  " + Math.round(eye.distanceTo(top)) + "m";
				DrawKit.label(poseStack, collector, camera, top, Component.literal(text), 1.0);
			}
		}

		poseStack.popPose();
	}

	private record Drawn(StarMob mob, AABB box, double distanceSqr) {
	}
}
