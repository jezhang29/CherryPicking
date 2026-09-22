package jeff.cherrypicking.client.dungeon.draw;

import java.util.ArrayList;
import java.util.List;

import jeff.cherrypicking.client.theme.Swatch;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * An immutable snapshot of what one feature wants drawn.
 *
 * <p>Each feature holds {@code private static volatile Marks marks = Marks.NONE}
 * and replaces it whole from the client thread. The render thread reads that one
 * reference, so it never sees a list half built.
 */
public record Marks(List<Mark> marks) {
	public static final Marks NONE = new Marks(List.of());

	public Marks {
		marks = List.copyOf(marks);
	}

	public boolean isEmpty() {
		return marks.isEmpty();
	}

	public static Builder builder() {
		return new Builder();
	}

	/** Client thread only. */
	public static final class Builder {
		private final List<Mark> marks = new ArrayList<>();

		private Builder() {
		}

		/** A box in a colour setting's outline and fill. */
		public Builder box(AABB box, Swatch colour, Style style) {
			return box(box, Theme.resolve(colour), Theme.resolveFill(colour), style);
		}

		/** Meshes the box now, so the render thread does not. */
		public Builder box(AABB box, int argb, int fill, Style style) {
			marks.add(new Mark.Box(BoxMesh.of(box), argb, fill, style));
			return this;
		}

		/**
		 * A box around a moving entity, in a colour setting's outline and fill.
		 *
		 * @param lift how far the box's floor sits above the entity's feet; negative is below
		 */
		public Builder entityBox(int entityId, double inflateXZ, double lift, double height,
				Swatch colour, Style style) {
			marks.add(new Mark.EntityBox(entityId, inflateXZ, lift, height, Theme.resolve(colour),
					Theme.resolveFill(colour), style));
			return this;
		}

		public Builder line(Vec3 from, Vec3 to, int argb, float width) {
			marks.add(new Mark.Line(from, to, argb, width));
			return this;
		}

		public Builder tracer(Vec3 to, int argb, float width) {
			marks.add(new Mark.Tracer(to, argb, width));
			return this;
		}

		/** Plain text at the size the renderer picks for the distance. */
		public Builder label(Vec3 at, String text) {
			return label(at, Component.literal(text), 1.0);
		}

		/**
		 * @param text carries its own colour, in its style
		 * @param scale a multiplier on the size the renderer picks; 1 is that size
		 */
		public Builder label(Vec3 at, Component text, double scale) {
			marks.add(new Mark.Label(at, text, scale));
			return this;
		}

		public Marks build() {
			return marks.isEmpty() ? NONE : new Marks(marks);
		}
	}
}
