<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# API Token (replaces the old Hash)

The old "Hash" field is now an **API token**: a UUID the player enters in the THM tab. It is sent
with every THM API POST **twice** — as an `Authorization` header (validation) and inside the
request body (identification).

## Client changes

### `src/main/java/xyz/thm/addon/system/THMSystem.java`

- Setting group `"Hash"` → `"API Token"`.
- Setting `Hash` (default `"SetYourHash"`) → `api-token` (default `""`).
  **Renaming the setting resets saved values** — every user re-enters their token once.
- `getHash()` → `getApiToken()` (returns the trimmed value).
- New `hasApiToken()` — `UUID.fromString(...)` in a try/catch. Blank *or* malformed counts as
  "no token set", so a mistyped token never reaches the API.
- The `thm-cape` `onChanged` handler returns early when `!hasApiToken()`.

### `src/main/java/xyz/thm/addon/modules/HighwayBuilderTHM.java`

- `sendStatusLog()` gate: `hash == "SetYourHash" || hash == ""` → `!THMSystem.get().hasApiToken()`.
  Warning text: `"Status not sent. No valid API token set."`
- API statistics gate: same replacement. Warning text:
  `"API not sent. No valid API token set."`
- Stats-decision log reason `skipped-missing-hash` → `skipped-missing-token`.
- Both payloads now interpolate `getApiToken()`; **field order and format are unchanged**.

### `build.gradle.kts` (the `generateAPIUtils` generator)

- New randomly-named private accessor that reads `THMSystem.get().getApiToken()` (null/exception
  safe, returns `""`). This is why `sendStatus(String)` / `sendStatistics(String)` keep their
  one-argument public signatures.
- The shared API POST helper and `postCapeSelection` set the `Authorization` header when the token
  is non-empty.
- `postCapeSelection(String username, String cape, String hash)` →
  `postCapeSelection(String username, String cape, String token)`, and its JSON key
  `"hash"` → `"token"`.

## What the server must accept

### 1. Status POST

```
POST <api.status>
Authorization: Bearer <uuid>        ← NEW
Content-Type: application/json

{"content": "<uuid>:<playerName>:<axis>:<blocksBroken>:<blocksPlaced>:<timestamp>"}
```

Body unchanged — the first colon-separated field is the token (previously the hash).

### 2. Statistics POST

```
POST <api.highway>
Authorization: Bearer <uuid>        ← NEW
Content-Type: application/json

{"content": "<uuid>:<playerName>:<server>:<distance>:<blocksBroken>:<blocksPlaced>:<dir>:<timestamp>:<onMainHighway>"}
```

Body unchanged — first field is the token.

### 3. Cape selection POST

```
POST <api.capePost>
Authorization: Bearer <uuid>        ← NEW
Content-Type: application/json

{"username": "...", "cape": "...", "timestamp": 1234567890, "token": "<uuid>"}
```

**Breaking:** the JSON key `"hash"` is now `"token"`. Accept both for one release if you want a
grace period for old clients.

### 4. Unchanged

| Endpoint | Header | Body |
|---|---|---|
| `sendToWebhook` (arbitrary Discord URL) | none | none |
| GET members / highway status / cape list / cape index | none | n/a |

The `Authorization` header is **omitted entirely** when the token is empty, so an unconfigured
client sends an anonymous request rather than `Bearer `.

## Notes

- Header format is `Authorization: Bearer <uuid>` — the standard bearer-token shape.
- The token is always a canonical UUID string (the client rejects anything `UUID.fromString`
  cannot parse before sending), so the server can validate it as a UUID.
- GET endpoints deliberately got no header. Add one if those need per-user validation too.
