/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;

import java.util.Optional;

/** Blaze3D helpers for Minecraft 26.2 ({@code GpuFormat}, bind-group layouts, instanced draw). */
public final class GpuCompat {
    private GpuCompat() {}

    public static GpuTexture createColorTexture(GpuDevice device, String name, int width, int height) {
        return device.createTexture(
            () -> name,
            GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1
        );
    }

    public static RenderPipeline.Builder withUniformBuffer(RenderPipeline.Builder builder, String name) {
        return builder.withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(name, UniformType.UNIFORM_BUFFER)
            .build());
    }

    public static RenderPipeline.Builder withSampler(RenderPipeline.Builder builder, String name) {
        return builder.withBindGroupLayout(BindGroupLayout.builder().withSampler(name).build());
    }

    public static RenderPipeline.Builder withUniformAndSampler(RenderPipeline.Builder builder, String uniform, String sampler) {
        return builder.withBindGroupLayout(BindGroupLayout.builder()
            .withUniform(uniform, UniformType.UNIFORM_BUFFER)
            .withSampler(sampler)
            .build());
    }

    public static RenderPass createPass(CommandEncoder encoder, String name, GpuTextureView view) {
        return encoder.createRenderPass(() -> name, view, Optional.empty());
    }

    public static void drawFullscreen(RenderPass pass) {
        pass.draw(0, 3, 1, 0);
    }
}
