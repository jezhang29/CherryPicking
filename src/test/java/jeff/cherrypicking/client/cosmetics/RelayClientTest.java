package jeff.cherrypicking.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The login request has the fields the relay reads (relay/worker.js, {@code login}), and its
 * signature checks out with the game key, as the relay checks it. relay/worker.test.mjs tests the
 * relay's side.
 */
class RelayClientTest {
	@Test
	void theLoginSignsTheChallengeWithTheGameKey() throws GeneralSecurityException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		KeyPair keys = generator.generateKeyPair();
		byte[] mojangSignature = {1, 2, 3};
		ProfileKeyPair game = new ProfileKeyPair(keys.getPrivate(), new ProfilePublicKey(
				new ProfilePublicKey.Data(Instant.ofEpochMilli(1_790_300_000_000L), keys.getPublic(), mojangSignature)),
				Instant.ofEpochMilli(1_790_200_000_000L));
		JsonObject challenge = JsonParser.parseString(
				"{\"serverId\": \"4ec92ad0b69bcb0c1996683d3fefb16c02fa4eb0\", \"ts\": 1790230538}").getAsJsonObject();

		JsonObject body = JsonParser.parseString(RelayClient.loginBody("Test_Player",
				"e707758b25e049e1a11f4ac39a2fa47d", challenge, game)).getAsJsonObject();

		assertEquals("Test_Player", body.get("name").getAsString());
		assertEquals(1790230538L, body.get("ts").getAsLong());
		assertEquals("e707758b25e049e1a11f4ac39a2fa47d", body.get("uuid").getAsString());
		assertEquals(1_790_300_000_000L, body.get("expiresAt").getAsLong());
		assertArrayEquals(keys.getPublic().getEncoded(), Base64.getDecoder().decode(body.get("publicKey").getAsString()));
		assertArrayEquals(mojangSignature, Base64.getDecoder().decode(body.get("keySignature").getAsString()));

		Signature check = Signature.getInstance("SHA256withRSA");
		check.initVerify(keys.getPublic());
		check.update("cherry-relay-login:4ec92ad0b69bcb0c1996683d3fefb16c02fa4eb0".getBytes(StandardCharsets.UTF_8));
		assertTrue(check.verify(Base64.getDecoder().decode(body.get("signature").getAsString())));
	}
}
