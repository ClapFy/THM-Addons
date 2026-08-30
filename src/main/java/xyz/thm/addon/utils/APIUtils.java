/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.MeteorClient;
import xyz.thm.addon.THMAddon;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Talks to the THM HTTP API. Endpoint URLs are injected at build time from
 * {@code secrets.properties} via {@link GeneratedApiEndpoints}; they are never
 * accepted from remote JSON. Response bodies are treated as untrusted data.
 */
public final class APIUtils {
    private static final Gson GSON = new Gson();
    private static final int MAX_MEMBERS = 4_000;
    private static final int MAX_USERNAMES_PER_MEMBER = 32;
    private static final int MAX_CAPES = 64;
    private static final int MAX_HIGHWAY_ROWS = 8_000;

    private static volatile String cachedStatsKey;

    private APIUtils() {}

    /**
     * Key for local HighwayBuilder stats-cache encryption. Not sent to the API.
     * Persisted under {@code meteor-client/thm/stats-key} so encrypted caches
     * survive client restarts and addon rebuilds.
     */
    public static String getPassword() {
        String existing = cachedStatsKey;
        if (existing != null) return existing;
        synchronized (APIUtils.class) {
            if (cachedStatsKey != null) return cachedStatsKey;
            cachedStatsKey = loadOrCreateStatsKey();
            return cachedStatsKey;
        }
    }

    private static String loadOrCreateStatsKey() {
        try {
            Path file = MeteorClient.FOLDER.toPath().resolve("thm").resolve("stats-key");
            Files.createDirectories(file.getParent());
            if (Files.isRegularFile(file)) {
                String loaded = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!loaded.isEmpty()) return loaded;
            }
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            String generated = HexFormat.of().formatHex(raw);
            Path tmp = file.resolveSibling("stats-key.tmp");
            Files.writeString(tmp, generated, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
            return generated;
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to persist stats cache key; using a session-only key: {}", e.getMessage());
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            return HexFormat.of().formatHex(raw);
        }
    }

    public static void sendStatus(String message) {
        postContent(GeneratedApiEndpoints.statusUrl(), message, "status");
    }

    public static void sendStatistics(String message) {
        postContent(GeneratedApiEndpoints.highwayUrl(), message, "statistics");
    }

    public static void sendToWebhook(String url, String message) {
        if (TrustedHttp.parseAllowedUri(url, TrustedHttp.Kind.USER_WEBHOOK) == null) {
            THMAddon.LOG.warn("Rejected webhook URL");
            return;
        }
        new Thread(() -> {
            boolean ok = TrustedHttp.postJson(url, TrustedHttp.jsonContent(message), TrustedHttp.Kind.USER_WEBHOOK, null);
            if (!ok) THMAddon.LOG.warn("Failed to send to webhook");
        }, "thm-webhook").start();
    }

    public static List<ThmMembers.Member> fetchMembersFromApi() {
        try {
            String response = TrustedHttp.getString(
                GeneratedApiEndpoints.memberHudUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (response == null) return null;

            JsonArray jsonArray = GSON.fromJson(response, JsonArray.class);
            if (jsonArray == null) return null;
            if (jsonArray.size() > MAX_MEMBERS) {
                THMAddon.LOG.warn("Member list exceeded {} entries; ignoring remote payload", MAX_MEMBERS);
                return null;
            }

            List<ThmMembers.Member> members = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                JsonElement element = jsonArray.get(i);
                if (element == null || !element.isJsonObject()) continue;
                JsonObject jsonObject = element.getAsJsonObject();
                JsonArray usernamesArray = jsonObject.getAsJsonArray("usernames");
                if (usernamesArray == null) continue;
                int count = Math.min(usernamesArray.size(), MAX_USERNAMES_PER_MEMBER);
                List<String> valid = new ArrayList<>(count);
                for (int j = 0; j < count; j++) {
                    JsonElement nameEl = usernamesArray.get(j);
                    if (nameEl == null || !nameEl.isJsonPrimitive()) continue;
                    String name = TrustedHttp.sanitizeDisplay(nameEl.getAsString(), 16);
                    if (TrustedHttp.isMinecraftUsername(name)) valid.add(name);
                }
                if (valid.isEmpty()) continue;

                String rank = TrustedHttp.sanitizeDisplay(stringField(jsonObject, "rank"), 64);
                String rankId = TrustedHttp.sanitizeDisplay(stringField(jsonObject, "rankId"), 64);
                String branch = TrustedHttp.sanitizeDisplay(stringField(jsonObject, "branch"), 64);
                String discordName = TrustedHttp.sanitizeDisplay(stringField(jsonObject, "discordname"), 64);
                String displayName = valid.getFirst();
                if (discordName.isEmpty()) discordName = displayName;
                members.add(new ThmMembers.Member(displayName, valid.toArray(String[]::new), rank, rankId, branch, discordName));
            }
            THMAddon.LOG.info("Fetched Members");
            return members;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching members from API: {}", e.getMessage());
            return null;
        }
    }

    public static Map<String, String> fetchHighwayStatusFromApi() {
        try {
            String body = TrustedHttp.getString(
                GeneratedApiEndpoints.highwayStatusUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            Map<String, String> highwayByName = new HashMap<>();
            Map<String, Long> newestTimestampByName = new HashMap<>();
            if (root == null) return highwayByName;
            if (root.size() > MAX_HIGHWAY_ROWS) {
                THMAddon.LOG.warn("Highway status map exceeded {} entries; ignoring remote payload", MAX_HIGHWAY_ROWS);
                return null;
            }

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String highway = TrustedHttp.sanitizeDisplay(entry.getKey(), 64);
                if (highway.isEmpty() || !entry.getValue().isJsonObject()) continue;
                JsonObject value = entry.getValue().getAsJsonObject();
                if (!value.has("username")) continue;
                String raw = TrustedHttp.sanitizeDisplay(value.get("username").getAsString(), 16)
                    .toLowerCase(Locale.ROOT);
                if (raw.isEmpty() || "unknown".equals(raw) || !TrustedHttp.isMinecraftUsername(raw)) continue;
                long timestamp = value.has("timestamp") ? value.get("timestamp").getAsLong() : Long.MIN_VALUE;
                Long existingTimestamp = newestTimestampByName.get(raw);
                if (existingTimestamp == null || timestamp >= existingTimestamp) {
                    newestTimestampByName.put(raw, timestamp);
                    highwayByName.put(raw, highway);
                }
            }
            return highwayByName;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching highway status from API: {}", e.getMessage());
            return null;
        }
    }

    public static Map<String, String> fetchCapeListFromApi() {
        try {
            String body = TrustedHttp.getString(
                GeneratedApiEndpoints.capeListUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("players") || !root.get("players").isJsonObject()) return null;
            JsonObject players = root.getAsJsonObject("players");
            if (players.size() > MAX_HIGHWAY_ROWS) return null;

            Map<String, String> result = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : players.entrySet()) {
                String key = entry.getKey().toLowerCase(Locale.ROOT);
                if (!TrustedHttp.isMinecraftUsername(key) || !entry.getValue().isJsonObject()) continue;
                JsonObject p = entry.getValue().getAsJsonObject();
                if (p == null || !p.has("cape")) continue;
                String cape = TrustedHttp.sanitizeDisplay(p.get("cape").getAsString(), 64);
                if (!TrustedHttp.isSafeCapeId(cape)) continue;
                result.put(key, cape);
            }
            THMAddon.LOG.info("Fetched Cape List");
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape list: {}", e.getMessage());
            return null;
        }
    }

    public static void postCapeSelection(String username, String cape, String token) {
        if (!TrustedHttp.isMinecraftUsername(username) || !TrustedHttp.isSafeCapeId(cape)) return;
        new Thread(() -> {
            JsonObject json = new JsonObject();
            json.addProperty("username", username);
            json.addProperty("cape", cape);
            json.addProperty("timestamp", System.currentTimeMillis());
            json.addProperty("token", token == null ? "" : token);
            boolean ok = TrustedHttp.postJson(
                GeneratedApiEndpoints.capePostUrl(), json.toString(), TrustedHttp.Kind.API, token);
            if (!ok) THMAddon.LOG.warn("Failed to post cape selection");
        }, "thm-cape-post").start();
    }

    public static List<CapeManager.CapeEntry> fetchCapeIndexFromApi() {
        try {
            String body = TrustedHttp.getString(
                GeneratedApiEndpoints.capeIndexUrl(), TrustedHttp.Kind.API, TrustedHttp.MAX_JSON_BYTES);
            if (body == null) return null;

            JsonObject root = GSON.fromJson(body, JsonObject.class);
            if (root == null || !root.has("capes") || !root.get("capes").isJsonArray()) return null;
            JsonArray arr = root.getAsJsonArray("capes");
            if (arr.size() > MAX_CAPES) {
                THMAddon.LOG.warn("Cape index exceeded {} entries; ignoring remote payload", MAX_CAPES);
                return null;
            }

            List<CapeManager.CapeEntry> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) continue;
                JsonObject o = arr.get(i).getAsJsonObject();
                if (o == null || !o.has("id") || !o.has("url")) continue;
                String id = TrustedHttp.sanitizeDisplay(o.get("id").getAsString(), 64);
                String url = o.get("url").getAsString();
                if (!TrustedHttp.isSafeCapeId(id) || id.equalsIgnoreCase("None")) continue;
                if (TrustedHttp.parseAllowedUri(url, TrustedHttp.Kind.IMAGE) == null) continue;
                result.add(new CapeManager.CapeEntry(id, url));
            }
            return result;
        } catch (Exception e) {
            THMAddon.LOG.warn("Error fetching cape index from API: {}", e.getMessage());
            return null;
        }
    }

    private static void postContent(String url, String message, String label) {
        new Thread(() -> {
            boolean ok = TrustedHttp.postJson(url, TrustedHttp.jsonContent(message), TrustedHttp.Kind.API, null);
            if (!ok) THMAddon.LOG.warn("Failed to send {} to API", label);
        }, "thm-api-" + label).start();
    }

    private static String stringField(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return "";
        try {
            return object.getAsJsonPrimitive(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }
}
