package jeff.cherrypicking.client.screen;

import java.util.Locale;

import jeff.cherrypicking.client.config.Entry;

/**
 * The header's query, and whether an {@link Entry} matches it.
 *
 * <p>An entry matches when the query is part of its label, its description, its key, or its
 * section's tab or group name, ignoring case. A blank query matches everything.
 */
final class Search {
	private String query = "";

	void query(String raw) {
		query = raw.strip().toLowerCase(Locale.ROOT);
	}

	boolean active() {
		return !query.isEmpty();
	}

	boolean matches(Entry entry) {
		return !active()
				|| contains(entry.label())
				|| contains(entry.blurb())
				|| contains(entry.key())
				|| contains(entry.section().tab())
				|| contains(entry.section().group());
	}

	private boolean contains(String text) {
		return text.toLowerCase(Locale.ROOT).contains(query);
	}
}
