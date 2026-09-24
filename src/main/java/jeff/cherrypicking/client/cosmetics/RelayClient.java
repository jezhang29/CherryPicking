package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.authlib.exceptions.AuthenticationException;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Talks to the relay, the Cloudflare Worker in section 5 of docs/friend-cosmetics-plan.md.
 *
 * <p><b>Login</b> uses the same check as joining a Minecraft server: the relay makes a
 * {@code serverId}, this client tells Mojang it joins that id, and the relay asks Mojang to confirm.
 * The access token goes only to Mojang. The relay's token is kept in memory only.
 *
 * <p><b>Failures</b> are logged once, not once per try; the log says again when the relay works.
 * After a network failure the next try waits a minute, after a session failure ten minutes. If the
 * relay does not allow this player, it stops until {@link #forget}.
 *
 * <p>Requests run on the HTTP client's threads and the login call on its own thread, never on the
 * client thread.
 */
public final class RelayClient {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long NETWORK_RETRY_MS = 60_000;
	private static final long SESSION_RETRY_MS = 600_000;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	/** {@code joinServer} blocks, so it gets a thread of its own. */
	private static final ExecutorService LOGIN = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "CherryPicking relay login");
		thread.setDaemon(true);
		return thread;
	});

	/** The {@code friendCosmetics.relayUrl} setting. The default is the relay this mod ships with. */
	private static volatile String url = "https://cherry-relay.jezhang-25.workers.dev";

	private static volatile String token;
	private static volatile long waitUntil;
	private static volatile boolean refused;
	/** The last problem logged; empty while the relay works. */
	private static volatile String lastProblem = "";

	private RelayClient() {
	}

	public static String url() {
		return url;
	}

	/** A new address is a new relay, so the old login is dropped. */
	public static void url(String value) {
		url = value;
		forget();
	}

	/** False while a failure's wait runs, or after the relay refused this player. */
	static boolean ready() {
		return !refused && System.currentTimeMillis() >= waitUntil;
	}

	/** Drops the login and every wait, so the next request logs in again. */
	static void forget() {
		token = null;
		waitUntil = 0;
		refused = false;
		lastProblem = "";
	}

	/** Sends your payload. Completes normally only when the relay stored it. */
	static CompletableFuture<Void> publish(String json, int looks) {
		return withToken(bearer -> send("PUT", "/cosmetics", bearer, json))
				.thenAccept(reply -> {
					if (reply.status() != 204) {
						throw new Failure("the relay did not take your looks: HTTP " + reply.status()
								+ " " + reply.body(), NETWORK_RETRY_MS);
					}
					working("Relay: shared {} of your looks.", looks);
				})
				.whenComplete((ignored, error) -> {
					if (error != null) {
						failed(error);
					}
				});
	}

	/** Runs {@code request} with a token, logging in first if there is none, and again on a 401. */
	private static CompletableFuture<Reply> withToken(Function<String, CompletableFuture<Reply>> request) {
		String current = token;
		CompletableFuture<String> bearer = current != null ? CompletableFuture.completedFuture(current) : login();
		return bearer.thenCompose(request).thenCompose(reply -> {
			if (reply.status() != 401) {
				return CompletableFuture.completedFuture(reply);
			}
			token = null;
			return login().thenCompose(request);
		});
	}

	private static CompletableFuture<String> login() {
		User user = Minecraft.getInstance().getUser();
		String name = user.getName();
		return send("GET", "/challenge?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8), null, null)
				.thenApplyAsync(reply -> {
					JsonObject challenge = object(reply, "challenge");
					String serverId = challenge.get("serverId").getAsString();
					long ts = challenge.get("ts").getAsLong();
					try {
						Minecraft.getInstance().services().sessionService()
								.joinServer(user.getProfileId(), user.getAccessToken(), serverId);
					} catch (AuthenticationException rejected) {
						throw new Failure("Mojang did not accept your session (" + rejected.getMessage()
								+ "). Trying again in 10 minutes.", SESSION_RETRY_MS);
					}
					JsonObject body = new JsonObject();
					body.addProperty("name", name);
					body.addProperty("ts", ts);
					return body.toString();
				}, LOGIN)
				.thenCompose(body -> send("POST", "/login", null, body))
				.thenApply(reply -> {
					if (reply.status() == 403 && reply.body().contains("not allowed")) {
						throw new Failure("your UUID " + user.getProfileId().toString().replace("-", "")
								+ " is not in the relay's ALLOWED list. Add it, then reconnect.", Long.MAX_VALUE);
					}
					String fresh = object(reply, "login").get("token").getAsString();
					token = fresh;
					CherryPicking.LOGGER.info("Relay: logged in as {}.", name);
					return fresh;
				});
	}

	private static CompletableFuture<Reply> send(String method, String path, String bearer, String body) {
		HttpRequest.Builder request;
		try {
			request = HttpRequest.newBuilder(base().resolve(path)).timeout(TIMEOUT);
		} catch (IllegalArgumentException badAddress) {
			return CompletableFuture.failedFuture(new Failure("the relay address \"" + url
					+ "\" is not an https:// web address.", NETWORK_RETRY_MS));
		}
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		if (body != null) {
			request.header("Content-Type", "application/json");
		}
		request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> new Reply(response.statusCode(), response.body()));
	}

	/** The relay address with no trailing slash. Only https, so the token never travels in clear. */
	private static URI base() {
		String address = url.strip();
		while (address.endsWith("/")) {
			address = address.substring(0, address.length() - 1);
		}
		if (!address.startsWith("https://")) {
			throw new IllegalArgumentException("not https");
		}
		return URI.create(address + "/");
	}

	/** The reply's JSON object; a failure if the status is not 200 or the body is not an object. */
	private static JsonObject object(Reply reply, String step) {
		if (reply.status() != 200) {
			throw new Failure(step + " failed: HTTP " + reply.status() + " " + reply.body(), NETWORK_RETRY_MS);
		}
		try {
			JsonElement parsed = JsonParser.parseString(reply.body());
			if (parsed.isJsonObject()) {
				return parsed.getAsJsonObject();
			}
		} catch (JsonParseException ignored) {
			// Reported below with the body.
		}
		throw new Failure(step + " reply is not JSON: " + reply.body(), NETWORK_RETRY_MS);
	}

	private static void failed(Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		switch (cause) {
			case Failure failure -> problem(failure.getMessage(), failure.retryMs);
			case IOException io -> problem("cannot reach " + url + " (" + io + ").", NETWORK_RETRY_MS);
			default -> {
				// A bug here, not the network: keep the stack trace.
				CherryPicking.LOGGER.warn("Relay: unexpected error.", cause);
				problem("unexpected error: " + cause, NETWORK_RETRY_MS);
			}
		}
	}

	private static void problem(String message, long retryMs) {
		if (retryMs == Long.MAX_VALUE) {
			refused = true;
		} else {
			waitUntil = System.currentTimeMillis() + retryMs;
		}
		if (!message.equals(lastProblem)) {
			lastProblem = message;
			CherryPicking.LOGGER.warn("Relay: {}", message);
		}
	}

	private static void working(String message, Object argument) {
		if (!lastProblem.isEmpty()) {
			lastProblem = "";
			CherryPicking.LOGGER.info("Relay: working again.");
		}
		CherryPicking.LOGGER.info(message, argument);
	}

	private record Reply(int status, String body) {
	}

	/** A failure with a known cause and wait. {@code Long.MAX_VALUE} waits until {@link #forget}. */
	private static final class Failure extends RuntimeException {
		private final long retryMs;

		Failure(String message, long retryMs) {
			super(message, null, false, false);
			this.retryMs = retryMs;
		}
	}
}
