/*
 * The puzzle solution files under assets/cherrypicking/puzzles/ are Odin's, bundled verbatim:
 *
 * Copyright (c) 2025, odtheking - BSD 3-Clause
 * https://github.com/odtheking/Odin
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the conditions of the BSD 3-Clause License are met. The notice is also in
 * README.md.
 */
package jeff.cherrypicking.client.dungeon.puzzle;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import jeff.cherrypicking.CherryPicking;

/**
 * Loads the bundled puzzle solutions.
 *
 * <p>The files are data, copied from Odin unchanged, so a correction upstream is a file swap. Each
 * is read once, lazily, straight off the classpath, the way {@code Palettes} reads the theme.
 *
 * <p><b>Adding a file</b> is a {@link File} constant with its parser and its value for "could not
 * read", and a getter that returns {@code THAT.get()}. The reading, the caching, the locking and
 * the logging are {@link File}'s, once, so the third file cannot drift from the first.
 *
 * <p>Nothing here throws. A missing or malformed file logs and yields the empty value, so that one
 * solver draws nothing and the rest of the mod is unaffected.
 */
public final class Solutions {
	/** A beams row: two room-relative positions, {@code x1,y1,z1, x2,y2,z2}. */
	private static final int BEAM_ROW = 6;

	private static final File<List<int[]>> BEAMS = new File<>(
			"/assets/cherrypicking/puzzles/creeper-beams-solutions.json",
			Solutions::parseBeams, List.of());

	private Solutions() {
	}

	/**
	 * Creeper Beams: every candidate pair of lanterns, in file order. Each row is
	 * {@code [x1, y1, z1, x2, y2, z2]}, room-relative. The upstream file lists one row twice;
	 * it is kept, and the caller de-duplicates.
	 */
	public static List<int[]> creeperBeams() {
		return BEAMS.get();
	}

	private static List<int[]> parseBeams(JsonElement root) {
		if (!root.isJsonArray()) {
			return null;
		}
		List<int[]> rows = new ArrayList<>();
		for (JsonElement element : root.getAsJsonArray()) {
			int[] row = ints(element, BEAM_ROW);
			if (row == null) {
				CherryPicking.LOGGER.warn("Skipping a malformed beams row: {}", element);
				continue;
			}
			rows.add(row);
		}
		return List.copyOf(rows);
	}

	/**
	 * @param length how many numbers to insist on, or -1 for any
	 * @return the whole numbers, or null when the element is not a list of them
	 */
	static int[] ints(JsonElement element, int length) {
		if (!element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		if (length >= 0 && array.size() != length) {
			return null;
		}
		int[] values = new int[array.size()];
		for (int i = 0; i < values.length; i++) {
			try {
				values[i] = array.get(i).getAsInt();
			} catch (RuntimeException notANumber) {
				return null;
			}
		}
		return values;
	}

	/** As {@link #ints}, for a file of fractional seconds. */
	static double[] reals(JsonElement element) {
		if (!element.isJsonArray()) {
			return null;
		}
		JsonArray array = element.getAsJsonArray();
		double[] values = new double[array.size()];
		for (int i = 0; i < values.length; i++) {
			try {
				values[i] = array.get(i).getAsDouble();
			} catch (RuntimeException notANumber) {
				return null;
			}
		}
		return values;
	}

	/**
	 * One bundled file, read on the first call and kept.
	 *
	 * <p>{@code parse} runs once, on whichever thread asks first, and returns null for a file it
	 * cannot make sense of; {@code empty} is then what every caller sees. A file that fails is not
	 * retried: it is in the jar, so it will not have got better.
	 */
	private static final class File<T> {
		private final String path;
		private final Function<JsonElement, T> parse;
		private final T empty;

		private volatile T value;

		File(String path, Function<JsonElement, T> parse, T empty) {
			this.path = path;
			this.parse = parse;
			this.empty = empty;
		}

		T get() {
			T loaded = value;
			if (loaded == null) {
				synchronized (this) {
					loaded = value;
					if (loaded == null) {
						loaded = load();
						value = loaded;
					}
				}
			}
			return loaded;
		}

		private T load() {
			JsonElement root = read();
			if (root == null) {
				return empty;
			}
			T parsed;
			try {
				parsed = parse.apply(root);
			} catch (RuntimeException malformed) {
				CherryPicking.LOGGER.warn("Could not make sense of {}.", path, malformed);
				return empty;
			}
			if (parsed == null) {
				CherryPicking.LOGGER.warn("{} is not the shape it should be.", path);
				return empty;
			}
			return parsed;
		}

		/** @return the parsed file, or null when it cannot be read */
		private JsonElement read() {
			try (InputStream in = Solutions.class.getResourceAsStream(path)) {
				if (in == null) {
					CherryPicking.LOGGER.warn("Puzzle solutions {} are missing.", path);
					return null;
				}
				try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
					return JsonParser.parseReader(reader);
				}
			} catch (Exception e) {
				CherryPicking.LOGGER.warn("Could not read puzzle solutions {}.", path, e);
				return null;
			}
		}
	}
}
