// Tests for the relay's login, run with: node --test relay/worker.test.mjs
// A fake Mojang and fake players stand in for the real ones, so no network is used.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createSign, generateKeyPairSync } from "node:crypto";
import worker from "./worker.js";

const rsa = () => generateKeyPairSync("rsa", { modulusLength: 2048 });
const mojang = rsa();
const player = rsa();
const stranger = rsa();
const der = (pair) => pair.publicKey.export({ type: "spki", format: "der" });
const ME = "e707758b25e049e1a11f4ac39a2fa47d";
const OTHER = "0c7c90f92f4d4ffa823bf2fb027614aa";

function relay() {
  const store = new Map();
  return {
    SECRET: "test secret",
    ALLOWED: ME,
    MOJANG_KEYS: JSON.stringify([der(mojang).toString("base64")]),
    COSMETICS: {
      get: async (key, type) => (store.has(key) ? (type === "json" ? JSON.parse(store.get(key)) : store.get(key)) : null),
      put: async (key, value) => void store.set(key, value),
    },
  };
}

function call(env, method, path, body, token) {
  const headers = {};
  if (token) headers.Authorization = "Bearer " + token;
  return worker.fetch(new Request("https://relay.test" + path, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  }), env);
}

// What Mojang signs for a player key, as ProfilePublicKey.Data.signedPayload builds it:
// UUID (16 bytes), expiry in epoch milliseconds (8 bytes, big-endian), then the key's X.509 bytes.
function certificate(uuid, expiresAt, key, signer) {
  const payload = Buffer.alloc(24 + key.length);
  Buffer.from(uuid, "hex").copy(payload, 0);
  payload.writeBigInt64BE(BigInt(expiresAt), 16);
  key.copy(payload, 24);
  return createSign("SHA1").update(payload).sign(signer.privateKey).toString("base64");
}

async function loginBody(env, {
  uuid = ME, certifiedUuid = uuid, key = player, certifiedBy = mojang, signer = player,
  expiresAt = Date.now() + 3_600_000,
} = {}) {
  const challenge = await (await call(env, "GET", "/challenge?name=Test_Player")).json();
  const signature = createSign("SHA256").update("cherry-relay-login:" + challenge.serverId)
      .sign(signer.privateKey).toString("base64");
  return {
    name: "Test_Player", ts: challenge.ts, uuid, expiresAt,
    publicKey: der(key).toString("base64"),
    keySignature: certificate(certifiedUuid, expiresAt, der(key), certifiedBy),
    signature,
  };
}

test("a key Mojang certified, that signed the challenge, logs in and can share looks", async () => {
  const env = relay();
  const reply = await call(env, "POST", "/login", await loginBody(env));
  assert.equal(reply.status, 200);
  const { token, uuid, name } = await reply.json();
  assert.equal(uuid, ME);
  assert.equal(name, "Test_Player");

  const looks = { format: 1, looks: {}, equipped: {} };
  assert.equal((await call(env, "PUT", "/cosmetics", looks, token)).status, 204);
  const fetched = await (await call(env, "GET", "/cosmetics?names=test_player", undefined, token)).json();
  assert.deepEqual(fetched.players[0].data, looks);
});

test("a key Mojang did not certify is refused", async () => {
  const env = relay();
  const reply = await call(env, "POST", "/login", await loginBody(env, { certifiedBy: stranger }));
  assert.equal(reply.status, 403);
  assert.deepEqual(await reply.json(), { error: "not verified", step: "certificate" });
});

test("a certificate for another player's UUID is refused", async () => {
  const env = relay();
  const reply = await call(env, "POST", "/login", await loginBody(env, { certifiedUuid: OTHER }));
  assert.deepEqual(await reply.json(), { error: "not verified", step: "certificate" });
});

test("a challenge signed by another key is refused", async () => {
  const env = relay();
  const reply = await call(env, "POST", "/login", await loginBody(env, { signer: stranger }));
  assert.deepEqual(await reply.json(), { error: "not verified", step: "challenge" });
});

test("an expired key is refused", async () => {
  const env = relay();
  const reply = await call(env, "POST", "/login", await loginBody(env, { expiresAt: Date.now() - 1000 }));
  assert.deepEqual(await reply.json(), { error: "not verified", step: "key expired" });
});

test("a verified player who is not in ALLOWED is refused", async () => {
  const env = relay();
  const reply = await call(env, "POST", "/login", await loginBody(env, { uuid: OTHER }));
  assert.equal(reply.status, 403);
  assert.deepEqual(await reply.json(), { error: "not allowed" });
});

test("the real Mojang keys load", async () => {
  const env = relay();
  delete env.MOJANG_KEYS;
  const reply = await call(env, "POST", "/login", await loginBody(env));
  assert.deepEqual(await reply.json(), { error: "not verified", step: "certificate" });
});
