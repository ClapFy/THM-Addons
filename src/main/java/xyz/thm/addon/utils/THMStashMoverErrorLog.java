/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

public final class THMStashMoverErrorLog {
    private static final long MAX_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_LINE_CHARS = 4096;

    private static boolean sessionDisabled;

    private THMStashMoverErrorLog() {
    }

    public static synchronized void resetSessionDisable() {
        sessionDisabled = false;
    }

    public static synchronized void log(boolean enabled, String line) {
        if (!enabled || sessionDisabled || line == null || line.isBlank()) return;

        try {
            Path logPath = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("thm-stash-mover")
                .resolve("thm-stash-mover.log");

            Files.createDirectories(logPath.getParent());

            String cleanLine = PrivacyText.scrubCoordinates(line.replace('\r', ' ').replace('\n', ' '));
            if (cleanLine.length() > MAX_LINE_CHARS) cleanLine = cleanLine.substring(0, MAX_LINE_CHARS) + "...";

            String entry = Instant.now() + " ERROR " + cleanLine + System.lineSeparator();
            byte[] entryBytes = entry.getBytes(StandardCharsets.UTF_8);

            trimForAppend(logPath, entryBytes.length);
            Files.write(logPath, entryBytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            sessionDisabled = true;
        }
    }

    private static void trimForAppend(Path logPath, int appendBytes) throws IOException {
        if (!Files.exists(logPath)) return;

        long currentSize = Files.size(logPath);
        if (currentSize + appendBytes <= MAX_BYTES) return;

        if (appendBytes >= MAX_BYTES) {
            Files.write(logPath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
            return;
        }

        List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
        List<String> kept = new ArrayList<>(lines);

        while (!kept.isEmpty() && byteSize(kept) + appendBytes > MAX_BYTES) {
            kept.remove(0);
        }

        if (kept.isEmpty()) {
            Files.write(logPath, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        } else {
            Files.write(logPath, serializeLines(kept).getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    private static int byteSize(List<String> lines) {
        return serializeLines(lines).getBytes(StandardCharsets.UTF_8).length;
    }

    private static String serializeLines(List<String> lines) {
        return String.join(System.lineSeparator(), lines) + System.lineSeparator();
    }
}
