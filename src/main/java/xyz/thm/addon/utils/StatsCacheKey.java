/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import xyz.thm.addon.THMAddon;

import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Local HighwayBuilder stats-cache encryption key. Not sent to the API.
 * Generated APIUtils still uses a per-build password to decrypt endpoint URLs;
 * this key is separate so stats survive rebuilds.
 */
public final class StatsCacheKey {
    private static volatile String cached;

    private StatsCacheKey() {}

    public static String getPassword() {
        String existing = cached;
        if (existing != null) return existing;
        synchronized (StatsCacheKey.class) {
            if (cached != null) return cached;
            cached = loadOrCreate();
            return cached;
        }
    }

    private static String loadOrCreate() {
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
}
