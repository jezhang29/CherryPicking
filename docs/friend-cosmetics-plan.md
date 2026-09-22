# Friend cosmetics: plan

Show a friend's Skyblocker armour cosmetics on your client, and yours on theirs. The data goes
through a small relay that you host on Cloudflare. After a one-time setup, the exchange is
automatic.

Status: **plan only. Nothing is built.** Do the steps in order. Step 0 decides between Plan A and
Plan B.

---

## 1. Goal

- Your friend dyes a helmet, adds a trim or changes a head skin in Skyblocker.
- In about 2 minutes, you see the change on your friend in the game. You do nothing.
- Each player types the other's **Minecraft username** one time. There are no accounts, tokens or
  passwords for the players.

### Display-only check

The feature changes only how armour **looks** on your screen. It does not click, move, send chat or
synthesise input. This is permitted by the constraint in `CLAUDE.md`.

---

## 2. What Skyblocker stores (facts from its source)

Skyblocker saves every cosmetic in `config/skyblocker.json`, under `general`. Every map is keyed by
the item's **SkyBlock UUID**, the `uuid` string in the item's `minecraft:custom_data`.

| Skyblocker key                 | Value                                                    | Applied by (Skyblocker mixin)          |
|--------------------------------|----------------------------------------------------------|----------------------------------------|
| `customDyeColors`              | `int` RGB                                                | `DyedItemColor.getOrDefault`           |
| `customAnimatedDyes`           | `{keyframes:[{color,time}], cycleBack, delay, duration}` | `DyedItemColor.getOrDefault`           |
| `customArmorTrims`             | `{material: Identifier, pattern: Identifier}`            | `DataComponentHolder.get` (`TRIM`)     |
| `customHelmetTextures`         | base64 texture property                                  | `DataComponentHolder.get` (`PROFILE`)  |
| `customAnimatedHelmetTextures` | id in Skyblocker's online animated-heads list            | `DataComponentHolder.get` (`PROFILE`)  |
| `customGlint`                  | `boolean`                                                | `DataComponentHolder.get` (`ENCHANTMENT_GLINT_OVERRIDE`) |
| `customArmorModel`             | `Identifier` of an equipment asset                       | `EquipmentLayerRenderer.renderLayers`  |

Source files, in `SkyblockerMod/Skyblocker`:

- `mixins/DataComponentHolderMixin.java`
- `mixins/DyedItemColorMixin.java`
- `mixins/EquipmentLayerRendererMixin.java`
- `skyblock/item/custom/CustomArmorAnimatedDyes.java` (animation maths)
- `skyblock/item/custom/CustomHelmetTextures.java` (`getProfile`: texture string to `ResolvableProfile`)
- `config/configs/GeneralConfig.java` (the maps)

Skyblocker is LGPL-3.0, and so is this mod. You can port its code with attribution.

Skyblocker's mixins check **every** item stack, not only your inventory. So if Hypixel sends the
item UUID for armour on other players, a lookup by item UUID also works on a friend. Step 0 checks
this.

---

## 3. Architecture

```
 Friend's client                     Cloudflare Worker + KV                    Your client
 ---------------                     ----------------------                    -----------
 skyblocker.json ──read──► Publisher ──PUT /cosmetics──►  c:<uuid> = data
                                                          n:<name> = uuid
                                                                  ◄──GET /cosmetics?names=──  Poller
                                                                                                │
                                                                              FriendCosmetics map
                                                                                                │
                                                                        mixins change dye/trim/head
```

- **Relay:** a Cloudflare Worker with one KV namespace. It stores the latest cosmetics for each
  player. Only the UUIDs on its allowlist (you and your friend) can write or read.
- **Identity:** the standard Minecraft login check (Mojang's `joinServer` / `hasJoined`). The
  client proves who it is to Mojang, and the relay asks Mojang to confirm. The access token goes
  only to Mojang, never to the relay.
- **Client:** it reads your `skyblocker.json`, publishes it when it changes, fetches your friends'
  data, and applies it in its own mixins.

---

## 4. Step 0: check what Hypixel sends (do this first)

### 4.1 Build a debug command

Add `/cherry debug armor <player>`. It finds the named player in `Minecraft.getInstance().level`
and prints to chat, for each armour slot (`HEAD`, `CHEST`, `LEGS`, `FEET`):

- the item id,
- `minecraft:custom_data` (the full tag),
- whether `custom_data` has a `uuid` string.

This command reads only. It is a debug tool, not a setting, so it is allowed as a subcommand.

### 4.2 Test

1. Stand near your friend in a hub, with the friend wearing SkyBlock armour.
2. Run `/cherry debug armor <friend>`.
3. Run it on yourself too, to compare.

### 4.3 Decide

| Result                                          | Next                    |
|-------------------------------------------------|-------------------------|
| Friend's armour has `uuid`, same as in their own inventory | **Plan A** (section 7.1) |
| Friend's armour has no `uuid`, or a different one           | **Plan B** (section 7.2) |

Add this as a check in `docs/in-game-checks.md`, in that file's format, with `Verdict: OPEN`.

---

## 5. The relay (Cloudflare Worker)

### 5.1 One-time setup (dashboard only, no command line)

1. Make a free account at <https://dash.cloudflare.com>.
2. **Storage & Databases → KV → Create.** Name it `cherry-cosmetics`.
3. **Compute (Workers) → Create → Start with Hello World.** Name it `cherry-relay`. Deploy.
4. Open the worker. **Edit code.** Replace everything with the code in 5.4. Deploy.
5. **Settings → Bindings → Add → KV namespace.** Variable name `COSMETICS`, namespace
   `cherry-cosmetics`.
6. **Settings → Variables and Secrets:**
   - `SECRET` (type **Secret**): a long random string. For example, the output of
     `openssl rand -hex 32`.
   - `ALLOWED` (type **Text**): your UUID and your friend's UUID, with no dashes, separated by a
     comma. Find a UUID at `https://api.mojang.com/users/profiles/minecraft/<name>` (the `id` field).
7. Deploy again. Note the URL, for example `https://cherry-relay.<you>.workers.dev`.
8. Check: open `<url>/challenge?name=<yourname>` in a browser. You see JSON with `serverId` and `ts`.

To add a friend later: add their UUID to `ALLOWED`, and deploy.

### 5.2 Endpoints

| Method + path                      | Auth   | Body / query                   | Reply                                      |
|------------------------------------|--------|--------------------------------|--------------------------------------------|
| `GET /challenge?name=<n>`          | none   | —                              | `{serverId, ts}`                           |
| `POST /login`                      | none   | `{name, ts}`                   | `{token, uuid, name, expires}` or `403`    |
| `PUT /cosmetics`                   | Bearer | the payload (section 6), ≤ 64 KB | `204`                                    |
| `GET /cosmetics?names=<a>,<b>`     | Bearer | `If-None-Match` optional       | `{players:[…]}` + `ETag`, or `304`         |

### 5.3 Login flow

1. Client: `GET /challenge?name=<you>`. The relay returns `serverId = HMAC(SECRET, name:ts)`,
   cut to 40 hex characters.
2. Client, on a background thread:
   `Minecraft.getInstance().services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId)`.
   This is the same call the game makes when you join any server.
3. Client: `POST /login {name, ts}`.
4. Relay: checks that `ts` is less than 60 s old, computes `serverId` again, and calls
   `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=<name>&serverId=<serverId>`.
5. Mojang returns `200` with `{id, name}` only if step 2 happened. The relay checks that `id` is in
   `ALLOWED`, then returns a token that is valid for 24 h: `uuid.name.expires.HMAC`.
6. The client keeps the token in memory only. When it gets a `401`, it logs in again.

**Why the relay makes the `serverId`:** if the client chose it, a bad Minecraft server that you
join could use your `joinServer` call to log in to the relay as you. The HMAC makes a `serverId`
that only the relay can make.

### 5.4 Worker code

```js
// cherry-relay: stores each allowed player's Skyblocker cosmetics.
// Bindings: COSMETICS (KV). Secrets/vars: SECRET, ALLOWED (comma-separated undashed UUIDs).

const enc = new TextEncoder();
const NAME = /^[A-Za-z0-9_]{1,16}$/;
const MAX_BODY = 64 * 1024;
const TOKEN_TTL = 24 * 3600;

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    try {
      if (req.method === "GET" && url.pathname === "/challenge") return await challenge(url, env);
      if (req.method === "POST" && url.pathname === "/login") return await login(req, env);
      if (req.method === "PUT" && url.pathname === "/cosmetics") return await publish(req, env);
      if (req.method === "GET" && url.pathname === "/cosmetics") return await fetchMany(req, url, env);
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
  const { name, ts } = await req.json();
  if (!NAME.test(name ?? "") || typeof ts !== "number" || Math.abs(now() - ts) > 60) {
    return json({ error: "bad challenge" }, 400);
  }
  const id = await serverId(env, name, ts);
  const r = await fetch("https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
      + encodeURIComponent(name) + "&serverId=" + id);
  if (r.status !== 200) return json({ error: "not verified" }, 403);
  const profile = await r.json();
  if (!allowed(env).has(profile.id)) return json({ error: "not allowed" }, 403);
  const expires = now() + TOKEN_TTL;
  const body = `${profile.id}.${profile.name}.${expires}`;
  const token = `${body}.${await hmac(env.SECRET, "token:" + body)}`;
  return json({ token, uuid: profile.id, name: profile.name, expires });
}

async function publish(req, env) {
  const who = await auth(req, env);
  if (!who) return json({ error: "unauthorised" }, 401);
  const text = await req.text();
  if (text.length > MAX_BODY) return json({ error: "too large" }, 413);
  const data = JSON.parse(text);
  if (data?.format !== 1) return json({ error: "bad format" }, 400);

  const etag = (await sha256(text)).slice(0, 16);
  const old = await env.COSMETICS.get("c:" + who.uuid, "json");
  if (old?.etag !== etag) {
    await env.COSMETICS.put("c:" + who.uuid,
        JSON.stringify({ name: who.name, updated: now(), etag, data }));
  }
  if (old?.name !== who.name) {
    await env.COSMETICS.put("n:" + who.name.toLowerCase(), who.uuid);
  }
  return new Response(null, { status: 204 });
}

async function fetchMany(req, url, env) {
  if (!(await auth(req, env))) return json({ error: "unauthorised" }, 401);
  const names = (url.searchParams.get("names") ?? "").split(",").filter((n) => NAME.test(n)).slice(0, 10);
  const players = [];
  for (const name of names) {
    const uuid = await env.COSMETICS.get("n:" + name.toLowerCase());
    const entry = uuid ? await env.COSMETICS.get("c:" + uuid, "json") : null;
    players.push(entry
        ? { name: entry.name, uuid, updated: entry.updated, etag: entry.etag, data: entry.data }
        : { name, missing: true });
  }
  const etag = '"' + (await sha256(players.map((p) => p.etag ?? "-").join(","))).slice(0, 16) + '"';
  if (req.headers.get("If-None-Match") === etag) {
    return new Response(null, { status: 304, headers: { ETag: etag } });
  }
  return json({ players }, 200, { ETag: etag });
}

async function auth(req, env) {
  const header = req.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const parts = header.slice(7).split(".");
  if (parts.length !== 4) return null;
  const [uuid, name, expires, sig] = parts;
  if (Number(expires) < now() || !allowed(env).has(uuid)) return null;
  if (sig !== await hmac(env.SECRET, `token:${uuid}.${name}.${expires}`)) return null;
  return { uuid, name };
}

const now = () => Math.floor(Date.now() / 1000);
const allowed = (env) => new Set((env.ALLOWED ?? "").split(",").map((s) => s.trim()).filter(Boolean));
const serverId = async (env, name, ts) => (await hmac(env.SECRET, `challenge:${name.toLowerCase()}:${ts}`)).slice(0, 40);
const hex = (buf) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
const sha256 = async (s) => hex(await crypto.subtle.digest("SHA-256", enc.encode(s)));

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
```

Notes on the code:

- The signature check uses a plain string compare. That is acceptable for a two-player relay. For a
  larger group, use `crypto.subtle.verify`, which compares in constant time.
- KV is "eventually consistent": a write can take up to 60 s to reach every Cloudflare location.
  With a 2-minute poll, this does not matter.

### 5.5 Free-plan limits

| Limit (per day)       | Free plan | Two players use              |
|-----------------------|-----------|------------------------------|
| Worker requests       | 100,000   | about 1,500                  |
| KV reads              | 100,000   | about 3,000 (2 per friend per poll) |
| KV writes             | **1,000** | a few (only when data changes) |

KV writes are the tightest limit. The relay writes only when the data changes (the `etag` check),
and the client publishes only when `skyblocker.json` changes. Do not remove either check.

---

## 6. Payload format (version 1)

The client builds this from `skyblocker.json`. It keeps only the keys in section 2.

```json
{
  "format": 1,
  "items": {
    "<skyblock item uuid>": {
      "dye": 16711680,
      "animatedDye": { "keyframes": [{ "color": 16711680, "time": 0.0 }], "cycleBack": true, "delay": 0.0, "duration": 2.0 },
      "trim": { "material": "minecraft:gold", "pattern": "minecraft:sentry" },
      "helmetTexture": "<base64 texture property>",
      "animatedHelmet": "<animated head id>",
      "glint": true,
      "armorModel": "minecraft:netherite"
    }
  },
  "equipped": {
    "head": "<item uuid>", "chest": "<item uuid>", "legs": "<item uuid>", "feet": "<item uuid>"
  }
}
```

- Every field in an item is optional.
- `equipped` is needed only for Plan B. With Plan A, leave it out.
- Size: one helmet texture is about 300–600 characters. 64 KB is enough for about 100 customised
  items. If the payload is too large, send only the items with a helmet texture that are in
  `equipped`, and log a warning.

---

## 7. Client side

### 7.1 Plan A: look up by item UUID

New package `jeff.cherrypicking.client.cosmetics`:

| Class              | Job                                                                                         |
|--------------------|---------------------------------------------------------------------------------------------|
| `Cosmetic`         | A record for one item: the fields in section 6. It has a Mojang `Codec`.                    |
| `SkyblockerFile`   | Reads `config/skyblocker.json` with Gson and returns `Map<String, Cosmetic>`. It imports no Skyblocker class. If the file or a key is missing, it returns an empty map. |
| `RelayClient`      | `java.net.http.HttpClient`, async, 10 s timeout. `challenge`, `login`, `publish`, `fetch`. It keeps the token and the last `ETag`. Copy the builder pattern from `skyblock-flipper-26.2/.../core/api/HypixelApi.java`. |
| `Publisher`        | Every 5 s (on a client tick counter): reads the file's modified time. If it changed, it reads the file and hashes the payload. If the hash changed, it waits 10 s (debounce), then publishes. It also publishes one time after login. |
| `Poller`           | On Skyblock join, then every `pollSeconds`: fetches all friends. On `200`, it replaces the map. On `304`, it does nothing. |
| `FriendCosmetics`  | The live data: a `volatile Map<String, Cosmetic>` from item UUID to cosmetic, merged from all friends. It is replaced as a whole, never edited in place, so render threads never see a half-built map. |
| `Looks`            | Turns a `Cosmetic` into Minecraft objects, with caches: `ArmorTrim` from the client registries, `ResolvableProfile` from a texture (port `CustomHelmetTextures.getProfile`), the animated dye colour (port `CustomArmorAnimatedDyes`). |
| `ItemUuid`         | Reads `uuid` from `minecraft:custom_data`. This mod does not depend on Skyblocker, so it cannot use Skyblocker's `stack.getUuid()`. |

New mixins in `jeff.cherrypicking.mixin` (add them to the `client` list in `cherrypicking.mixins.json`):

| Mixin                        | Target                                                                    | Change                                |
|------------------------------|---------------------------------------------------------------------------|---------------------------------------|
| `FriendDyeMixin`             | `DyedItemColor.getOrDefault(ItemStack, int)` (static), `@ModifyReturnValue` | dye or animated dye                  |
| `FriendComponentMixin`       | `DataComponentHolder.get(DataComponentType)` (default method), `@ModifyReturnValue` | `TRIM`, `PROFILE` (player heads only), `ENCHANTMENT_GLINT_OVERRIDE` |
| `FriendArmorModelMixin`      | `EquipmentLayerRenderer.renderLayers(...)`, the 11-argument overload, `@ModifyVariable` on the `ResourceKey<EquipmentAsset>` | armour model |

These signatures were checked against the 26.2 jar. `@ModifyReturnValue` comes from MixinExtras,
which Fabric Loader includes.

**Performance.** `DataComponentHolder.get` is one of the most-called methods in the game. Exit
early, in this order:

1. The component type is not one of the three types → return.
2. The feature is off, or `FriendCosmetics` is empty → return.
3. `this` is not an `ItemStack` → return.
4. Only now read the UUID. Cache it on the stack with an `@Unique` field in a small `ItemStack`
   mixin, as Skyblocker does in `ItemStackMixin`.

**Order with Skyblocker's mixins.** Both mods change the same return values. This is not a
conflict: your own items are in your Skyblocker config, and your friend's items are in
`FriendCosmetics`. Their UUIDs are different. So for any one stack, only one of the two mods makes
a change.

**Only on Skyblock.** No general "on Skyblock" check exists in this mod now (`DungeonState` checks
only the Catacombs). Add one: `SharedLocraw` gives `gametype == "SKYBLOCK"` when coalroutegenerator
is present. Without it, use the sidebar title from `ScoreboardReader`.

### 7.2 Plan B: look up by player and slot (only if step 0 fails)

- The publisher also fills `equipped`. It reads your armour slots from `Minecraft.getInstance().player`.
- A friend's client keeps `Map<playerUuid, Map<slot, Cosmetic>>`.
- The mixins cannot use the item stack alone, because the stack does not say who wears it. Hook
  one step earlier, where the entity is still known: the step where a player's armour goes into its
  render state (`HumanoidArmorLayer` / the avatar render-state extraction). Find the exact 26.2
  method with `javap` before you write code. Put the friend's UUID in a thread-local, or in a field
  on the render state, and read it in the three mixins.
- Disadvantage: when your friend changes armour, your view is wrong until their next publish. The
  publisher must also publish when the equipped UUIDs change, not only when the file changes.

---

## 8. Settings (add to `client.config.Settings`)

New `Section`: `FRIEND_COSMETICS("Cosmetics", "Friends")`.

| Key                           | Label                   | Control     | Default | One-sentence description                                    |
|-------------------------------|-------------------------|-------------|---------|-------------------------------------------------------------|
| `friendCosmetics.enabled`     | Show friends' cosmetics | Flag        | off     | Shows your friends' Skyblocker armour looks, and shares yours with them. |
| `friendCosmetics.friends`     | Friends                 | **Text**    | empty   | The Minecraft names of the friends whose armour looks you want to see, separated by commas. |
| `friendCosmetics.relayUrl`    | Relay address           | **Text**    | your worker URL | The web address of the relay that passes looks between you and your friends. |
| `friendCosmetics.pollSeconds` | Check every             | Whole 30–600, step 30, "s" | 120 | How often to fetch your friends' latest looks. |
| `friendCosmetics.share`       | Share my cosmetics      | Flag        | on      | Sends your own Skyblocker armour looks to the relay, so your friends can see them. |
| `friendCosmetics.dyes`        | Dyes                    | Flag        | on      | Shows your friends' custom and animated armour dyes.        |
| `friendCosmetics.trims`       | Trims                   | Flag        | on      | Shows your friends' custom armour trims.                    |
| `friendCosmetics.heads`       | Helmet skins            | Flag        | on      | Shows your friends' custom helmet skins.                    |
| `friendCosmetics.models`      | Armour models           | Flag        | on      | Shows your friends' custom armour models, if you have the same resource pack. |

Put all but `enabled` inside `when(FriendCosmeticsConfig::enabled, …)`.

Add one `Action`: **"Reconnect relay"**. It forgets the token, logs in again and fetches now.

**New work: a text control.** `Control` has no text kind now (only `Flag`, `Whole`, `Real`,
`Choice`, `Colour`). Add `record Text(int maxLength) implements Control<String>`. The switch in the
screen builder is exhaustive, so the compiler shows every place that must draw it. Add a text field
widget in `client.screen`. Do not add a `/cherry friend add` command instead: `CLAUDE.md` says that
commands do not set values.

---

## 9. Safety checks on friends' data

A friend's data comes from the network. Check all of it before it reaches a renderer.

| Field            | Check                                                                                  |
|------------------|----------------------------------------------------------------------------------------|
| whole payload    | ≤ 64 KB. `format == 1`. At most 500 items. If it does not parse, keep the old data and log one warning. |
| item UUID key    | Matches `^[0-9a-f-]{36}$`.                                                             |
| `helmetTexture`  | Base64 that decodes to JSON with `textures.SKIN.url` on `http(s)://textures.minecraft.net/`. Otherwise, discard it. |
| `trim`           | The material and pattern exist in the client's registries. Otherwise, discard it.      |
| `armorModel`     | A valid `Identifier`. If the equipment asset is missing, vanilla draws nothing, so check that the asset exists first. |
| `animatedDye`    | 1–32 keyframes, `duration` 0.1–60 s, colours masked to `0xFFFFFF`.                     |
| `animatedHelmet` | Known in the animated-heads list. If you do not load that list, discard the field.     |

---

## 10. Failure behaviour

| Case                                      | Behaviour                                                              |
|-------------------------------------------|------------------------------------------------------------------------|
| Relay is down, or there is no internet    | Keep the last data. Try again at the next poll. Log one line, not one per poll. |
| `joinServer` fails (offline account, expired session) | Log one line. Try again after 10 minutes.                  |
| `403 not allowed`                         | Log: "Relay: your UUID is not in ALLOWED". Stop trying until "Reconnect relay". |
| Friend has never published                | `missing: true`. Show nothing for that friend.                         |
| Skyblocker not installed                  | Publish nothing. Showing friends still works.                          |
| Feature turned off                        | Stop both loops, clear `FriendCosmetics` and the token.                |

Run all network and file work off the client thread. Only the map swap in `FriendCosmetics` affects
rendering, and it is one reference write.

---

## 11. Build order

| # | Work                                                                                    | Done when                                                        |
|---|-----------------------------------------------------------------------------------------|------------------------------------------------------------------|
| 0 | `/cherry debug armor <player>` and the in-game test                                    | You know Plan A or Plan B.                                       |
| 1 | `Cosmetic`, `SkyblockerFile`, `ItemUuid`, `Looks` (static dye, trim, head, glint), the three mixins. Load friend data from a **local test file** that you copy from your friend. | Your friend's colours show on them in the game, with no network. |
| 2 | Deploy the Worker (section 5). Test `/challenge` in a browser.                          | The browser shows `serverId`.                                    |
| 3 | `RelayClient` login and `Publisher`.                                                    | In the Cloudflare dashboard, KV shows `c:<your uuid>`.           |
| 4 | `Poller` and `FriendCosmetics` swap. Remove the local test file.                        | A dye change on your friend shows for you in about 2 minutes.    |
| 5 | Settings, the `Text` control, "Reconnect relay".                                        | All settings show and save.                                      |
| 6 | Animated dyes, then armour models, then animated helmets.                               | Each one shows on a friend.                                      |
| 7 | Plan B, only if step 0 failed.                                                          | —                                                                |

After each step: `./gradlew build`, then test in the real client on Hypixel. Add the checks that
only the game can confirm to `docs/in-game-checks.md`, in that file's format.

---

## 12. In-game checks to add (draft)

Copy these into `docs/in-game-checks.md` when you build each step. Give them IDs in that file's
format.

1. **Friend's armour has a UUID.** `/cherry debug armor <friend>` prints a `uuid` for each
   SkyBlock armour piece.
2. **Static dye shows.** The friend dyes their chestplate red in Skyblocker. Within 2 minutes it is
   red on your screen.
3. **Trim shows.** Same as 2, for a trim.
4. **Helmet skin shows.** Same as 2, for a helmet texture.
5. **Your own looks do not change.** With the feature on, your own Skyblocker looks are the same
   as with it off.
6. **Relay down.** Turn off the internet for 5 minutes. The friend's last looks stay. The log shows
   one relay warning, not one per poll.
7. **No FPS loss.** In a busy hub, FPS with the feature on is within about 5% of FPS with it off.

---

## 13. Open questions

1. Does Hypixel send the item `uuid` for other players' armour? (Step 0.)
2. ~~When does Skyblocker write `skyblocker.json`?~~ **Answered from the source:**
   `CustomizeScreen.onClose()` calls `SkyblockerConfigManager.update(...)`, which saves the file.
   So the file changes when you close the customise screen, and the file-time check in `Publisher`
   is enough. (If you leave with a key that skips `onClose`, the save can wait until the next config
   save. Check this in the game.)
3. Does Hypixel's dye on some armour (for example, dyes from the Dye item) use `DyedItemColor`? If
   the vanilla component is missing, `getOrDefault` still returns the default, and the mixin still
   changes it. Confirm this in the game.
