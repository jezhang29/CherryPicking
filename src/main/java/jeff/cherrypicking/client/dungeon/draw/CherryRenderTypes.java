package jeff.cherrypicking.client.dungeon.draw;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;

/**
 * Lines and quads that draw through terrain.
 *
 * <p>Vanilla has no such render type, and the dungeon layer cannot do without
 * one: a puzzle's next lever, a starred mob and the real Livid are all routinely
 * behind a wall, and a depth-tested highlight appears exactly when it has
 * stopped being useful.
 *
 * <p>Copied from coalroutegenerator's {@code render.RouteRenderTypes}.
 *
 * <p>Each has a depth-tested twin, for the puzzles' {@code puzzles.throughWalls}
 * switch. Everything else in the layer always draws through terrain.
 *
 * <p>Built lazily. Render pipelines cannot be constructed during mod init,
 * before the graphics device exists, so the first frame that wants one makes it.
 */
public final class CherryRenderTypes {
	/** Pass the depth test unconditionally, and do not write depth back. */
	private static final DepthStencilState NO_DEPTH_TEST =
			new DepthStencilState(CompareOp.ALWAYS_PASS, false);

	/** Vanilla's own depth test, still without writing depth, so marks never hide each other. */
	private static final DepthStencilState DEPTH_TEST =
			new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false);

	private static RenderType linesThroughWalls;
	private static RenderType quadsThroughWalls;
	private static RenderType linesDepthTested;
	private static RenderType quadsDepthTested;

	private CherryRenderTypes() {
	}

	/** {@link #linesThroughWalls()}, or the same lines hidden by terrain. */
	public static RenderType lines(boolean throughWalls) {
		return throughWalls ? linesThroughWalls() : linesDepthTested();
	}

	/** {@link #quadsThroughWalls()}, or the same quads hidden by terrain. */
	public static RenderType quads(boolean throughWalls) {
		return throughWalls ? quadsThroughWalls() : quadsDepthTested();
	}

	public static RenderType linesThroughWalls() {
		if (linesThroughWalls == null) {
			// Built from vanilla's own line snippet, so the vertex shader and
			// format come from Mojang rather than from hard-coded ids here. The
			// fragment shader is ours: vanilla's applies fog, and under blindness
			// that fades every edge past a few blocks to black. The fill's
			// position_color shader has no fog, so only the lines need this.
			RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
					.withLocation(CherryPicking.id("pipeline/lines_through_walls"))
					.withFragmentShader(CherryPicking.id("core/lines_no_fog"))
					.withDepthStencilState(NO_DEPTH_TEST)
					.withCull(false)
					.build();

			linesThroughWalls = RenderType.create(
					CherryPicking.MOD_ID + ":lines_through_walls",
					RenderSetup.builder(pipeline)
							.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
							.createRenderSetup());
		}
		return linesThroughWalls;
	}

	/**
	 * Translucent quads that draw through terrain, for box fills.
	 *
	 * <p>Back faces are culled. With no depth test, an unculled box draws its far
	 * faces over its near ones, which shows as soon as the fill is opaque. Culled,
	 * a box is at most one face deep at any pixel, so it needs no sorting; boxes
	 * that overlap each other are drawn far to near by their caller. Every quad
	 * here comes from {@link BoxMesh}, which winds its faces for this.
	 */
	public static RenderType quadsThroughWalls() {
		if (quadsThroughWalls == null) {
			RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(CherryPicking.id("pipeline/quads_through_walls"))
					.withDepthStencilState(NO_DEPTH_TEST)
					.withCull(true)
					.build();

			quadsThroughWalls = RenderType.create(
					CherryPicking.MOD_ID + ":quads_through_walls",
					RenderSetup.builder(pipeline)
							.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
							.createRenderSetup());
		}
		return quadsThroughWalls;
	}

	/** The lines of {@link #linesThroughWalls()}, depth-tested: for a player who wants walls to hide marks. */
	private static RenderType linesDepthTested() {
		if (linesDepthTested == null) {
			RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
					.withLocation(CherryPicking.id("pipeline/lines_depth_tested"))
					.withFragmentShader(CherryPicking.id("core/lines_no_fog"))
					.withDepthStencilState(DEPTH_TEST)
					.withCull(false)
					.build();

			linesDepthTested = RenderType.create(
					CherryPicking.MOD_ID + ":lines_depth_tested",
					RenderSetup.builder(pipeline)
							.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
							.createRenderSetup());
		}
		return linesDepthTested;
	}

	/**
	 * The quads of {@link #quadsThroughWalls()}, depth-tested. A box that sits
	 * exactly on a block's faces fights that block for depth, so callers inflate
	 * such boxes a little.
	 */
	private static RenderType quadsDepthTested() {
		if (quadsDepthTested == null) {
			RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(CherryPicking.id("pipeline/quads_depth_tested"))
					.withDepthStencilState(DEPTH_TEST)
					.withCull(true)
					.build();

			quadsDepthTested = RenderType.create(
					CherryPicking.MOD_ID + ":quads_depth_tested",
					RenderSetup.builder(pipeline)
							.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
							.createRenderSetup());
		}
		return quadsDepthTested;
	}
}
