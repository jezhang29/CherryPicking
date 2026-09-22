package jeff.cherrypicking.client.dungeon.mob;

import jeff.cherrypicking.client.theme.Swatch;

/**
 * What a starred mob is, read from its name stand. Decides the box colour.
 *
 * <p>Three kinds, because only these change what you do: Fels hide, and a
 * miniboss hits far harder than the rest. Every other starred mob is one kind.
 * The box height is not here: the mob's own bounding box is used, which fits
 * every mob, including a hidden Fels.
 *
 * <p>The colours are literal, not palette names. The boxes are drawn over grey
 * dungeon stone, where the Latte palette's darker accents are hard to see.
 */
public enum MobKind {
	FELS("Fels", Swatch.of(0xFFFF55FF), "Fels"),
	MINIBOSS("Miniboss", Swatch.of(0xFFFFAA00),
			"Shadow Assassin", "Adventurer", "Angry Archaeologist", "King Midas"),
	STARRED("Starred mob", Swatch.of(0xFF55FFFF));

	private final String label;
	private final String[] names;

	/** The {@code mobs.*Colour} setting. Written by the config screen, read per frame. */
	private volatile Swatch colour;

	MobKind(String label, Swatch colour, String... names) {
		this.label = label;
		this.colour = colour;
		this.names = names;
	}

	/** The kind a stand name belongs to. Never null: anything else is {@link #STARRED}. */
	public static MobKind of(String standName) {
		for (MobKind kind : values()) {
			for (String name : kind.names) {
				if (standName.contains(name)) {
					return kind;
				}
			}
		}
		return STARRED;
	}

	public String label() {
		return label;
	}

	public Swatch colour() {
		return colour;
	}

	public void colour(Swatch value) {
		colour = value;
	}
}
