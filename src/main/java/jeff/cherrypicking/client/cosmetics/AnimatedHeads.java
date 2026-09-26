package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import jeff.cherrypicking.CherryPicking;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The frames of Skyblocker's animated helmets. A shared look names an animated head only by its id;
 * the frames come from the list Skyblocker downloads with the NEU repo, so each client reads its own
 * copy and the payload stays small. Without Skyblocker's list, animated helmets are left out.
 *
 * <p>The list is read once, on an IO thread, the first time an animated helmet is drawn. Its frames
 * are checked there like a friend's helmet skin: only Mojang skins are kept.
 *
 * <p>The game keeps skin images on disk, but loads each one into memory only when it is first drawn,
 * and draws a default skin until then. So the first time a head is drawn in a session, all its frames
 * are loaded at once ({@link #preload}), and a frame that is not loaded yet is not shown: the head
 * keeps its last loaded frame. That holds for friends' heads ({@code Looks}) and for your own, which
 * Skyblocker draws ({@link #steady}).
 */
public final class AnimatedHeads {
	/**
	 * One animated head: its frames as texture properties, and the game ticks each frame shows for.
	 * Ported from Skyblocker's {@code CustomAnimatedHelmetTextures} (LGPL-3.0).
	 */
	record Head(int ticks, List<String> textures) {
	}

	/** Null until the list is read; empty if it could not be. Written once, by the IO thread. */
	private static volatile Map<String, Head> heads;
	/** Render thread only, like {@link #texture}. */
	private static boolean loading;
	private static final Set<String> MISSING = new HashSet<>();
	private static final Set<String> PRELOADED = new HashSet<>();
	/** The last frame of each of Skyblocker's animated heads that was loaded when drawn. */
	private static final Map<String, ResolvableProfile> SHOWN = new HashMap<>();

	private AnimatedHeads() {
	}

	/**
	 * The texture to show now for the animated head {@code id}. Empty while the list loads, and for
	 * an id that is not in it, which is logged once.
	 */
	static Optional<String> texture(String id, long millis) {
		Map<String, Head> loaded = heads;
		if (loaded == null) {
			load();
			return Optional.empty();
		}
		Head head = loaded.get(id);
		if (head == null) {
			if (!loaded.isEmpty() && MISSING.add(id)) {
				CherryPicking.LOGGER.warn("Friend looks: Skyblocker's list has no animated head {}; left out.", id);
			}
			return Optional.empty();
		}
		preload(id, head);
		return Optional.of(frame(head, millis));
	}

	/**
	 * The frame to draw for Skyblocker's animated head {@code id}, when Skyblocker picked
	 * {@code frame}: that frame once its skin is loaded, else the last one that was. Null, which makes
	 * Skyblocker draw the plain helmet, until the first frame loads. Called by
	 * {@code SkyblockerAnimatedFrameMixin}, on the thread Skyblocker's own code runs on.
	 */
	public static ResolvableProfile steady(String id, ResolvableProfile frame) {
		if (frame == null) {
			return null;
		}
		Map<String, Head> loaded = heads;
		if (loaded == null) {
			load();
		} else if (loaded.get(id) instanceof Head head) {
			preload(id, head);
		}
		if (skinLoaded(frame)) {
			SHOWN.put(id, frame);
			return frame;
		}
		return SHOWN.get(id);
	}

	/**
	 * True once the game has the skin of {@code profile} in memory, and starts loading it if not. Until
	 * then the head layer draws a default skin.
	 */
	static boolean skinLoaded(ResolvableProfile profile) {
		return Minecraft.getInstance().playerSkinRenderCache().lookup(profile).getNow(Optional.empty()).isPresent();
	}

	/** Starts loading every frame of {@code head}, once per session. */
	private static void preload(String id, Head head) {
		if (PRELOADED.add(id)) {
			CherryPicking.LOGGER.info("Animated helmets: loading the {} frames of {}.", head.textures().size(), id);
			head.textures().forEach(texture -> skinLoaded(Looks.profile(texture)));
		}
	}

	/** The frame shown at {@code millis}: each lasts {@code ticks} game ticks of 50 ms, then the next. */
	static String frame(Head head, long millis) {
		// floorMod: the game's clock comes from System.nanoTime, which may be negative.
		return head.textures().get((int) Math.floorMod(millis / 50 / head.ticks(), head.textures().size()));
	}

	private static void load() {
		if (loading) {
			return;
		}
		loading = true;
		Path file = FabricLoader.getInstance().getConfigDir()
				.resolve("skyblocker/item-repo/constants/animatedskulls.json");
		CompletableFuture.runAsync(() -> heads = read(file), Util.ioPool());
	}

	private static Map<String, Head> read(Path file) {
		try {
			JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			Map<String, Head> read = parsed.isJsonObject() ? parse(parsed.getAsJsonObject()) : Map.of();
			CherryPicking.LOGGER.info("Friend looks: read {} animated heads from {}.", read.size(), file);
			return read;
		} catch (IOException | JsonParseException failed) {
			CherryPicking.LOGGER.warn("Friend looks: could not read Skyblocker's animated heads, {}, so animated"
					+ " helmets are left out. {}", file, failed.toString());
			return Map.of();
		}
	}

	/**
	 * Reads the NEU repo's {@code animatedskulls.json}: under {@code skins}, each id has {@code ticks}
	 * and {@code textures}, each texture written {@code <uuid>:<texture property>}. A frame that is not
	 * a Mojang skin is dropped, and a head with no frames left is dropped.
	 */
	static Map<String, Head> parse(JsonObject root) {
		Map<String, Head> heads = new HashMap<>();
		if (!(root.get("skins") instanceof JsonObject skins)) {
			return heads;
		}
		for (Map.Entry<String, JsonElement> entry : skins.entrySet()) {
			if (!(entry.getValue() instanceof JsonObject head)
					|| !(head.get("ticks") instanceof JsonPrimitive ticks) || !ticks.isNumber()
					|| !(head.get("textures") instanceof JsonArray frames)) {
				continue;
			}
			List<String> textures = new ArrayList<>();
			for (JsonElement frame : frames) {
				if (frame instanceof JsonPrimitive text && text.isString()) {
					String texture = text.getAsString().substring(text.getAsString().indexOf(':') + 1);
					if (Payload.skinOnMojang(texture)) {
						textures.add(texture);
					}
				}
			}
			if (!textures.isEmpty()) {
				// Skyblocker makes it at least 1 too: the list has an entry with 0.
				heads.put(entry.getKey(), new Head(Math.max(1, ticks.getAsInt()), List.copyOf(textures)));
			}
		}
		return heads;
	}
}
