// cherry-relay: stores each allowed player's Skyblocker cosmetics, and pushes a change at once to
// the friends who are online.
// Bindings: HUB (Durable Object, class Hub; relay/wrangler.jsonc). Vars: SECRET, ALLOWED
// (comma-separated undashed UUIDs), kept on deploy by keep_vars.
// Source of truth: relay/worker.js in the cherrypicking repo. Deploy: cd relay && npx wrangler deploy
// Test: node --test relay/worker.test.mjs

const enc = new TextEncoder();
const NAME = /^[A-Za-z0-9_]{1,16}$/;
const MAX_BODY = 64 * 1024;
const TOKEN_TTL = 24 * 3600;
const UUID = /^[0-9a-f]{32}$/;
const RSA_SHA1 = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-1" };
const RSA_SHA256 = { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" };

// Mojang's player-certificate keys, from https://api.minecraftservices.com/publickeys
// ("playerCertificateKeys"). The relay cannot fetch them: Mojang refuses requests from Cloudflare.
// A MOJANG_KEYS variable (a JSON list) replaces these; the tests use that.
const MOJANG_KEYS = [
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==",
];

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (req.method === "GET" && url.pathname === "/challenge") return await challenge(url, env);
      if (req.method === "POST" && url.pathname === "/login") return await login(req, env);
      if (req.method === "PUT" && url.pathname === "/cosmetics") return await publish(req, env);
      if (req.method === "GET" && url.pathname === "/cosmetics") return await fetchMany(req, url, env);
      if (req.method === "GET" && url.pathname === "/live") return await live(req, env);
      return json({ error: "not found" }, 404);
    } catch (e) {
      return json({ error: "server error" }, 500);
    }
  },
};

async function challenge(url, env) {
  const name = url.searchParams.get("name") ?? "";
  if (!NAME.test(name)) return json({ error: "bad name" }, 400);
  const ts = now();
  return json({ serverId: await serverId(env, name, ts), ts });
}

async function login(req, env) {
  const { name, ts, uuid, expiresAt, publicKey, keySignature, signature } = await req.json();
  if (!NAME.test(name ?? "") || typeof ts !== "number" || Math.abs(now() - ts) > 60) {
    return json({ error: "bad challenge" }, 400);
  }
  if (!UUID.test(uuid ?? "") || typeof expiresAt !== "number" || typeof publicKey !== "string"
      || typeof keySignature !== "string" || typeof signature !== "string") {
    return json({ error: "bad login" }, 400);
  }
  if (expiresAt < Date.now()) return json({ error: "not verified", step: "key expired" }, 403);

  // 1. Mojang signed this player key for this UUID: the game's chat-signing certificate.
  const key = base64(publicKey);
  const certified = concat(hexBytes(uuid), longBytes(expiresAt), key);
  if (!(await signedByMojang(env, certified, base64(keySignature)))) {
    return json({ error: "not verified", step: "certificate" }, 403);
  }
  // 2. The client holds that key's private half: it signed this relay's challenge.
  const player = await crypto.subtle.importKey("spki", key, RSA_SHA256, false, ["verify"]);
  const challenge = enc.encode("cherry-relay-login:" + await serverId(env, name, ts));
  if (!(await crypto.subtle.verify("RSASSA-PKCS1-v1_5", player, base64(signature), challenge))) {
    return json({ error: "not verified", step: "challenge" }, 403);
  }
  if (!allowed(env).has(uuid)) return json({ error: "not allowed" }, 403);

  // The name is the player's own claim; Mojang is not asked. Only allowed players get this far.
  const expires = now() + TOKEN_TTL;
  const body = `${uuid}.${name}.${expires}`;
  const token = `${body}.${await hmac(env.SECRET, "token:" + body)}`;
  return json({ token, uuid, name, expires });
}

// True if one of Mojang's player-certificate keys signed data (SHA1withRSA).
async function signedByMojang(env, data, sig) {
  const keys = env.MOJANG_KEYS ? JSON.parse(env.MOJANG_KEYS) : MOJANG_KEYS;
  for (const text of keys) {
    const mojang = await crypto.subtle.importKey("spki", base64(text), RSA_SHA1, false, ["verify"]);
    if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", mojang, sig, data)) return true;
  }
  return false;
}

async function publish(req, env) {
  const who = await auth(req, env);
  if (!who) return json({ error: "unauthorized" }, 401);
  const text = await req.text();
  if (text.length > MAX_BODY) return json({ error: "too large" }, 413);
  const data = JSON.parse(text);
  if (data?.format !== 1) return json({ error: "bad format" }, 400);
  return hub(env).fetch(inner("/publish", who, { method: "POST", body: text }));
}

async function fetchMany(req, url, env) {
  if (!(await auth(req, env))) return json({ error: "unauthorized" }, 401);
  const names = nameList(url.searchParams.get("names"));
  const { players } = await (await hub(env).fetch(inner("/players?names=" + names.join(",")))).json();
  const etag = '"' + (await sha256(players.map((p) => p.etag ?? "-").join(","))).slice(0, 16) + '"';
  if (req.headers.get("If-None-Match") === etag) {
    return new Response(null, { status: 304, headers: { ETag: etag } });
  }
  return json({ players }, 200, { ETag: etag });
}

// The live link: a WebSocket that the hub keeps, so it can push a friend's looks the moment they
// change.
async function live(req, env) {
  if (req.headers.get("Upgrade") !== "websocket") return json({ error: "expected a WebSocket" }, 426);
  const who = await auth(req, env);
  if (!who) return json({ error: "unauthorized" }, 401);
  return hub(env).fetch(inner("/live", who, { headers: { Upgrade: "websocket" } }));
}

// All players share one hub: a few friends fit easily in one Durable Object.
const hub = (env) => env.HUB.get(env.HUB.idFromName("hub"));

// A request from this Worker to the hub. The hub trusts X-Player, because only this Worker can
// reach it, and only after auth.
function inner(path, who, init = {}) {
  const headers = { ...init.headers };
  if (who) headers["X-Player"] = JSON.stringify(who);
  return new Request("https://hub" + path, { ...init, headers });
}

async function auth(req, env) {
  const header = req.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const parts = header.slice(7).split(".");
  if (parts.length !== 4) return null;
  const [uuid, name, expires, sig] = parts;
  if (Number(expires) < now() || !allowed(env).has(uuid)) return null;
  if (sig !== await hmac(env.SECRET, `token:${uuid}.${name}.${expires}`)) return null;
  return { uuid, name, expires: Number(expires) };
}

const now = () => Math.floor(Date.now() / 1000);
const nameList = (text) => (text ?? "").split(",").filter((n) => NAME.test(n)).slice(0, 10);
const allowed = (env) => new Set((env.ALLOWED ?? "").split(",").map((s) => s.trim()).filter(Boolean));
const serverId = async (env, name, ts) => (await hmac(env.SECRET, `challenge:${name.toLowerCase()}:${ts}`)).slice(0, 40);
const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const sha256 = async (s) => hex(await crypto.subtle.digest("SHA-256", enc.encode(s)));
const base64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const hexBytes = (h) => Uint8Array.from(h.match(/../g), (b) => parseInt(b, 16));
const concat = (...parts) => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of parts) { out.set(p, at); at += p.length; }
  return out;
};
const longBytes = (n) => {
  const out = new Uint8Array(8);
  new DataView(out.buffer).setBigInt64(0, BigInt(n));
  return out;
};

async function hmac(secret, msg) {
  const key = await crypto.subtle.importKey("raw", enc.encode(secret),
      { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(await crypto.subtle.sign("HMAC", key, enc.encode(msg)));
}

function json(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json", ...headers },
  });
}

// The one Durable Object. It stores each player's looks ("c:<uuid>", and "n:<lower name>" to find
// the uuid), and holds the live link of each player online, with the names that link watches. A
// changed publish is pushed at once to every link that watches that player. Only the Worker above
// reaches it, after checking the token.
export class Hub {
  constructor(ctx, env) {
    this.ctx = ctx;
    this.env = env;
    // Cloudflare answers the client's ping without waking the hub, so an idle link costs nothing.
    ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  async fetch(req) {
    const url = new URL(req.url);
    const who = JSON.parse(req.headers.get("X-Player") ?? "null");
    if (url.pathname === "/publish") return await this.publish(who, await req.text());
    if (url.pathname === "/players") {
      return json({ players: await this.players(nameList(url.searchParams.get("names"))) });
    }
    if (url.pathname === "/live") {
      const [client, server] = Object.values(new WebSocketPair());
      this.ctx.acceptWebSocket(server);
      server.serializeAttachment({ uuid: who.uuid, expires: who.expires, names: [] });
      return new Response(null, { status: 101, webSocket: client });
    }
    return json({ error: "not found" }, 404);
  }

  async publish(who, text) {
    const storage = this.ctx.storage;
    const old = await storage.get("c:" + who.uuid);
    if (old?.name !== who.name) await storage.put("n:" + who.name.toLowerCase(), who.uuid);
    const etag = (await sha256(text)).slice(0, 16);
    if (old?.etag !== etag) {
      const entry = { name: who.name, updated: now(), etag, data: JSON.parse(text) };
      await storage.put("c:" + who.uuid, entry);
      this.push(who.uuid, entry);
    }
    return new Response(null, { status: 204 });
  }

  async players(names) {
    const players = [];
    for (const name of names) {
      const uuid = await this.ctx.storage.get("n:" + name.toLowerCase());
      const entry = uuid ? await this.ctx.storage.get("c:" + uuid) : null;
      players.push(entry ? shown(uuid, entry) : { name, missing: true });
    }
    return players;
  }

  push(uuid, entry) {
    const message = JSON.stringify({ type: "changed", players: [shown(uuid, entry)] });
    for (const ws of this.ctx.getWebSockets()) {
      const link = ws.deserializeAttachment();
      if (this.valid(ws, link) && link.names.includes(entry.name.toLowerCase())) send(ws, message);
    }
  }

  // A link whose token ran out, or whose player left ALLOWED, is closed; the client logs in again.
  valid(ws, link) {
    if (link.expires >= now() && allowed(this.env).has(link.uuid)) return true;
    ws.close(4001, "log in again");
    return false;
  }

  // The client's one message: {"type":"watch","names":[...]}, its friend list. The reply is those
  // friends' looks now, the same list GET /cosmetics gives.
  async webSocketMessage(ws, message) {
    const link = ws.deserializeAttachment();
    if (!this.valid(ws, link)) return;
    let request;
    try {
      request = JSON.parse(message);
    } catch {
      return;
    }
    if (request?.type !== "watch" || !Array.isArray(request.names)) return;
    const names = nameList(request.names.join(","));
    ws.serializeAttachment({ ...link, names: names.map((n) => n.toLowerCase()) });
    send(ws, JSON.stringify({ type: "friends", players: await this.players(names) }));
  }

  webSocketClose(ws, code, reason) {
    try {
      ws.close(code, reason);
    } catch {
      // The codes 1005 and 1006 only report a close and cannot be sent back; nothing to answer.
    }
  }
}

const shown = (uuid, entry) =>
  ({ name: entry.name, uuid, updated: entry.updated, etag: entry.etag, data: entry.data });

// A link can close while a message is on its way; the client connects again by itself.
function send(ws, message) {
  try {
    ws.send(message);
  } catch {
    // Closed: nothing to send to.
  }
}
