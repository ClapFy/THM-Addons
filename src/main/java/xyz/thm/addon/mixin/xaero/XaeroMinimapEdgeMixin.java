/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.xaero;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Xaero-side equivalent of {@code EdgeRenderMixin} / {@code SodiumEdgeRenderMixin}: before writing a
 * chunk, {@code MinimapWriter#writeTile} walks the chunk's 3x3 neighborhood and treats the chunk as
 * unloaded if any neighbor is null or an EmptyChunk. At the outer ring of render distance the
 * one-out neighbor is never sent, so edge chunks render but never reach the minimap.
 *
 * <p>The neighbor lookup's result is only null/EmptyChunk-tested, so substituting a loaded chunk is
 * enough to pass the check. ponytail: pinned to Xaero's method name and to that being the only
 * World#getChunk(II) call in it — re-check both if Xaero changes writeTile.
 */
@Mixin(targets = "xaero.common.minimap.write.MinimapWriter", remap = false)
public abstract class XaeroMinimapEdgeMixin {
    @Redirect(
        method = "writeTile",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;getChunk(II)Lnet/minecraft/world/chunk/WorldChunk;", remap = true)
    )
    private WorldChunk thm$neighborAlwaysLoaded(World world, int x, int z) {
        WorldChunk chunk = world.getChunk(x, z);
        if (!(chunk instanceof EmptyChunk)) return chunk;

        PlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return chunk;
        return world.getChunk(player.getChunkPos().x, player.getChunkPos().z);
    }
}
