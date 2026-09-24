package jeff.cherrypicking.client.cosmetics;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

/**
 * Talks to the relay, the Cloudflare Worker in section 5 of docs/friend-cosmetics-plan.md.
 *
 * <p><b>Login</b> signs the relay's challenge with the game's chat key, which Mojang certifies for
 * this player (see {@link #loginBody}). The relay's token is kept in memory only.
 *
 * <p><b>Failures</b> are logged once, not once per try; the log says again when the relay works.
 * After a network failure the next try waits a minute, after a key failure ten minutes. If the
 * relay does not allow this player, it stops until {@link #forget}.
 *
 * <p>Requests run on the HTTP client's threads, never on the client thread.
 */
public final class RelayClient {
	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final long NETWORK_RETRY_MS = 60_000;
	private static final long KEY_RETRY_MS = 600_000;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

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
		return withToken(bearer -> send("PUT", "/cosmetics", bearer, json, null))
				.thenAccept(reply -> {
					if (reply.status() != 204) {
						throw new Failure("the relay did not take your looks: HTTP " + reply.status()
								+ " " + reply.body(), NETWORK_RETRY_MS);
					}
					working();
					CherryPicking.LOGGER.info("Relay: shared {} of your looks.", looks);
				})
				.whenComplete((ignored, error) -> {
					if (error != null) {
						failed(error);
					}
				});
	}

	/** The relay's reply to a fetch: its {@code ETag}, and the JSON body with the players. */
	record Fetched(String etag, JsonObject body) {
	}

	/**
	 * Fetches the named players' payloads. Empty when the relay answers that nothing changed since
	 * {@code etag}, the {@code ETag} of the last fetch, or null for none.
	 */
	static CompletableFuture<Optional<Fetched>> fetch(List<String> names, String etag) {
		String path = "/cosmetics?names=" + URLEncoder.encode(String.join(",", names), StandardCharsets.UTF_8);
		return withToken(bearer -> send("GET", path, bearer, null, etag))
				.thenApply(reply -> {
					if (reply.status() == 304) {
						working();
						return Optional.<Fetched>empty();
					}
					JsonObject body = object(reply, "fetching friends' looks");
					working();
					return Optional.of(new Fetched(reply.etag(), body));
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
		Minecraft minecraft = Minecraft.getInstance();
		User user = minecraft.getUser();
		String name = user.getName();
		String uuid = user.getProfileId().toString().replace("-", "");
		CompletableFuture<Optional<ProfileKeyPair>> keys = minecraft.getProfileKeyPairManager().prepareKeyPair();
		return send("GET", "/challenge?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8), null, null, null)
				.thenCombine(keys, (reply, keyPair) -> loginBody(name, uuid, object(reply, "challenge"),
						keyPair.orElseThrow(() -> new Failure("the game has no Mojang chat key, so the relay"
								+ " cannot check who you are. An offline account, or an account with chat turned"
								+ " off, has none. Trying again in 10 minutes.", KEY_RETRY_MS))))
				.thenCompose(body -> send("POST", "/login", null, body, null))
				.thenApply(reply -> {
					if (reply.status() == 403 && reply.body().contains("not allowed")) {
						throw new Failure("your UUID " + uuid + " is not in the relay's ALLOWED list."
								+ " Add it, then reconnect.", Long.MAX_VALUE);
					}
					if (reply.status() == 403) {
						throw new Failure("the relay did not accept your game key: " + reply.body()
								+ ". Trying again in 10 minutes.", KEY_RETRY_MS);
					}
					String fresh = object(reply, "login").get("token").getAsString();
					token = fresh;
					CherryPicking.LOGGER.info("Relay: logged in as {}.", name);
					return fresh;
				});
	}

	/**
	 * The login request (relay/worker.js, {@code login}). The game's chat key signs the relay's
	 * challenge, and Mojang's certificate for that key says whose it is. So the relay checks who you
	 * are with no call to Mojang, which refuses requests from Cloudflare. No token or password is in
	 * it. The prefix keeps this signature from ever being a valid chat signature.
	 */
	static String loginBody(String name, String uuid, JsonObject challenge, ProfileKeyPair keys) {
		String serverId = challenge.get("serverId").getAsString();
		ProfilePublicKey.Data certificate = keys.publicKey().data();
		byte[] signature;
		try {
			Signature signer = Signature.getInstance("SHA256withRSA");
			signer.initSign(keys.privateKey());
			signer.update(("cherry-relay-login:" + serverId).getBytes(StandardCharsets.UTF_8));
			signature = signer.sign();
		} catch (GeneralSecurityException failed) {
			throw new Failure("could not sign the relay's challenge (" + failed + ").", KEY_RETRY_MS);
		}
		Base64.Encoder base64 = Base64.getEncoder();
		JsonObject body = new JsonObject();
		body.addProperty("name", name);
		body.addProperty("ts", challenge.get("ts").getAsLong());
		body.addProperty("uuid", uuid);
		body.addProperty("expiresAt", certificate.expiresAt().toEpochMilli());
		body.addProperty("publicKey", base64.encodeToString(certificate.key().getEncoded()));
		body.addProperty("keySignature", base64.encodeToString(certificate.keySignature()));
		body.addProperty("signature", base64.encodeToString(signature));
		return body.toString();
	}

	private static CompletableFuture<Reply> send(String method, String path, String bearer, String body,
			String etag) {
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
		if (etag != null) {
			request.header("If-None-Match", etag);
		}
		request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
				: HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		return HTTP.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
				.thenApply(response -> new Reply(response.statusCode(), response.body(),
						response.headers().firstValue("ETag").orElse(null)));
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

	/** A request worked: if the last one failed, the log says the relay works again. */
	private static void working() {
		if (!lastProblem.isEmpty()) {
			lastProblem = "";
			CherryPicking.LOGGER.info("Relay: working again.");
		}
	}

	private record Reply(int status, String body, String etag) {
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
