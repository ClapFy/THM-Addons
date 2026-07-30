/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.CapeManager;
import xyz.thm.addon.utils.ThmMembers;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class ThmCapeRenderMixin {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void thm$injectCape(CallbackInfoReturnable<SkinTextures> cir) {
        THMSystem system = THMSystem.get();
        if (system == null) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        MinecraftClient mc = MinecraftClient.getInstance();

        String capeId;
        if (mc.player == (Object) self) {
            capeId = system.cape.get();
            if (capeId == null || capeId.equals("None")) return;
        } else {
            capeId = ThmMembers.getCapeByMcName(self.getGameProfile().name());
            if (capeId == null || capeId.equalsIgnoreCase("None")) return;
            if (!FabricLoader.getInstance().isDevelopmentEnvironment() && !ThmMembers.isThmMember(self)) return;
        }

        SkinTextures original = cir.getReturnValue();
        if (original == null) return;

        Identifier capeTexture = CapeManager.getCapeTexture(capeId);
        if (capeTexture == null) return;

        AssetInfo.TextureAssetInfo capeAsset = new AssetInfo.TextureAssetInfo(capeTexture, capeTexture);
        cir.setReturnValue(new SkinTextures(
            original.body(),
            capeAsset,
            capeAsset,
            original.model(),
            original.secure()
        ));
    }
}
