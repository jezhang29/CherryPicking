package jeff.cherrypicking.client.dungeon.draw;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/**
 * One thing to draw in the world. Features publish these in {@link Marks};
 * {@link MarkRenderer} is the only code that draws them, so a new feature writes
 * no render code.
 *
 * <p>Every colour is {@code 0xAARRGGBB}, resolved from its swatch when the
 * feature publishes.
 */
public sealed interface Mark {
	/** Static geometry: the box is meshed once, at publish time. */
	/** {@code argb} is the outline, {@code fill} the inside; each has its own alpha. */
	record Box(BoxMesh mesh, int argb, int fill, Style style) implements Mark {
	}

	/**
	 * A moving entity: its box is read per frame, so the box does not lag the mob.
	 *
	 * @param inflateXZ added to each side of the entity's own width
	 * @param lift how far the box's floor sits above the entity's feet; negative is below
	 * @param height the box height; zero or less uses the entity's own
	 */
	record EntityBox(int entityId, double inflateXZ, double lift, double height, int argb,
			int fill, Style style) implements Mark {
	}

	record Line(Vec3 from, Vec3 to, int argb, float width) implements Mark {
	}

	/** From the player's eye to a point; the eye comes from the camera at draw time. */
	record Tracer(Vec3 to, int argb, float width) implements Mark {
	}

	/**
	 * Floating text. The colour rides in the component's own style, so a feature that wants a
	 * coloured label builds a coloured component and needs nothing here.
	 *
	 * @param scale a multiplier on the size the renderer already holds against distance; 1 is
	 *     that size
	 */
	record Label(Vec3 at, Component text, double scale) implements Mark {
	}
}
