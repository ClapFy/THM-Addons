/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Hostname / address checks for {@link TrustedHttp} with no Minecraft types so
 * unit tests can cover SSRF and placeholder-endpoint rejection.
 */
public final class PublicHosts {
    public enum Status {
        PUBLIC,
        PRIVATE,
        UNRESOLVED,
        PLACEHOLDER,
        INVALID
    }

    private PublicHosts() {}

    /**
     * True when {@code url} is HTTPS and not an RFC 2606 example host. Does not
     * perform DNS — {@link TrustedHttp} still refuses private/unresolved hosts
     * at request time.
     */
    public static boolean isConfiguredApiUrl(String url) {
        if (url == null || url.isBlank()) return false;
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        return host != null && !host.isBlank() && !isPlaceholderHost(host);
    }

    public static boolean isPlaceholderUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            return isPlaceholderHost(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isPlaceholderHost(String host) {
        String h = normalizeHost(host);
        if (h == null) return false;
        return isExampleFamily(h);
    }

    public static Status classify(String host) {
        String h = normalizeHost(host);
        if (h == null) return Status.INVALID;
        if (isExampleFamily(h)) return Status.PLACEHOLDER;
        if (h.equals("localhost") || h.endsWith(".localhost")) return Status.PRIVATE;
        if (h.equals("metadata.google.internal") || h.endsWith(".internal")) return Status.PRIVATE;

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(h);
        } catch (UnknownHostException e) {
            return Status.UNRESOLVED;
        }
        if (addresses.length == 0) return Status.UNRESOLVED;
        for (InetAddress addr : addresses) {
            if (!isPublicAddress(addr)) return Status.PRIVATE;
        }
        return Status.PUBLIC;
    }

    static boolean isPublicAddress(InetAddress addr) {
        if (addr == null) return false;
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
            return false;
        }
        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            int a = raw[0] & 0xFF;
            int b = raw[1] & 0xFF;
            int c = raw[2] & 0xFF;
            if (a == 0) return false;
            if (a == 100 && b >= 64 && b <= 127) return false; // 100.64/10 CGNAT
            if (a == 169 && b == 254) return false;
            // 192.0.0.0/24 (IANA) and 192.0.2.0/24 (TEST-NET-1), not the whole /16
            if (a == 192 && b == 0 && (c == 0 || c == 2)) return false;
            if (a == 198 && (b == 18 || b == 19)) return false;
        }
        if (raw.length == 16) {
            if ((raw[0] & 0xFE) == 0xFC) return false; // unique local fc00::/7
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

    private static String normalizeHost(String host) {
        if (host == null) return null;
        String h = host.toLowerCase(Locale.ROOT).trim();
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h.isEmpty() ? null : h;
    }

    private static boolean isExampleFamily(String h) {
        return h.equals("example.com") || h.endsWith(".example.com")
            || h.equals("example.org") || h.endsWith(".example.org")
            || h.equals("example.net") || h.endsWith(".example.net");
    }
}
