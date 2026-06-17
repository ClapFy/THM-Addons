package xyz.thm.addon.mixin;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

@Mixin(ScreenshotRecorder.class)
public class ScreenshotClipboardMixin {

    // Minecraft launches with -Djava.awt.headless=true; override before AWT is first touched.
    // Safe because Minecraft uses LWJGL for rendering and never initializes AWT itself.
    static {
        System.setProperty("java.awt.headless", "false");
    }

    // InputStream representation so native apps (Discord, browsers) receive raw PNG bytes.
    private static final DataFlavor PNG_FLAVOR = new DataFlavor("image/png", "PNG Image");

    // Inject at TAIL so: (1) the file is fully written, (2) the "Saved screenshot" message
    // has already been sent — our clipboard message always appears after it.
    @Inject(method = "method_22691", at = @At("TAIL"))
    private static void onSaveScreenshotFile(NativeImage image, File file, Consumer<Text> messageReceiver, CallbackInfo ci) {
        if (!THMSystem.get().screenshotToClipboard.get()) return;

        new Thread(() -> {
            try {
                BufferedImage buffered = ImageIO.read(file);
                if (buffered == null) {
                    THMAddon.LOG.warn("[THM] Could not read screenshot file for clipboard");
                    return;
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new TransferableImage(buffered), null);
                ChatUtils.info("Successfully copied screenshot to clipboard");
            } catch (Exception e) {
                THMAddon.LOG.warn("[THM] Failed to copy screenshot to clipboard", e);
            }
        }).start();
    }

    private record TransferableImage(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{PNG_FLAVOR, DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PNG_FLAVOR.equals(flavor) || DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (DataFlavor.imageFlavor.equals(flavor)) return image;
            if (PNG_FLAVOR.equals(flavor)) {
                // Encode to raw PNG bytes; PNG_FLAVOR expects an InputStream
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                return new ByteArrayInputStream(out.toByteArray());
            }
            throw new UnsupportedFlavorException(flavor);
        }
    }
}
