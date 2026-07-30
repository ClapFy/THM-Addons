/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkBuilder.BuiltChunk.class)
public abstract class EdgeRenderMixin {
    @Inject(method = "isChunkNonEmpty", at = @At("HEAD"), cancellable = true)
    private void allowEdgeChunksToRender(long l, CallbackInfoReturnable<Boolean> cir) {
        if (!FabricLoader.getInstance().isModLoaded("tweakeroo")) {
            cir.setReturnValue(true);
        }
    }
}
