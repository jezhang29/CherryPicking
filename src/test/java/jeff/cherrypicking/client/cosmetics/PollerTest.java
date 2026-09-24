package jeff.cherrypicking.client.cosmetics;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the friend list asks the relay for, and what the relay's reply puts in {@link FriendLooks}.
 * The reply samples have the shape relay/worker.js writes.
 */
class PollerTest {
	private static final UUID BEE = UUID.fromString("e707758b-25e0-49e1-a11f-4ac39a2fa47d");

	@Test
	void theFriendListIsCleanedUp() {
		assertEquals(List.of("Test_Player", "Rrout"), Poller.names(" Test_Player, Rrout ,test_player,,"));
		assertEquals(List.of("Test_Player", "Rrout"), Poller.names("Test_Player Rrout"));
		assertEquals(List.of("Rrout"), Poller.names("a-name!, Rrout, waytoolongforaminecraftname"));
		assertEquals(List.of(), Poller.names(""));
	}

	@Test
	void theRelayReadsAtMostTenNames() {
		assertEquals(10, Poller.names("a,b,c,d,e,f,g,h,i,j,k,l").size());
	}

	@Test
	void aUuidIsReadFromHexDigits() {
		assertEquals(Optional.of(BEE), Poller.uuid("e707758b25e049e1a11f4ac39a2fa47d"));
		assertEquals(Optional.empty(), Poller.uuid("e707758b-25e0-49e1-a11f-4ac39a2fa47d"));
		assertEquals(Optional.empty(), Poller.uuid("E707758B25E049E1A11F4AC39A2FA47D"));
	}

	@Test
	void eachSharedPayloadGoesToItsPlayer() {
		Poller.Players players = Poller.players(JsonParser.parseString("""
				{"players": [
				  {"name": "Test_Player", "uuid": "e707758b25e049e1a11f4ac39a2fa47d", "updated": 1790232749,
				   "etag": "abc", "data": {"format": 1, "looks": {
				     "accac1fb-a17b-46f7-9925-6aa2aa8e35a6": {"id": "FROZEN_BLAZE_CHESTPLATE", "dye": 255}},
				     "equipped": {}}},
				  {"name": "Rrout", "missing": true}]}
				""").getAsJsonObject());

		assertEquals(Set.of(BEE), players.looks().keySet());
		assertEquals(Set.of("accac1fb-a17b-46f7-9925-6aa2aa8e35a6"), players.looks().get(BEE).looks().keySet());
		assertEquals(List.of("Rrout"), players.missing());
	}

	@Test
	void aMalformedEntryIsLeftOutAndTheRestKept() {
		Poller.Players players = Poller.players(JsonParser.parseString("""
				{"players": [
				  {"name": "NoUuid", "data": {"format": 1, "looks": {}}},
				  {"name": "BadData", "uuid": "0c7c90f92f4d4ffa823bf2fb027614aa", "data": {"format": 7}},
				  7,
				  {"name": "Test_Player", "uuid": "e707758b25e049e1a11f4ac39a2fa47d", "data": {"format": 1}}]}
				""").getAsJsonObject());

		assertEquals(Set.of(BEE), players.looks().keySet());
		assertEquals(List.of(), players.missing());
	}

	@Test
	void aReplyWithoutAPlayerListGivesNothing() {
		Poller.Players players = Poller.players(JsonParser.parseString("{\"error\": \"x\"}").getAsJsonObject());
		assertEquals(Set.of(), players.looks().keySet());
	}
}
