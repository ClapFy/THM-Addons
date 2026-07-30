/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.client.texture.NativeImage;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.webp.Vp8LDecoder;

import java.nio.ByteBuffer;

/** Decodes WebP-encoded texture bytes into a {@link NativeImage}, since Minecraft's native STB loader can't read WebP. */
public final class WebpUtil {
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

    private WebpUtil() {
    }

    /** Returns the decoded image if {@code buffer} (from its current position) holds WebP data, else null. Leaves buffer position untouched. */
    public static NativeImage tryDecode(NativeImage.Format format, ByteBuffer buffer) {
        if (!isWebp(buffer)) return null;

        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.duplicate().get(bytes);

            Vp8LDecoder.DecodedImage image = Vp8LDecoder.decode(bytes);
            if (image == null) return null;
            return toNativeImage(format, image);
        } catch (Exception e) {
            THMAddon.LOG.warn("Failed to decode WebP image: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isWebp(ByteBuffer buffer) {
        if (buffer.remaining() < 12) return false;
        int base = buffer.position();
        for (int i = 0; i < 4; i++) if (buffer.get(base + i) != RIFF[i]) return false;
        for (int i = 0; i < 4; i++) if (buffer.get(base + 8 + i) != WEBP[i]) return false;
        return true;
    }

    private static NativeImage toNativeImage(NativeImage.Format format, Vp8LDecoder.DecodedImage image) {
        NativeImage.Format target = format != null ? format : NativeImage.Format.RGBA;
        NativeImage nativeImage = new NativeImage(target, image.width, image.height, false);
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                nativeImage.setColorArgb(x, y, image.argb[y * image.width + x]);
            }
        }
        return nativeImage;
    }
}
