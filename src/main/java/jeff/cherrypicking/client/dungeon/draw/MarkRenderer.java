package jeff.cherrypicking.client.dungeon.draw;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.mojang.blaze3d.vertex.PoseStack;

import jeff.cherrypicking.client.dungeon.DungeonState;
import jeff.cherrypicking.client.dungeon.boss.Livid;
import jeff.cherrypicking.client.dungeon.puzzle.Puzzles;
import jeff.cherrypicking.client.dungeon.room.RoomWatch;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws every feature's {@link Marks}, from one {@code COLLECT_SUBMITS} listener.
 *
 * <p>Odin gives every solver its own render hook. Here a feature publishes a
 * snapshot and this draws it, so <b>a new feature writes no render code</b> - it
 * adds one line to {@link #SOURCES}.
 *
 * <p>Outside the Catacombs a frame costs one volatile read. Inside, it costs one
 * read per source and returns when they are all empty. Nothing is allocated per
 * frame except a moving entity's box and a tracer's start point.
 */
public final class MarkRenderer {
	private static final int ORDER_LAST = Integer.MAX_VALUE;

	/** How far in front of the eye a tracer starts, so it is not a dot at the centre of the view. */
	private static final double TRACER_START = 0.5;

	/** Always, for the sources that draw through terrain whatever the settings say. */
	private static final BooleanSupplier ALWAYS = () -> true;

	/**
	 * One feature that draws, and whether terrain may hide it.
	 *
	 * @param throughWalls read once per frame per source
	 */
	private record Source(Supplier<Marks> marks, BooleanSupplier throughWalls) {
	}

	/** Every feature that draws. Adding a feature adds a line here. */
	private static final List<Source> SOURCES = List.of(
			new Source(RoomWatch::marks, ALWAYS),
			new Source(Livid::marks, ALWAYS),
			new Source(Puzzles::marks, Puzzles::throughWalls));

	private MarkRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(MarkRenderer::collect);
	}

	private static void collect(LevelRenderContext context) {
		if (!DungeonState.inCatacombs()) {
			return;
		}

		CameraRenderState camera = context.levelState().cameraRenderState;
		Vec3 eye = camera.pos;
		PoseStack poseStack = context.poseStack();
		OrderedSubmitNodeCollector collector = null;

		for (Source source : SOURCES) {
			Marks marks = source.marks().get();
			if (marks.isEmpty()) {
				continue;
			}
			if (collector == null) {
				collector = context.submitNodeCollector().order(ORDER_LAST);
				poseStack.pushPose();
				// The level pose is identity at the camera; bring world coordinates back to it.
				poseStack.translate(-eye.x, -eye.y, -eye.z);
			}
			boolean throughWalls = source.throughWalls().getAsBoolean();
			for (Mark mark : marks.marks()) {
				draw(poseStack, collector, camera, mark, throughWalls);
			}
		}

		if (collector != null) {
			poseStack.popPose();
		}
	}

	private static void draw(PoseStack poseStack, OrderedSubmitNodeCollector collector,
			CameraRenderState camera, Mark mark, boolean throughWalls) {
		switch (mark) {
			case Mark.Box box -> DrawKit.box(poseStack, collector, box.mesh(), box.argb(), box.fill(), box.style(),
					throughWalls);
			case Mark.EntityBox entityBox -> entityBox(poseStack, collector, entityBox, throughWalls);
			case Mark.Line line -> DrawKit.segment(poseStack, collector, line.from(), line.to(),
					line.argb(), line.width(), throughWalls);
			case Mark.Tracer tracer -> {
				// A tracer starts at the eye, so terrain never hides it: it would be
				// cut off by the first wall and point at nothing.
				Vec3 look = Vec3.directionFromRotation(camera.xRot, camera.yRot);
				DrawKit.segment(poseStack, collector, camera.pos.add(look.scale(TRACER_START)),
						tracer.to(), tracer.argb(), tracer.width(), true);
			}
			case Mark.Label label -> DrawKit.label(poseStack, collector, camera, label.at(),
					label.text(), label.scale());
		}
	}

	/** The entity's box where it is this frame, not where it was last tick. */
	private static void entityBox(PoseStack poseStack, OrderedSubmitNodeCollector collector,
			Mark.EntityBox mark, boolean throughWalls) {
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		Entity entity = level.getEntity(mark.entityId());
		if (entity == null || entity.isRemoved()) {
			return;
		}

		Vec3 at = entity.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		double half = entity.getBbWidth() / 2.0 + mark.inflateXZ();
		double height = mark.height() > 0 ? mark.height() : entity.getBbHeight();
		double floor = at.y + mark.lift();
		AABB box = new AABB(at.x - half, floor, at.z - half, at.x + half, floor + height, at.z + half);
		DrawKit.box(poseStack, collector, BoxMesh.of(box), mark.argb(), mark.fill(), mark.style(),
				throughWalls);
	}
}
