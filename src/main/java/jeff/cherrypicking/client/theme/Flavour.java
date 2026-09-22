package jeff.cherrypicking.client.theme;

import java.util.List;

/**
 * The colour themes the {@code theme.flavour} setting picks from: the four Catppuccin flavours and
 * popular editor themes from IntelliJ and VS Code, dark ones first, then light ones.
 *
 * <p>Every theme fills the same 26 Catppuccin slots in {@code themes.json}, so the screen and the
 * markers never need to know which theme is live. {@link #key()} is the theme's name in that
 * file; {@link #label()} is what the screen shows. The constant's name is what the config file
 * saves, so never rename one.
 */
public enum Flavour {
	FRAPPE("frappe", "Catppuccin Frappé"),
	MACCHIATO("macchiato", "Catppuccin Macchiato"),
	MOCHA("mocha", "Catppuccin Mocha"),
	GITHUB_DARK("github_dark", "GitHub Dark"),
	GITHUB_DIMMED("github_dimmed", "GitHub Dark Dimmed"),
	ONE_DARK("one_dark", "One Dark"),
	DRACULA("dracula", "Dracula"),
	NORD("nord", "Nord"),
	TOKYO_NIGHT("tokyo_night", "Tokyo Night"),
	GRUVBOX_DARK("gruvbox_dark", "Gruvbox Dark"),
	SOLARIZED_DARK("solarized_dark", "Solarized Dark"),
	ROSE_PINE("rose_pine", "Rosé Pine"),
	EVERFOREST_DARK("everforest_dark", "Everforest Dark"),
	KANAGAWA("kanagawa", "Kanagawa"),
	INTELLIJ_DARK("intellij_dark", "IntelliJ Dark"),
	VSCODE_DARK("vscode_dark", "VS Code Dark Modern"),
	MONOKAI("monokai", "Monokai"),
	LATTE("latte", "Catppuccin Latte"),
	GITHUB_LIGHT("github_light", "GitHub Light"),
	ONE_LIGHT("one_light", "One Light"),
	SOLARIZED_LIGHT("solarized_light", "Solarized Light"),
	GRUVBOX_LIGHT("gruvbox_light", "Gruvbox Light"),
	ROSE_PINE_DAWN("rose_pine_dawn", "Rosé Pine Dawn"),
	EVERFOREST_LIGHT("everforest_light", "Everforest Light"),
	INTELLIJ_LIGHT("intellij_light", "IntelliJ Light"),
	VSCODE_LIGHT("vscode_light", "VS Code Light Modern");

	/** What the dropdown shows beside each theme: its background, its text, and five accents. */
	private static final List<String> PREVIEW = List.of("base", "text", "red", "yellow", "green", "blue", "mauve");

	private final String key;
	private final String label;

	Flavour(String key, String label) {
		this.key = key;
		this.label = label;
	}

	public String key() {
		return key;
	}

	public String label() {
		return label;
	}

	/** A few of this theme's colours, so the dropdown shows what it looks like before it is picked. */
	public List<Integer> preview() {
		Palette palette = Palettes.of(this);
		return PREVIEW.stream().map(palette::of).toList();
	}
}
