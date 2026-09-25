package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonObject;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.DungeonState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;

/**
 * Shares your own looks: reads {@code skyblocker.json}, builds your payload and sends it to the relay
 * when it changes (docs/friend-cosmetics-plan.md, sections 7.3 and 7.4).
 *
 * <ul>
 *   <li>Every 5 s, on an IO thread: has the file changed? If so, it is read again. The first
 *       read also loads the saved {@link SeenIds}; after that, new ids are saved.</li>
 *   <li>Every second: {@link SeenIds} scans your items, and the payload is built again.</li>
 *   <li>A changed payload is sent after a wait, 10 s after a file change and 2 s after anything
 *       else, so a burst of changes is one send.</li>
 * </ul>
 *
 * <p>Only on SkyBlock, where the worn armor and the seen ids mean something. Client thread only;
 * the file read and the send report back through {@link Minecraft#execute}.
 */
public final class Publisher {
	private static final int SCAN_TICKS = 20;
	private static final int FILE_TICKS = 100;
	private static final int FILE_WAIT_TICKS = 200;
	private static final int CHANGE_WAIT_TICKS = 40;

	/** The {@code friendCosmetics.share} setting. */
	private static volatile boolean share = true;

	private static int ticks;
	/** Null until the file is read, and while there is no Skyblocker file. */
	private static Map<String, JsonObject> looks;
	private static long fileTime = Long.MIN_VALUE;
	private static boolean reading;
	/** The newest payload, its look count, and the tick it may be sent at. */
	private static String pending;
	private static int pendingLooks;
	private static int dueAt;
	/** The last payload the relay stored. */
	private static String sent;
	private static boolean sending;
	/** The saved ids are loaded; until then, saving would overwrite them with fewer. */
	private static boolean seenLoaded;
	private static int seenSaved;
	private static boolean seenSaving;

	private Publisher() {
	}

	public static boolean share() {
		return share;
	}

	public static void share(boolean value) {
		share = value;
	}

	/** Forgets everything, so turning the feature on again reads and sends from the start. */
	static void reset() {
		looks = null;
		fileTime = Long.MIN_VALUE;
		pending = null;
		sent = null;
		SeenIds.clear();
		seenLoaded = false;
	}

	/** Client thread, every tick. */
	static void tick(Minecraft client) {
		ticks++;
		LocalPlayer player = client.player;
		if (!Cosmetics.enabled() || !share || player == null || !DungeonState.inSkyBlock()) {
			return;
		}
		if (ticks % FILE_TICKS == 0 && !reading) {
			checkFile(client);
		}
		if (ticks % FILE_TICKS == 0 && seenLoaded && !seenSaving && SeenIds.changes() != seenSaved) {
			saveSeen(client);
		}
		if (ticks % SCAN_TICKS == 0) {
			SeenIds.scan(player);
			build(player, CHANGE_WAIT_TICKS);
		}
		if (pending != null && !pending.equals(sent) && !sending && ticks >= dueAt && RelayClient.ready()) {
			send(client, pending, pendingLooks);
		}
	}

	private static void build(LocalPlayer player, int wait) {
		if (looks == null) {
			return;
		}
		String json = Payload.write(looks, SeenIds.all(), SeenIds.equipped(player));
		if (json.equals(pending)) {
			return;
		}
		// Your own payload goes through the same checks as a friend's, so a look the other side
		// would drop is logged here too.
		pendingLooks = Payload.decode(json, "your own looks").map(payload -> payload.looks().size()).orElse(0);
		pending = json;
		dueAt = ticks + wait;
	}

	private static void checkFile(Minecraft client) {
		reading = true;
		Path file = SkyblockerFile.path();
		boolean loadSeen = !seenLoaded;
		CompletableFuture.supplyAsync(() -> modified(file) == fileTime && !loadSeen ? null : readFile(file, loadSeen),
						Util.ioPool())
				.whenComplete((read, error) -> client.execute(() -> {
					reading = false;
					if (error != null) {
						CherryPicking.LOGGER.warn("Friend looks: could not check {}.", file, error);
					} else if (read != null) {
						if (loadSeen && !seenLoaded) {
							SeenIds.addSaved(read.seen());
							seenLoaded = true;
						}
						fileTime = read.modified();
						looks = read.looks().orElse(null);
						if (client.player != null) {
							build(client.player, FILE_WAIT_TICKS);
						}
					}
				}));
	}

	/** The Skyblocker file's time and looks, and the saved ids when {@code loadSeen}; else none. */
	private record Read(long modified, Optional<Map<String, JsonObject>> looks, Map<String, String> seen) {
	}

	private static Read readFile(Path file, boolean loadSeen) {
		long modified = modified(file);
		return new Read(modified, SkyblockerFile.read(file).map(SkyblockerFile::looks),
				loadSeen ? SeenIds.read(SeenIds.path()) : Map.of());
	}

	private static void saveSeen(Minecraft client) {
		seenSaving = true;
		int changes = SeenIds.changes();
		Map<String, String> snapshot = Map.copyOf(SeenIds.all());
		CompletableFuture.runAsync(() -> SeenIds.write(SeenIds.path(), snapshot), Util.ioPool())
				.whenComplete((ignored, error) -> client.execute(() -> {
					seenSaving = false;
					if (error == null) {
						seenSaved = changes;
					}
				}));
	}

	/** The file's modified time, or {@code -1} if it is missing. */
	private static long modified(Path file) {
		try {
			return Files.getLastModifiedTime(file).toMillis();
		} catch (IOException missing) {
			return -1;
		}
	}

	private static void send(Minecraft client, String json, int count) {
		sending = true;
		RelayClient.publish(json, count).whenComplete((ignored, error) -> client.execute(() -> {
			sending = false;
			if (error == null) {
				sent = json;
			}
		}));
	}
}
