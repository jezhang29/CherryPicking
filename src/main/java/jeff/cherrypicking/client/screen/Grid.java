package jeff.cherrypicking.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Flows cards into as many columns as fit the body.
 *
 * <p>Each card goes into whichever column is currently shortest, so the columns end nearly level.
 * That, with folding cards, is what keeps sixty settings on one screen instead of four.
 */
final class Grid {
	static final int MIN_CARD_WIDTH = 160;
	static final int MAX_CARD_WIDTH = 280;

	static final int GAP = 6;

	/** The {@code screen.cardWidth} setting. The default is here, at the field. */
	private static volatile int cardWidth = MIN_CARD_WIDTH;

	private Grid() {
	}

	/** How narrow a column may get before the grid drops to fewer of them. */
	static int cardWidth() {
		return cardWidth;
	}

	static void cardWidth(int value) {
		cardWidth = Math.clamp(value, MIN_CARD_WIDTH, MAX_CARD_WIDTH);
	}

	/** A card's place, with {@code y} measured from the top of the content, before scrolling. */
	record Placed(Card card, int x, int y, int width, int height) {
	}

	record Layout(List<Placed> placed, int contentHeight) {
	}

	static int columns(int width) {
		return Math.max(1, (width + GAP) / (cardWidth + GAP));
	}

	static Layout flow(List<Card> cards, ToIntFunction<Card> height, int left, int width) {
		int columns = columns(width);
		int columnWidth = (width - GAP * (columns - 1)) / columns;
		int[] bottoms = new int[columns];

		List<Placed> placed = new ArrayList<>(cards.size());
		for (Card card : cards) {
			int column = 0;
			for (int i = 1; i < columns; i++) {
				if (bottoms[i] < bottoms[column]) {
					column = i;
				}
			}
			int cardHeight = height.applyAsInt(card);
			int x = left + column * (columnWidth + GAP);
			placed.add(new Placed(card, x, bottoms[column], columnWidth, cardHeight));
			bottoms[column] += cardHeight + GAP;
		}

		int contentHeight = 0;
		for (int bottom : bottoms) {
			contentHeight = Math.max(contentHeight, bottom - GAP);
		}
		return new Layout(placed, Math.max(0, contentHeight));
	}
}
