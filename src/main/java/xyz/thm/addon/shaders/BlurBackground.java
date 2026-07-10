package xyz.thm.addon.shaders;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryStack;
import xyz.thm.addon.THMAddon;

import java.nio.ByteBuffer;
import java.util.OptionalInt;

// "Frosted glass" blur for just the window rectangle (not the whole shader background):
// renders the active shader into a small offscreen texture (cheap softening via downscale),
// then blits *only that rectangle* back onto the main framebuffer, scissored to it, sampling
// via gl_FragCoord so it lines up with the window regardless of GUI scale. No changes needed
// to the 19 shader files, and the blit shader source is supplied inline (no .fsh resource
// files) via GpuDevice#precompilePipeline(pipeline, sourceGetter).
class BlurBackground {
    private static final Identifier BLIT_VSH_ID = Identifier.of(THMAddon.MOD_ID, "blur_blit_vsh");
    private static final Identifier BLIT_FSH_ID = Identifier.of(THMAddon.MOD_ID, "blur_blit_fsh");
    private static final int MAX_DOWNSCALE = 24;
    private static final int MIN_TEXTURE_SIZE = 4;
    private static final long RECT_BUFFER_SIZE = 16L; // std140: vec4 rectPixels

    private static final String BLIT_VSH_SRC = """
        #version 330 core
        void main() {
            vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
        }
        """;

    private static final String BLIT_FSH_SRC = """
        #version 330 core
        layout(std140) uniform BlitRect {
            vec4 rectPixels;
        };
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            vec2 localUV = (gl_FragCoord.xy - rectPixels.xy) / rectPixels.zw;
            fragColor = texture(InSampler, localUV);
        }
        """;

    private static RenderPipeline blitPipeline;
    private static Boolean blitPipelineValid;
    private static GpuBuffer rectBuffer;

    private static GpuTexture smallTexture;
    private static GpuTextureView smallTextureView;
    private static int smallWidth = -1, smallHeight = -1;

    /**
     * @param x1 x2 y1 y2 window bounds in GUI-scaled screen coordinates (same space as
     *           MainMenuFx.windowBounds).
     * @return true if it drew.
     */
    static boolean renderRegion(RenderPipeline shaderPipeline, int strength, int x1, int y1, int x2, int y2) {
        GpuDevice device = RenderSystem.getDevice();
        if (!ensureBlitPipeline(device)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer framebuffer = mc.getFramebuffer();
        GpuTextureView mainView = framebuffer.getColorAttachmentView();
        if (mainView == null) return false;

        Window window = mc.getWindow();
        double scaleX = framebuffer.textureWidth / (double) window.getScaledWidth();
        double scaleY = framebuffer.textureHeight / (double) window.getScaledHeight();
        int px1 = (int) Math.round(x1 * scaleX);
        int py1 = (int) Math.round(y1 * scaleY);
        int pw = (int) Math.round((x2 - x1) * scaleX);
        int ph = (int) Math.round((y2 - y1) * scaleY);
        if (pw <= 0 || ph <= 0) return false;

        int divisor = 1 + Math.round(strength / 100f * (MAX_DOWNSCALE - 1));
        int w = Math.max(MIN_TEXTURE_SIZE, pw / divisor);
        int h = Math.max(MIN_TEXTURE_SIZE, ph / divisor);
        ensureTexture(device, w, h);

        // Render the active shader into the small texture - this is what gets sampled back,
        // softened by the downscale + the blit's bilinear upscale.
        ShaderBackground.drawInto(shaderPipeline, smallTextureView, w, h);

        if (rectBuffer == null) {
            rectBuffer = device.createBuffer(() -> "THM blur rect", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, RECT_BUFFER_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) RECT_BUFFER_SIZE);
            data.putFloat(0, px1);
            data.putFloat(4, py1);
            data.putFloat(8, pw);
            data.putFloat(12, ph);
            device.createCommandEncoder().writeToBuffer(rectBuffer.slice(), data);
        }

        CommandEncoder encoder = device.createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(() -> "THM blur blit", mainView, OptionalInt.empty())) {
            pass.enableScissor(px1, py1, pw, ph);
            pass.setPipeline(blitPipeline);
            pass.setUniform("BlitRect", rectBuffer);
            pass.bindTexture("InSampler", smallTextureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.draw(0, 3);
        }

        return true;
    }

    private static boolean ensureBlitPipeline(GpuDevice device) {
        if (blitPipelineValid != null) return blitPipelineValid;

        blitPipeline = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
            .withLocation(Identifier.of(THMAddon.MOD_ID, "blur_blit"))
            .withVertexShader(BLIT_VSH_ID)
            .withFragmentShader(BLIT_FSH_ID)
            .withUniform("BlitRect", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .build();

        CompiledRenderPipeline compiled = device.precompilePipeline(blitPipeline, BlurBackground::shaderSource);
        blitPipelineValid = compiled.isValid();
        if (!blitPipelineValid) THMAddon.LOG.warn("[THM] Main-menu window blur shader failed to compile, disabling blur");
        return blitPipelineValid;
    }

    private static String shaderSource(Identifier id, ShaderType type) {
        if (id.equals(BLIT_VSH_ID)) return BLIT_VSH_SRC;
        if (id.equals(BLIT_FSH_ID)) return BLIT_FSH_SRC;
        return null;
    }

    private static void ensureTexture(GpuDevice device, int w, int h) {
        if (smallTexture != null && smallWidth == w && smallHeight == h) return;

        if (smallTextureView != null) smallTextureView.close();
        if (smallTexture != null) smallTexture.close();

        smallTexture = device.createTexture(() -> "THM blur target",
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, w, h, 1, 1);
        smallTextureView = device.createTextureView(smallTexture);
        smallWidth = w;
        smallHeight = h;
    }
}
