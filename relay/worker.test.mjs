// Tests for the relay's login, storage and live push, run with: node --test relay/worker.test.mjs
// A fake Mojang, fake players and a fake Cloudflare (the hub's storage and sockets) stand in for the
// real ones, so no network is used. The WebSocket upgrade itself needs Cloudflare and is not tested.
import { test } from "node:test";
import assert from "node:assert/strict";
import { createSign, generateKeyPairSync } from "node:crypto";
import worker, { Hub } from "./worker.js";

// Cloudflare's class for the hub's ping auto-response; only its existence matters here.
globalThis.WebSocketRequestResponsePair = class {};

const rsa = () => generateKeyPairSync("rsa", { modulusLength: 2048 });
const mojang = rsa();
const player = rsa();
const stranger = rsa();
const der = (pair) => pair.publicKey.export({ type: "spki", format: "der" });
const ME = "e707758b25e049e1a11f4ac39a2fa47d";
const OTHER = "0c7c90f92f4d4ffa823bf2fb027614aa";

function relay() {
  const store = new Map();
  const sockets = [];
  const env = {
    SECRET: "test secret",
    ALLOWED: ME,
    MOJANG_KEYS: JSON.stringify([der(mojang).toString("base64")]),
  };
  const ctx = {
    storage: {
      get: async (key) => structuredClone(store.get(key)),
      put: async (key, value) => void store.set(key, structuredClone(value)),
    },
    setWebSocketAutoResponse() {},
    acceptWebSocket: (ws) => void sockets.push(ws),
    getWebSockets: () => sockets,
  };
  const hub = new Hub(ctx, env);
  env.HUB = { idFromName: () => "hub", get: () => hub };
  env.hub = hub;
  env.link = (uuid, expires = Math.floor(Date.now() / 1000) + 3600) => {
    const ws = socket({ uuid, expires, names: [] });
    ctx.acceptWebSocket(ws);
    return ws;
  };
  return env;
}

// A hub-side WebSocket that records what the hub sends it.
function socket(attachment) {
  return {
    attachment, sent: [], closed: null,
    serializeAttachment(value) { this.attachment = structuredClone(value); },
    deserializeAttachment() { return structuredClone(this.attachment); },
    send(message) { this.sent.push(JSON.parse(message)); },
    close(code) { this.closed = code; },
  };
}

async function token(env) {
  return (await (await call(env, "POST", "/login", await loginBody(env))).json()).token;
}

const watch = (env, ws, names) => env.hub.webSocketMessage(ws, JSON.stringify({ type: "watch", names }));

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

test("a live link gets its friends' looks, then each change at once", async () => {
  const env = relay();
  const me = await token(env);
  const ws = env.link(ME);

  await watch(env, ws, ["Test_Player"]);
  assert.deepEqual(ws.sent, [{ type: "friends", players: [{ name: "Test_Player", missing: true }] }]);

  const looks = { format: 1, looks: {}, equipped: {} };
  await call(env, "PUT", "/cosmetics", looks, me);
  assert.equal(ws.sent.length, 2);
  assert.equal(ws.sent[1].type, "changed");
  assert.deepEqual(ws.sent[1].players.map((p) => [p.name, p.uuid, p.data]), [["Test_Player", ME, looks]]);

  // The same looks again change nothing, so nothing is pushed.
  await call(env, "PUT", "/cosmetics", looks, me);
  assert.equal(ws.sent.length, 2);
});

test("a link gets only the players it watches", async () => {
  const env = relay();
  const me = await token(env);
  const ws = env.link(ME);
  await watch(env, ws, ["SomeoneElse"]);

  await call(env, "PUT", "/cosmetics", { format: 1, looks: {}, equipped: {} }, me);
  assert.deepEqual(ws.sent.map((m) => m.type), ["friends"]);
});

test("a link with an old token, or of a player no longer allowed, is closed", async () => {
  const env = relay();
  const me = await token(env);
  const expired = env.link(ME, Math.floor(Date.now() / 1000) - 1);
  const removed = env.link(OTHER);
  const current = env.link(ME);
  for (const ws of [expired, current]) ws.attachment.names = ["test_player"];
  removed.attachment.names = ["test_player"];

  await call(env, "PUT", "/cosmetics", { format: 1, looks: {}, equipped: {} }, me);
  assert.equal(expired.closed, 4001);
  assert.equal(removed.closed, 4001);
  assert.deepEqual([expired.sent.length, removed.sent.length, current.sent.length], [0, 0, 1]);
});

test("a watch message that is not one is ignored", async () => {
  const env = relay();
  const ws = env.link(ME);
  await env.hub.webSocketMessage(ws, "not json");
  await env.hub.webSocketMessage(ws, JSON.stringify({ type: "other" }));
  assert.deepEqual(ws.sent, []);
});

test("the live link needs a WebSocket upgrade and a token", async () => {
  const env = relay();
  assert.equal((await call(env, "GET", "/live")).status, 426);
  const reply = await worker.fetch(new Request("https://relay.test/live", { headers: { Upgrade: "websocket" } }), env);
  assert.equal(reply.status, 401);
});

test("the fetch answers not changed with the last ETag", async () => {
  const env = relay();
  const me = await token(env);
  await call(env, "PUT", "/cosmetics", { format: 1, looks: {}, equipped: {} }, me);
  const first = await call(env, "GET", "/cosmetics?names=Test_Player", undefined, me);
  const etag = first.headers.get("ETag");
  const again = await worker.fetch(new Request("https://relay.test/cosmetics?names=Test_Player", {
    headers: { Authorization: "Bearer " + me, "If-None-Match": etag },
  }), env);
  assert.equal(again.status, 304);
});
