package jeff.cherrypicking.client.cosmetics;

import java.net.http.WebSocket;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.client.Minecraft;

/**
 * The live link: a WebSocket to the relay, open while friend cosmetics are on and you are on a
 * server. The relay pushes a friend's looks through it the moment the friend shares them, so
 * {@link Poller} fetches only while the link is down.
 *
 * <p>Transport only. It connects, reconnects after a wait that doubles from 5 s to 5 minutes, pings
 * every 30 s, and drops a link that stops answering. Each message waits in a queue for
 * {@link Poller}, which decides what it means.
 *
 * <p>Client thread only. The listener runs on the HTTP client's threads; it only queues messages
 * and reports a close back through {@link Minecraft#execute}.
 */
final class LiveLink {
	private static final int PING_TICKS = 600;
	/** Two pings without an answer: the link is dead, even with no close. */
	private static final long SILENT_MS = 70_000;
	private static final long FIRST_RETRY_MS = 5_000;
	private static final long LAST_RETRY_MS = 300_000;
	/** Ten friends of 64 KB each, with room for the JSON around them. */
	private static final int MAX_MESSAGE_CHARS = 1024 * 1024;

	private static final Queue<String> RECEIVED = new ConcurrentLinkedQueue<>();

	private static WebSocket socket;
	/** The relay address the link was opened to; a new address needs a new link. */
	private static String linkedUrl;
	private static boolean connecting;
	/** Goes up by one each time a link opens, so {@link Poller} knows to send its friend list. */
	private static int generation;
	private static long retryAt;
	private static long retryMs = FIRST_RETRY_MS;
	private static volatile long heardAt;
	/** Sends one at a time: Java's WebSocket refuses a send while another is on its way. */
	private static CompletableFuture<?> sending = CompletableFuture.completedFuture(null);
	private static int ticks;

	private LiveLink() {
	}

	static boolean open() {
		return socket != null;
	}

	static int generation() {
		return generation;
	}

	/** The next message from the relay, or null if none is waiting. */
	static String next() {
		return RECEIVED.poll();
	}

	static void send(String text) {
		WebSocket to = socket;
		sending = sending.thenCompose(ignored -> to.sendText(text, true)).exceptionally(error -> null);
	}

	/** Client thread, every tick. */
	static void tick(Minecraft client) {
		ticks++;
		boolean wanted = Cosmetics.enabled() && client.getConnection() != null;
		if (!wanted || (socket != null && !RelayClient.url().equals(linkedUrl))) {
			close();
			if (!wanted) {
				return;
			}
		}
		long now = System.currentTimeMillis();
		if (socket == null) {
			if (!connecting && now >= retryAt && RelayClient.ready()) {
				connect(client);
			}
			return;
		}
		if (now - heardAt > SILENT_MS) {
			socket.abort();
			lost(socket, "no answer to pings");
		} else if (ticks % PING_TICKS == 0) {
			send("ping");
		}
	}

	private static void connect(Minecraft client) {
		connecting = true;
		String address = RelayClient.url();
		RelayClient.live(new Listener(client)).whenComplete((opened, error) -> client.execute(() -> {
			connecting = false;
			if (error != null) {
				// RelayClient logged why.
				retryLater();
				return;
			}
			if (!Cosmetics.enabled() || client.getConnection() == null || !address.equals(RelayClient.url())) {
				opened.sendClose(WebSocket.NORMAL_CLOSURE, "not needed");
				return;
			}
			socket = opened;
			linkedUrl = address;
			sending = CompletableFuture.completedFuture(null);
			generation++;
			heardAt = System.currentTimeMillis();
			retryMs = FIRST_RETRY_MS;
			CherryPicking.LOGGER.info("Relay: live link open; friends' looks arrive as they change.");
		}));
	}

	/**
	 * Closes the link, when it is no longer wanted or on "Reconnect relay". Drops the wait, so the
	 * next wanted link opens at once.
	 */
	static void close() {
		if (socket != null) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			socket = null;
		}
		RECEIVED.clear();
		retryAt = 0;
		retryMs = FIRST_RETRY_MS;
	}

	/** Client thread. Ignores a close of an old link that was already replaced. */
	private static void lost(WebSocket link, String why) {
		if (socket != link) {
			return;
		}
		socket = null;
		retryLater();
		CherryPicking.LOGGER.info("Relay: live link lost ({}); trying again in {} s.", why, retryMs / 1000);
	}

	private static void retryLater() {
		retryAt = System.currentTimeMillis() + retryMs;
		retryMs = Math.min(retryMs * 2, LAST_RETRY_MS);
	}

	private static final class Listener implements WebSocket.Listener {
		private final Minecraft client;
		private final StringBuilder text = new StringBuilder();

		Listener(Minecraft client) {
			this.client = client;
		}

		@Override
		public CompletionStage<?> onText(WebSocket link, CharSequence part, boolean last) {
			heardAt = System.currentTimeMillis();
			text.append(part);
			if (text.length() > MAX_MESSAGE_CHARS) {
				link.abort();
				client.execute(() -> lost(link, "a message larger than " + MAX_MESSAGE_CHARS + " characters"));
				return null;
			}
			if (last) {
				String message = text.toString();
				text.setLength(0);
				if (!message.equals("pong")) {
					RECEIVED.add(message);
				}
			}
			link.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket link, int code, String reason) {
			client.execute(() -> lost(link, "closed by the relay, " + code + " " + reason));
			return null;
		}

		@Override
		public void onError(WebSocket link, Throwable error) {
			client.execute(() -> lost(link, error.toString()));
		}
	}
}
