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
 * Same fix as {@link XaeroMinimapEdgeMixin}, for the world map: {@code MapWriter#writeChunk} skips
 * the chunk outright when any of its 3x3 neighbors is null or an EmptyChunk.
 */
@Mixin(targets = "xaero.map.MapWriter", remap = false)
public abstract class XaeroWorldMapEdgeMixin {
    @Redirect(
        method = "writeChunk",
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
