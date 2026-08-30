/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import xyz.thm.addon.THMAddon;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Downloads THM capes listed in the API's cape index to disk and registers them as textures on demand. */
public final class CapeManager {
    public record CapeEntry(String id, String url) {}

    private static volatile String[] availableIds = {"None"};
    private static final Map<String, Identifier> textureCache = new HashMap<>();
    private static final Identifier MISSING = Identifier.fromNamespaceAndPath("thm-addon", "cape/missing");
    private static final int MAX_CAPE_WIDTH = 512;
    private static final int MAX_CAPE_HEIGHT = 512;

    private CapeManager() {
    }

    public static void initialize() {
        Thread t = new Thread(CapeManager::refresh, "THM-CapeDownload");
        t.setDaemon(true);
        t.start();
    }

    private static void refresh() {
        List<CapeEntry> entries = APIUtils.fetchCapeIndexFromApi();
        if (entries == null) return;

        List<String> ids = new ArrayList<>();
        ids.add("None");
        for (CapeEntry entry : entries) {
            if (!TrustedHttp.isSafeCapeId(entry.id()) || entry.id().equalsIgnoreCase("None")) continue;
            ids.add(entry.id());
            downloadIfMissing(entry);
        }
        availableIds = ids.toArray(new String[0]);
    }

    private static void downloadIfMissing(CapeEntry entry) {
        File file = capeFile(entry.id());
        if (file == null || file.exists()) return;

        try {
            if (TrustedHttp.parseAllowedUri(entry.url(), TrustedHttp.Kind.IMAGE) == null) return;
            byte[] bytes = TrustedHttp.getBytes(entry.url(), TrustedHttp.Kind.IMAGE, TrustedHttp.MAX_IMAGE_BYTES);
            if (bytes == null || bytes.length == 0) return;
            if (!looksLikeImage(bytes)) {
                THMAddon.LOG.warn("Rejected cape '{}': not a PNG or WebP payload", entry.id());
                return;
            }

            File parent = file.getParentFile();
            if (parent == null) return;
            parent.mkdirs();
            Path target = file.toPath();
            Path tmp = target.resolveSibling(entry.id() + ".webp.tmp");
            try {
                Files.write(tmp, bytes);
                try {
                    Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tmp);
            }
            THMAddon.LOG.info("Downloaded cape '{}'", entry.id());
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to download cape '{}': {}", entry.id(), e.getMessage());
        }
    }

    /** Options for the self-cape picker: "None" plus every id known from the cape index. */
    public static String[] availableCapeIds() {
        return availableIds;
    }

    /** Resolves (loading + registering the texture on first use) the render Identifier for a cape id, or null if unavailable. */
    public static synchronized Identifier getCapeTexture(String id) {
        if (id == null || id.isBlank() || id.equalsIgnoreCase("None") || !TrustedHttp.isSafeCapeId(id)) return null;

        Identifier cached = textureCache.get(id);
        if (cached != null) return cached == MISSING ? null : cached;

        Identifier resolved = loadTexture(id);
        textureCache.put(id, resolved == null ? MISSING : resolved);
        return resolved;
    }

    private static Identifier loadTexture(String id) {
        File file = capeFile(id);
        if (file == null || !file.isFile()) return null;

        NativeImage image = null;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length > TrustedHttp.MAX_IMAGE_BYTES || !looksLikeImage(bytes)) {
                Files.deleteIfExists(file.toPath());
                return null;
            }
            image = NativeImage.read(bytes);
            if (image.getWidth() > MAX_CAPE_WIDTH || image.getHeight() > MAX_CAPE_HEIGHT) {
                THMAddon.LOG.warn("Rejected cape texture '{}': dimensions too large", id);
                Files.deleteIfExists(file.toPath());
                return null;
            }
            Identifier textureId = Identifier.fromNamespaceAndPath("thm-addon", "cape/" + id.toLowerCase(Locale.ROOT));
            Minecraft.getInstance().getTextureManager().register(
                textureId, new DynamicTexture(() -> "thm-cape/" + id, image)
            );
            image = null;
            return textureId;
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to load cape texture '{}': {}", id, e.getMessage());
            try {
                Files.deleteIfExists(file.toPath());
            } catch (Exception ignored) {
            }
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    private static File capeFile(String id) {
        if (!TrustedHttp.isSafeCapeId(id) || id.equalsIgnoreCase("None")) return null;
        File dir = new File(MeteorClient.FOLDER, "thm/capes");
        File file = new File(dir, id + ".webp");
        try {
            if (!file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile())) return null;
        } catch (Exception e) {
            return null;
        }
        return file;
    }

    private static boolean looksLikeImage(byte[] bytes) {
        if (bytes.length >= 8
            && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return true;
        }
        if (bytes.length >= 12
            && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return true;
        }
        return false;
    }
}
