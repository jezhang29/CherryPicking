package jeff.cherrypicking.client.screen;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import jeff.cherrypicking.client.config.ConfigFile;
import jeff.cherrypicking.client.config.Entry;
import jeff.cherrypicking.client.config.Section;
import jeff.cherrypicking.client.config.Setting;
import jeff.cherrypicking.client.config.Settings;
import jeff.cherrypicking.client.theme.Role;
import jeff.cherrypicking.client.theme.Theme;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * CherryPicking's settings screen, drawn in the live colour theme.
 *
 * <pre>
 * ┌──────────────────────────────────────────────────────────┐
 * │ CherryPicking                  ⌕ [ filter every tab… ]  │  header
 * ├────────────┬─────────────────────────────────────────────┤
 * │▍Appearance │ ┌ Theme ──────────┐ ┌ Screen ──────────┐    │
 * │  Puzzles   │ │ Flavour   Latte │ │ …                │    │  rail | card grid
 * │            │ └─────────────────┘ └──────────────────┘    │
 * ├────────────┴─────────────────────────────────────────────┤
 * │ 2 settings · 2 shown          [Reset tab] [Reset all] [Done] │  footer
 * └──────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Everything on it comes from {@link Settings}: the rail from {@link Section#tab()}, the cards
 * from {@link Section#group()}, the rows from each entry's control. There is no per-setting code
 * here and there must not be.
 *
 * <p>Typing filters every tab at once. Escape clears a query first and only closes the screen
 * when there is nothing left to clear, so a filtered view is never thrown away by surprise.
 *
 * <p>Fold state lives here for the session and is not saved: it is a working state, not a
 * preference.
 */
public final class SettingsScreen extends Screen {
	private static final int MARGIN = 16;
	private static final int MAX_WIDTH = 560;
	private static final int MAX_HEIGHT = 320;
	private static final int HEADER = 22;
	private static final int FOOTER = 22;
	private static final int PAD = 6;
	/** Room kept on the right of the body for the scroll bar. */
	private static final int BAR = 4;
	private static final int SEARCH_WIDTH = 180;
	private static final int BUTTON_HEIGHT = 14;
	private static final int TOOLTIP_DELAY_MS = 350;
	private static final int FLASH_FRAMES = 4;
	/** How long "Reset all" waits for its second click. */
	private static final long CONFIRM_MS = 3000;

	private static final String RESET_TAB = "Reset tab";
	private static final String RESET_SHOWN = "Reset shown";
	private static final String RESET_ALL = "Reset all";
	private static final String CONFIRM = "Click again";

	private final Screen parent;
	private final Search search = new Search();
	private final Scroll scroll = new Scroll();
	private final Set<Section> folded = EnumSet.noneOf(Section.class);

	private String tab;
	private EditBox searchBox;
	private Popover popover;
	private Dragging dragging;

	private String flashKey;
	private int flashFrames;
	/** When "Reset all" was first clicked; 0 while it is not waiting for a second click. */
	private long resetAllArmedAt;

	private Entry hoverEntry;
	private long hoverSince;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int bodyX;
	private int bodyY;
	private int bodyWidth;
	private int bodyHeight;

	/**
	 * The slider a press landed on, held until release, so a fast drag that leaves the row's
	 * 14 pixels keeps scrubbing.
	 */
	record Dragging(Setting<?> setting, int trackX, int trackWidth) {
	}

	private record FooterButton(String key, String label, Chrome.Rect rect, Role text, Runnable run) {
	}

	/** A row under the cursor, with where it was drawn. */
	private record Row(Entry entry, int x, int y, int width) {
	}

	/** @param parent the screen to return to on close; may be null */
	public SettingsScreen(Screen parent) {
		super(Component.literal("CherryPicking"));
		this.parent = parent;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		panelWidth = Math.min(width - 2 * MARGIN, MAX_WIDTH);
		panelHeight = Math.min(height - 2 * MARGIN, MAX_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		bodyX = panelX + 1 + Rail.WIDTH + PAD;
		bodyY = panelY + HEADER + 1 + PAD;
		bodyWidth = panelX + panelWidth - 1 - PAD - BAR - bodyX;
		bodyHeight = panelY + panelHeight - FOOTER - PAD - bodyY;

		// init() runs again on every resize, after the widgets have been cleared, so the
		// popover's field is already gone and the query is carried over by hand.
		popover = null;
		dragging = null;
		String query = searchBox == null ? "" : searchBox.getValue();

		Chrome.Rect field = searchField();
		searchBox = new EditBox(font, field.x() + 14, field.y() + 2, field.width() - 18, 10,
				Component.literal("Search settings"));
		searchBox.setBordered(false);
		searchBox.setMaxLength(64);
		searchBox.setTextShadow(false);
		searchBox.setValue(query);
		searchBox.setResponder(this::searched);
		addWidget(searchBox);
		setInitialFocus(searchBox);
	}

	private void searched(String query) {
		search.query(query);
		scroll.reset();
	}

	private Chrome.Rect searchField() {
		int fieldWidth = Math.max(60, Math.min(SEARCH_WIDTH, panelWidth - 110));
		return new Chrome.Rect(panelX + panelWidth - 8 - fieldWidth, panelY + 5, fieldWidth, 12);
	}

	/** Tabs that have at least one entry, in {@link Section} order. */
	private static List<String> tabs() {
		List<Entry> entries = Settings.all();
		List<String> tabs = new ArrayList<>();
		for (Section section : Section.values()) {
			if (!tabs.contains(section.tab())
					&& entries.stream().anyMatch(entry -> entry.section() == section)) {
				tabs.add(section.tab());
			}
		}
		return tabs;
	}

	private String activeTab(List<String> tabs) {
		if (tab == null || !tabs.contains(tab)) {
			tab = tabs.isEmpty() ? null : tabs.getFirst();
		}
		return tab;
	}

	/** The current tab's cards, or while searching, a card per section with a match. */
	private List<Card> cards() {
		boolean searching = search.active();
		String active = searching ? null : activeTab(tabs());
		List<Entry> entries = Settings.all();

		List<Card> cards = new ArrayList<>();
		for (Section section : Section.values()) {
			if (!searching && !section.tab().equals(active)) {
				continue;
			}
			List<Entry> rows = entries.stream()
					.filter(entry -> entry.section() == section && search.matches(entry))
					.toList();
			if (rows.isEmpty()) {
				continue;
			}
			cards.add(searching
					? new Card(section, section.tab() + " · " + section.group(), rows, false)
					: new Card(section, section.group(), rows, true));
		}
		return cards;
	}

	private boolean isFolded(Card card) {
		return card.foldable() && folded.contains(card.section());
	}

	private Grid.Layout layout() {
		return Grid.flow(cards(), card -> card.height(isFolded(card)), bodyX, bodyWidth);
	}

	private boolean inBody(double mouseX, double mouseY) {
		return Chrome.inside(mouseX, mouseY, bodyX, bodyY, bodyWidth, bodyHeight);
	}

	/** The card under the cursor, or null. */
	private Grid.Placed cardAt(Grid.Layout layout, double mouseX, double mouseY) {
		if (!inBody(mouseX, mouseY)) {
			return null;
		}
		for (Grid.Placed placed : layout.placed()) {
			if (Chrome.inside(mouseX, mouseY, placed.x(), top(placed), placed.width(), placed.height())) {
				return placed;
			}
		}
		return null;
	}

	/** The row under the cursor, or null when it is on a title bar, a gap, or nothing. */
	private Row rowAt(Grid.Placed placed, double mouseY) {
		if (placed == null || isFolded(placed.card())) {
			return null;
		}
		Card card = placed.card();
		Entry entry = card.rowAt(top(placed), mouseY);
		if (entry == null) {
			return null;
		}
		int rowY = top(placed) + Card.HEADER + Card.INSET + card.entries().indexOf(entry) * Widgets.row();
		return new Row(entry, placed.x() + 1, rowY, placed.width() - 2);
	}

	/** Where a card's top is on screen right now. */
	private int top(Grid.Placed placed) {
		return bodyY + placed.y() - scroll.offset();
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// In a world, only the backdrop: the world stays visible behind the panel. From the title
		// screen, vanilla's panorama goes underneath first.
		if (minecraft.level == null) {
			super.extractBackground(graphics, mouseX, mouseY, partialTick);
		}
		graphics.fill(0, 0, width, height, Theme.of(Role.BACKDROP));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		// Nothing under the popover reacts to the cursor while it is open.
		int hoverX = popover == null ? mouseX : -1;
		int hoverY = popover == null ? mouseY : -1;

		Chrome.box(graphics, panelX, panelY, panelWidth, panelHeight, Role.PANEL, Role.BORDER_STRONG);
		drawHeader(graphics, mouseX, mouseY);

		List<String> tabs = tabs();
		int railTop = panelY + HEADER + 1;
		Rail.draw(graphics, font, tabs, search.active() ? null : activeTab(tabs), panelX + 1, railTop,
				panelY + panelHeight - FOOTER - railTop, hoverX, hoverY);

		Entry hovered = drawBody(graphics, hoverX, hoverY);
		drawFooter(graphics, hoverX, hoverY);

		if (popover != null) {
			graphics.nextStratum();
			popover.draw(graphics, font, mouseX, mouseY);
		}
		drawTooltip(graphics, hovered, mouseX, mouseY);

		if (flashFrames > 0 && --flashFrames == 0) {
			flashKey = null;
		}
	}

	private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Chrome.fill(graphics, panelX + 1, panelY + 1, panelWidth - 2, HEADER - 1, Role.HEADER);
		Chrome.horizontal(graphics, panelX + 1, panelX + panelWidth - 1, panelY + HEADER, Role.BORDER);
		Text.draw(graphics, font, title.getString(), panelX + 8, panelY + (HEADER - font.lineHeight) / 2 + 2,
				Theme.of(Role.TEXT));

		Chrome.Rect field = searchField();
		Chrome.box(graphics, field.x(), field.y(), field.width(), field.height(), Role.PANEL,
				searchBox.isFocused() ? Role.ACCENT : Role.BORDER);
		Text.draw(graphics, font, "⌕", field.x() + 4, field.y() + 2, Theme.of(Role.TEXT_DIM));
		searchBox.setTextColor(Theme.of(Role.TEXT));
		searchBox.setHint(Component.literal("filter every tab…").withColor(Theme.of(Role.TEXT_DIM)));
		searchBox.extractRenderState(graphics, mouseX, mouseY, 0);
	}

	/** @return the row under the cursor, for the tooltip */
	private Entry drawBody(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Grid.Layout layout = layout();
		Grid.Placed under = cardAt(layout, mouseX, mouseY);
		Row row = rowAt(under, mouseY);

		graphics.enableScissor(bodyX, bodyY, bodyX + bodyWidth, bodyY + bodyHeight);
		for (Grid.Placed placed : layout.placed()) {
			int top = top(placed);
			if (top >= bodyY + bodyHeight || top + placed.height() <= bodyY) {
				continue;
			}
			placed.card().draw(graphics, font, placed.x(), top, placed.width(), isFolded(placed.card()),
					row != null && placed == under ? row.entry() : null, flashKey);
		}
		if (layout.placed().isEmpty()) {
			Text.centred(graphics, font, search.active() ? "Nothing matches that search." : "Nothing here yet.",
					bodyX + bodyWidth / 2, bodyY + 12, Theme.of(Role.TEXT_DIM));
		}
		graphics.disableScissor();

		scroll.measured(layout.contentHeight(), bodyHeight);
		scroll.renderBar(graphics, bodyX + bodyWidth, bodyY, BAR, bodyHeight);
		return row == null ? null : row.entry();
	}

	private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int top = panelY + panelHeight - FOOTER;
		Chrome.horizontal(graphics, panelX + 1, panelX + panelWidth - 1, top, Role.BORDER);
		Chrome.fill(graphics, panelX + 1, top + 1, panelWidth - 2, FOOTER - 2, Role.FOOTER);

		List<FooterButton> buttons = footerButtons();
		int leftmost = panelX + panelWidth;
		for (FooterButton button : buttons) {
			Chrome.Rect rect = button.rect();
			Chrome.button(graphics, font, button.label(), rect.x(), rect.y(), rect.width(), rect.height(),
					rect.contains(mouseX, mouseY), button.key().equals(flashKey), Role.CARD, button.text());
			leftmost = Math.min(leftmost, rect.x());
		}

		int shown = 0;
		for (Card card : cards()) {
			shown += card.entries().size();
		}
		String count = Settings.all().size() + " settings · " + shown + " shown";
		Text.draw(graphics, font, Text.fit(font, count, leftmost - 8 - (panelX + 8)), panelX + 8,
				top + (FOOTER - font.lineHeight) / 2 + 1, Theme.of(Role.TEXT_DIM));
	}

	/** Right to left: Done, a gap, Reset all, Reset tab. Widths fit either label, so nothing jumps. */
	private List<FooterButton> footerButtons() {
		int y = panelY + panelHeight - FOOTER + (FOOTER - BUTTON_HEIGHT) / 2;
		int right = panelX + panelWidth - 8;

		int doneWidth = font.width("Done") + 20;
		Chrome.Rect done = new Chrome.Rect(right - doneWidth, y, doneWidth, BUTTON_HEIGHT);
		right = done.x() - 12;

		int allWidth = Math.max(font.width(RESET_ALL), font.width(CONFIRM)) + 12;
		Chrome.Rect all = new Chrome.Rect(right - allWidth, y, allWidth, BUTTON_HEIGHT);
		right = all.x() - 4;

		int tabWidth = Math.max(font.width(RESET_TAB), font.width(RESET_SHOWN)) + 12;
		Chrome.Rect shown = new Chrome.Rect(right - tabWidth, y, tabWidth, BUTTON_HEIGHT);

		return List.of(
				new FooterButton("footer.resetShown", search.active() ? RESET_SHOWN : RESET_TAB, shown,
						Role.TEXT, this::resetShown),
				new FooterButton("footer.resetAll", armed() ? CONFIRM : RESET_ALL, all, Role.DANGER,
						this::resetAll),
				new FooterButton("footer.done", "Done", done, Role.TEXT, this::onClose));
	}

	private void drawTooltip(GuiGraphicsExtractor graphics, Entry hovered, int mouseX, int mouseY) {
		if (hovered != hoverEntry) {
			hoverEntry = hovered;
			hoverSince = System.currentTimeMillis();
		}
		if (!Tooltip.enabled() || hovered == null || popover != null || dragging != null
				|| System.currentTimeMillis() - hoverSince < TOOLTIP_DELAY_MS) {
			return;
		}
		graphics.nextStratum();
		Tooltip.draw(graphics, font, hovered, mouseX, mouseY, width, height);
	}

	// ------------------------------------------------------------------ actions

	/** Resets every setting currently on screen: the tab, or the search results. */
	private void resetShown() {
		for (Card card : cards()) {
			for (Entry entry : card.entries()) {
				if (entry instanceof Setting<?> setting) {
					setting.reset();
				}
			}
		}
		ConfigFile.save();
		flash("footer.resetShown");
	}

	/** Asks for a second click within three seconds before resetting everything. */
	private void resetAll() {
		if (!armed()) {
			resetAllArmedAt = System.currentTimeMillis();
			return;
		}
		resetAllArmedAt = 0;
		Settings.resetAll();
		ConfigFile.save();
		flash("footer.resetAll");
	}

	private boolean armed() {
		return resetAllArmedAt != 0 && System.currentTimeMillis() - resetAllArmedAt < CONFIRM_MS;
	}

	private void flash(String key) {
		flashKey = key;
		flashFrames = FLASH_FRAMES;
	}

	private Chrome.Rect popoverBounds() {
		return new Chrome.Rect(panelX + 1, panelY + 1, panelWidth - 2, panelHeight - 2);
	}

	private void openPopover(Popover opened) {
		popover = opened;
		if (popover.field() != null) {
			addWidget(popover.field());
			setFocused(popover.field());
		}
	}

	private void closePopover() {
		if (popover.field() != null) {
			removeWidget(popover.field());
		}
		popover = null;
		setFocused(searchBox);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		int button = event.button();

		// Hit-testing runs in reverse draw order: popover, footer, header, grid, rail.
		if (popover != null) {
			if (!popover.click(event, doubleClick)) {
				closePopover();
			}
			return true;
		}
		if (!Chrome.inside(mouseX, mouseY, panelX, panelY, panelWidth, panelHeight)) {
			return super.mouseClicked(event, doubleClick);
		}

		if (mouseY >= panelY + panelHeight - FOOTER) {
			for (FooterButton footer : footerButtons()) {
				if (button == 0 && footer.rect().contains(mouseX, mouseY)) {
					footer.run().run();
					return true;
				}
			}
			return true;
		}

		if (mouseY < panelY + HEADER) {
			if (searchField().contains(mouseX, mouseY)) {
				searchBox.mouseClicked(event, doubleClick);
				setFocused(searchBox);
			}
			return true;
		}

		if (inBody(mouseX, mouseY)) {
			clickBody(event);
			return true;
		}

		List<String> tabs = tabs();
		int index = Rail.hit(tabs, panelX + 1, panelY + HEADER + 1, mouseX, mouseY);
		if (index >= 0 && button == 0) {
			tab = tabs.get(index);
			searchBox.setValue("");
			scroll.reset();
		}
		return true;
	}

	private void clickBody(MouseButtonEvent event) {
		Grid.Placed placed = cardAt(layout(), event.x(), event.y());
		if (placed == null) {
			return;
		}
		if (event.y() < top(placed) + Card.HEADER) {
			if (placed.card().foldable() && event.button() == 0 && !folded.remove(placed.card().section())) {
				folded.add(placed.card().section());
			}
			return;
		}

		Row row = rowAt(placed, event.y());
		if (row == null || !row.entry().available()) {
			return;
		}
		Widgets.Hit hit = Widgets.click(font, row.entry(), row.x(), row.y(), row.width(), event.x(), event.y(),
				event.button(), event.hasShiftDown());
		if (hit == null) {
			return;
		}
		switch (hit) {
			case Widgets.Hit.Done ignored -> {
			}
			case Widgets.Hit.Ran ran -> flash(ran.action().key());
			case Widgets.Hit.Drag drag -> dragging = new Dragging(drag.setting(), drag.trackX(), drag.trackWidth());
			case Widgets.Hit.Pick pick -> openPopover(
					new Swatches(font, pick.setting(), pick.swatch(), popoverBounds(), ConfigFile::save));
			case Widgets.Hit.Choose choose -> openPopover(
					Dropdown.of(font, choose.setting(), choose.choice(), choose.chip(), popoverBounds(),
							ConfigFile::save));
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (popover != null) {
			popover.drag(event.x(), event.y());
			return true;
		}
		if (dragging != null) {
			Widgets.scrub(dragging.setting(), dragging.trackX(), dragging.trackWidth(), event.x(),
					event.hasShiftDown());
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (popover != null) {
			popover.release();
			return true;
		}
		if (dragging != null) {
			dragging = null;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (popover != null) {
			popover.scrolled(scrollY);
			return true;
		}
		if (!inBody(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		Row row = rowAt(cardAt(layout(), mouseX, mouseY), mouseY);
		if (row != null && Widgets.wheel(row.entry(), row.x(), row.y(), row.width(), mouseX, mouseY,
				scrollY, minecraft.hasShiftDown())) {
			return true;
		}
		scroll.scroll(scrollY);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			if (popover != null) {
				closePopover();
			} else if (!searchBox.getValue().isEmpty()) {
				searchBox.setValue("");
			} else {
				onClose();
			}
			return true;
		}
		if (popover != null && popover.key(event)) {
			return true;
		}
		if (popover != null && (event.key() == InputConstants.KEY_RETURN
				|| event.key() == InputConstants.KEY_NUMPADENTER)) {
			closePopover();
			return true;
		}
		return super.keyPressed(event);
	}

	/** Escape and Done both go back where the player came from, which is what the hub relies on. */
	@Override
	public void onClose() {
		ConfigFile.save();
		minecraft.setScreenAndShow(parent);
	}

	/** The world keeps running behind the screen, matching the hub. */
	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
