/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import com.google.gson.JsonObject;
import xyz.thm.addon.THMAddon;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Outbound HTTP that cannot be turned into a backdoor by a compromised API host.
 *
 * <p>Remote JSON/cape endpoints stay functional, but responses cannot:
 * <ul>
 *   <li>fetch {@code file://}, {@code jar:}, or other non-http(s) URLs</li>
 *   <li>hit loopback / RFC1918 / link-local / metadata addresses (SSRF)</li>
 *   <li>follow redirects off to a different host</li>
 *   <li>write unbounded payloads to memory or disk</li>
 * </ul>
 */
public final class TrustedHttp {
    public static final int MAX_JSON_BYTES = 1_048_576;
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_WEBHOOK_BYTES = 8 * 1024 * 1024;
    public static final int CONNECT_TIMEOUT_MS = 8_000;
    public static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_REDIRECTS = 3;

    public enum Kind {
        API,           // THM API: HTTPS; Bearer token only on POST
        PUBLIC_HTTPS,  // third-party HTTPS, never sends the API token
        USER_WEBHOOK,  // user-configured Discord webhooks
        IMAGE          // cape / texture download
    }

    private TrustedHttp() {}

    public static String getString(String url, Kind kind, int maxBytes) {
        byte[] body = getBytes(url, kind, maxBytes);
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    public static byte[] getBytes(String url, Kind kind, int maxBytes) {
        try {
            URI uri = parseAllowedUri(url, kind);
            if (uri == null) return null;
            return exchange("GET", uri, kind, null, null, maxBytes, false, null);
        } catch (Exception e) {
            THMAddon.LOG.warn("Trusted HTTP GET failed: {}", e.getMessage());
            return null;
        }
    }

    public static boolean postJson(String url, String json, Kind kind, String bearerToken) {
        try {
            if (!allowOutboundPost(kind, json == null ? null : json.getBytes(StandardCharsets.UTF_8))) return false;
            URI uri = parseAllowedUri(url, kind);
            if (uri == null) return false;
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            if (body.length > MAX_JSON_BYTES) {
                THMAddon.LOG.warn("Refusing oversized JSON POST ({} bytes)", body.length);
                return false;
            }
            exchange("POST", uri, kind, "application/json", body, MAX_JSON_BYTES, true, bearerToken);
            return true;
        } catch (Exception e) {
            THMAddon.LOG.warn("Trusted HTTP POST failed: {}", e.getMessage());
            return false;
        }
    }

    public static boolean postMultipart(String url, byte[] body, String contentType, Kind kind) {
        try {
            if (!allowOutboundPost(kind, body)) return false;
            URI uri = parseAllowedUri(url, kind);
            if (uri == null) return false;
            if (body.length > MAX_WEBHOOK_BYTES) {
                THMAddon.LOG.warn("Refusing oversized multipart POST ({} bytes)", body.length);
                return false;
            }
            exchange("POST", uri, kind, contentType, body, 4096, true, null);
            return true;
        } catch (Exception e) {
            THMAddon.LOG.warn("Trusted HTTP multipart POST failed: {}", e.getMessage());
            return false;
        }
    }

    private static boolean allowOutboundPost(Kind kind, byte[] body) {
        if (kind == Kind.USER_WEBHOOK && !PrivacyGuard.allowsRemoteExport()) {
            THMAddon.LOG.warn("Blocked webhook: chat and coordinates only leave this client while Highway Builder is paving a main highway, plus 5s after it turns off");
            return false;
        }
        if (body == null || body.length == 0) return true;
        String text = new String(body, StandardCharsets.UTF_8);
        try {
            xyz.thm.addon.system.THMSystem system = xyz.thm.addon.system.THMSystem.get();
            if (system != null) {
                String password = system.getCrackedPassword();
                if (password != null && password.length() >= 3 && text.contains(password)) {
                    THMAddon.LOG.warn("Refusing HTTP body that contains the cracked login password");
                    return false;
                }
                if (kind == Kind.USER_WEBHOOK) {
                    String token = system.getApiToken();
                    if (token != null && token.length() >= 8 && text.contains(token)) {
                        THMAddon.LOG.warn("Refusing webhook body that contains the API token");
                        return false;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Settings may not be loaded yet.
        }
        return true;
    }

    public static String jsonContent(String message) {
        JsonObject root = new JsonObject();
        root.addProperty("content", message == null ? "" : message);
        return root.toString();
    }

    public static String jsonEmbed(String description) {
        JsonObject embed = new JsonObject();
        embed.addProperty("description", description == null ? "" : description);
        JsonObject root = new JsonObject();
        com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
        embeds.add(embed);
        root.add("embeds", embeds);
        return root.toString();
    }

    public static URI parseAllowedUri(String raw, Kind kind) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048) return null;

        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            return null;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (kind == Kind.API || kind == Kind.IMAGE || kind == Kind.PUBLIC_HTTPS) {
            if (!"https".equals(scheme)) {
                THMAddon.LOG.warn("Rejected non-HTTPS {} URL", kind);
                return null;
            }
        } else if (!"https".equals(scheme) && !"http".equals(scheme)) {
            THMAddon.LOG.warn("Rejected non-HTTP(S) webhook URL");
            return null;
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) return null;
        if (uri.getUserInfo() != null) return null;
        if (!isPublicHostname(uri.getHost())) {
            THMAddon.LOG.warn("Rejected URL host that resolves to a private or local address");
            return null;
        }
        return uri.normalize();
    }

    public static boolean isSafeCapeId(String id) {
        if (id == null || id.isBlank() || id.length() > 64) return false;
        if (id.equalsIgnoreCase("None")) return true;
        if (id.contains("..") || id.indexOf('/') >= 0 || id.indexOf('\\') >= 0) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.';
            if (!ok) return false;
        }
        return true;
    }

    public static boolean isMinecraftUsername(String name) {
        if (name == null) return false;
        int len = name.length();
        if (len < 1 || len > 16) return false;
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    public static String sanitizeDisplay(String value, int maxLen) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxLen));
        for (int i = 0; i < value.length() && out.length() < maxLen; i++) {
            char c = value.charAt(i);
            if (c < 32 || c == 127) continue;
            out.append(c);
        }
        return out.toString().trim();
    }

    static boolean isPublicHostname(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.isEmpty() || h.equals("localhost") || h.endsWith(".localhost")) return false;
        if (h.equals("metadata.google.internal") || h.endsWith(".internal")) return false;

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(h);
        } catch (UnknownHostException e) {
            return false;
        }
        if (addresses.length == 0) return false;
        for (InetAddress addr : addresses) {
            if (!isPublicAddress(addr)) return false;
        }
        return true;
    }

    static boolean isPublicAddress(InetAddress addr) {
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return false;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int a = raw[0] & 0xFF;
            int b = raw[1] & 0xFF;
            if (a == 0) return false;
            if (a == 100 && b >= 64 && b <= 127) return false; // 100.64/10 CGNAT
            if (a == 169 && b == 254) return false;
            if (a == 192 && b == 0) return false;
            if (a == 198 && (b == 18 || b == 19)) return false;
        }
        if (raw.length == 16) {
            // Unique local fc00::/7
            if ((raw[0] & 0xFE) == 0xFC) return false;
            // IPv4-mapped
            boolean v4mapped = true;
            for (int i = 0; i < 10; i++) if (raw[i] != 0) { v4mapped = false; break; }
            if (v4mapped && raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF) {
                try {
                    return isPublicAddress(InetAddress.getByAddress(new byte[]{raw[12], raw[13], raw[14], raw[15]}));
                } catch (UnknownHostException e) {
                    return false;
                }
            }
        }
        return true;
    }

    private static byte[] exchange(
        String method,
        URI start,
        Kind kind,
        String contentType,
        byte[] requestBody,
        int maxResponseBytes,
        boolean discardBody,
        String bearerToken
    ) throws Exception {
        URI current = start;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            if (current.getHost() == null || !isPublicHostname(current.getHost())) {
                THMAddon.LOG.warn("Rejected URL host that resolves to a private or local address");
                return null;
            }

            HttpURLConnection cn = (HttpURLConnection) current.toURL().openConnection();
            try {
                cn.setInstanceFollowRedirects(false);
                cn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                cn.setReadTimeout(READ_TIMEOUT_MS);
                cn.setRequestMethod(method);
                cn.setUseCaches(false);
                if (contentType != null) cn.setRequestProperty("Content-Type", contentType);
                if (kind == Kind.API && "POST".equals(method)) {
                    String token = bearerToken != null && !bearerToken.isBlank() ? bearerToken : apiToken();
                    if (!token.isEmpty()) cn.setRequestProperty("Authorization", "Bearer " + token);
                }
                if (requestBody != null) {
                    cn.setDoOutput(true);
                    cn.setFixedLengthStreamingMode(requestBody.length);
                    try (var os = cn.getOutputStream()) {
                        os.write(requestBody);
                    }
                }

                int code = cn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == HttpURLConnection.HTTP_SEE_OTHER || code == 307 || code == 308) {
                    String location = cn.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        THMAddon.LOG.warn("HTTP redirect without Location from {}", current.getHost());
                        return null;
                    }
                    URI allowed = parseAllowedUri(current.resolve(location).toString(), kind);
                    if (allowed == null) return null;
                    if (!current.getHost().equalsIgnoreCase(allowed.getHost())) {
                        THMAddon.LOG.warn("Rejected cross-host HTTP redirect from {} to {}", current.getHost(), allowed.getHost());
                        return null;
                    }
                    current = allowed;
                    continue;
                }

                InputStream raw = code >= 400 ? cn.getErrorStream() : cn.getInputStream();
                byte[] body = new byte[0];
                if (raw != null) {
                    try (InputStream stream = raw) {
                        body = readLimited(stream, maxResponseBytes);
                    }
                }
                if (discardBody) {
                    if (code != 200 && code != 204) {
                        throw new java.io.IOException("HTTP " + method + " " + current.getHost() + " returned " + code);
                    }
                    return body;
                }
                if (code != 200) {
                    THMAddon.LOG.warn("HTTP GET {} returned {}", current.getHost(), code);
                    return null;
                }
                return body;
            } finally {
                cn.disconnect();
            }
        }
        THMAddon.LOG.warn("Too many HTTP redirects");
        return null;
    }

    private static byte[] readLimited(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                throw new IllegalStateException("response exceeded " + maxBytes + " bytes");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String apiToken() {
        try {
            var system = xyz.thm.addon.system.THMSystem.get();
            return system == null ? "" : system.getApiToken();
        } catch (Throwable t) {
            return "";
        }
    }
}
