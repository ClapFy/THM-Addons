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

// "Frosted glass" blur for just the window rectangle (not the whole shader background): renders
// the active shader into a small offscreen texture at the window's own resolution, runs a real
// two-pass (horizontal then vertical) box blur over it - the same technique vanilla's own menu
// blur (assets/minecraft/shaders/post/box_blur.fsh) uses, just simplified to a plain N-tap
// average instead of its bilinear-doubled sampling - then blits the result back onto the main
// framebuffer, scissored to the window rect so nothing outside it is touched. No changes needed
// to the 19 shader files, and all shader sources here are supplied inline (no .fsh resource
// files) via GpuDevice#precompilePipeline(pipeline, sourceGetter).
class BlurBackground {
    private static final Identifier VSH_ID = Identifier.of(THMAddon.MOD_ID, "blur_vsh");
    private static final Identifier BLUR_FSH_ID = Identifier.of(THMAddon.MOD_ID, "blur_pass_fsh");
    private static final Identifier BLIT_FSH_ID = Identifier.of(THMAddon.MOD_ID, "blur_blit_fsh");
    private static final float MAX_RADIUS = 20f; // texels, at strength = 100
    // The shader render + both blur passes work at 1/WORK_SCALE resolution - a blurred image
    // already destroys fine detail, so downsampling before blurring and upsampling on the final
    // blit (bilinear, see FilterMode.LINEAR below) is the standard cost/quality tradeoff every
    // engine's blur/bloom uses; cuts the two most expensive passes' pixel count by WORK_SCALE^2.
    private static final int WORK_SCALE = 2;
    private static final int MIN_TEXTURE_SIZE = 4;
    private static final long BLUR_PARAMS_SIZE = 16L; // std140: vec4(stepX, stepY, radius, unused)
    private static final long RECT_BUFFER_SIZE = 16L; // std140: vec4 rectPixels

    private static final String VSH_SRC = """
        #version 330 core
        out vec2 texCoord;
        void main() {
            vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
            gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
            texCoord = uv;
        }
        """;

    private static final String BLUR_FSH_SRC = """
        #version 330 core
        layout(std140) uniform BlurParams {
            vec4 params;
        };
        in vec2 texCoord;
        uniform sampler2D InSampler;
        out vec4 fragColor;
        void main() {
            vec4 sum = texture(InSampler, texCoord);
            float total = 1.0;
            float radius = params.z;
            for (float i = 1.0; i <= radius; i += 1.0) {
                sum += texture(InSampler, texCoord + params.xy * i);
                sum += texture(InSampler, texCoord - params.xy * i);
                total += 2.0;
            }
            fragColor = sum / total;
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

    private static RenderPipeline blurPipeline;
    private static RenderPipeline blitPipeline;
    private static Boolean pipelinesValid;
    private static GpuBuffer blurParamsBuffer;
    private static GpuBuffer rectBuffer;

    private static GpuTexture textureA, textureB;
    private static GpuTextureView viewA, viewB;
    private static int texWidth = -1, texHeight = -1;

    /**
     * @param x1 x2 y1 y2 window bounds in GUI-scaled screen coordinates (same space as
     *           MainMenuFx.windowBounds).
     * @return true if it drew.
     */
    static boolean renderRegion(RenderPipeline shaderPipeline, int strength, int x1, int y1, int x2, int y2) {
        GpuDevice device = RenderSystem.getDevice();
        if (!ensurePipelines(device)) return false;

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer framebuffer = mc.getFramebuffer();
        GpuTextureView mainView = framebuffer.getColorAttachmentView();
        if (mainView == null) return false;

        Window window = mc.getWindow();
        double scaleX = framebuffer.textureWidth / (double) window.getScaledWidth();
        double scaleY = framebuffer.textureHeight / (double) window.getScaledHeight();
        int px1 = (int) Math.round(x1 * scaleX);
        int pw = (int) Math.round((x2 - x1) * scaleX);
        int ph = (int) Math.round((y2 - y1) * scaleY);
        if (pw <= 0 || ph <= 0) return false;
        // gl_FragCoord/glScissor both use bottom-left origin (Y up) in raw GL, unlike every 2D
        // coordinate elsewhere in this codebase (Y down from the top) - flip once here so the
        // scissored region and the fragment shader's sampling agree with where the window
        // actually is, instead of a several-pixel seam at the top/bottom from the mismatch.
        int py1 = (int) Math.round(framebuffer.textureHeight - y2 * scaleY);

        int workW = Math.max(MIN_TEXTURE_SIZE, pw / WORK_SCALE);
        int workH = Math.max(MIN_TEXTURE_SIZE, ph / WORK_SCALE);
        ensureTextures(device, workW, workH);

        // Pass 1: render the active shader at (downscaled) working resolution into A.
        ShaderBackground.drawInto(shaderPipeline, viewA, workW, workH);

        float radius = Math.max(1f, strength / 100f * MAX_RADIUS);
        CommandEncoder encoder = device.createCommandEncoder();

        // Pass 2: horizontal blur, A -> B.
        writeBlurParams(encoder, 1f / workW, 0f, radius);
        drawFullscreen(encoder, blurPipeline, viewB, "BlurParams", blurParamsBuffer, viewA);

        // Pass 3: vertical blur, B -> A.
        writeBlurParams(encoder, 0f, 1f / workH, radius);
        drawFullscreen(encoder, blurPipeline, viewA, "BlurParams", blurParamsBuffer, viewB);

        // Pass 4: blit A back onto the main framebuffer at full size, scissored to the window
        // rect - the bilinear sampler does the upscale, which is fine since it's already blurred.
        writeRect(encoder, px1, py1, pw, ph);
        try (RenderPass pass = encoder.createRenderPass(() -> "THM blur blit", mainView, OptionalInt.empty())) {
            pass.enableScissor(px1, py1, pw, ph);
            pass.setPipeline(blitPipeline);
            pass.setUniform("BlitRect", rectBuffer);
            pass.bindTexture("InSampler", viewA, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.draw(0, 3);
        }

        return true;
    }

    private static void drawFullscreen(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView target,
                                        String uniformName, GpuBuffer uniformBuffer, GpuTextureView source) {
        try (RenderPass pass = encoder.createRenderPass(() -> "THM blur pass", target, OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.setUniform(uniformName, uniformBuffer);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    private static void writeBlurParams(CommandEncoder encoder, float stepX, float stepY, float radius) {
        if (blurParamsBuffer == null) {
            blurParamsBuffer = RenderSystem.getDevice().createBuffer(() -> "THM blur params", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, BLUR_PARAMS_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) BLUR_PARAMS_SIZE);
            data.putFloat(0, stepX);
            data.putFloat(4, stepY);
            data.putFloat(8, radius);
            encoder.writeToBuffer(blurParamsBuffer.slice(), data);
        }
    }

    private static void writeRect(CommandEncoder encoder, int px1, int py1, int pw, int ph) {
        if (rectBuffer == null) {
            rectBuffer = RenderSystem.getDevice().createBuffer(() -> "THM blur rect", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, RECT_BUFFER_SIZE);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.calloc((int) RECT_BUFFER_SIZE);
            data.putFloat(0, px1);
            data.putFloat(4, py1);
            data.putFloat(8, pw);
            data.putFloat(12, ph);
            encoder.writeToBuffer(rectBuffer.slice(), data);
        }
    }

    private static boolean ensurePipelines(GpuDevice device) {
        if (pipelinesValid != null) return pipelinesValid;

        blurPipeline = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
            .withLocation(Identifier.of(THMAddon.MOD_ID, "blur_pass"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(BLUR_FSH_ID)
            .withUniform("BlurParams", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .build();

        blitPipeline = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
            .withLocation(Identifier.of(THMAddon.MOD_ID, "blur_blit"))
            .withVertexShader(VSH_ID)
            .withFragmentShader(BLIT_FSH_ID)
            .withUniform("BlitRect", UniformType.UNIFORM_BUFFER)
            .withSampler("InSampler")
            .build();

        boolean blurOk = device.precompilePipeline(blurPipeline, BlurBackground::shaderSource).isValid();
        boolean blitOk = device.precompilePipeline(blitPipeline, BlurBackground::shaderSource).isValid();
        pipelinesValid = blurOk && blitOk;
        if (!pipelinesValid) THMAddon.LOG.warn("[THM] Main-menu window blur shaders failed to compile, disabling blur");
        return pipelinesValid;
    }

    private static String shaderSource(Identifier id, ShaderType type) {
        if (id.equals(VSH_ID)) return VSH_SRC;
        if (id.equals(BLUR_FSH_ID)) return BLUR_FSH_SRC;
        if (id.equals(BLIT_FSH_ID)) return BLIT_FSH_SRC;
        return null;
    }

    private static void ensureTextures(GpuDevice device, int w, int h) {
        if (textureA != null && texWidth == w && texHeight == h) return;

        if (viewA != null) viewA.close();
        if (viewB != null) viewB.close();
        if (textureA != null) textureA.close();
        if (textureB != null) textureB.close();

        int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
        textureA = device.createTexture(() -> "THM blur A", usage, TextureFormat.RGBA8, w, h, 1, 1);
        textureB = device.createTexture(() -> "THM blur B", usage, TextureFormat.RGBA8, w, h, 1, 1);
        viewA = device.createTextureView(textureA);
        viewB = device.createTextureView(textureB);
        texWidth = w;
        texHeight = h;
    }
}
