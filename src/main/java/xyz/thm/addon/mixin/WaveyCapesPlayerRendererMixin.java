package xyz.thm.addon.mixin;

import net.minecraft.client.network.ClientPlayerLikeEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.waveycapes.WaveyCapeRenderLayer;

@Mixin(PlayerEntityRenderer.class)
public abstract class WaveyCapesPlayerRendererMixin<T extends PlayerLikeEntity & ClientPlayerLikeEntity>
        extends LivingEntityRenderer<T, PlayerEntityRenderState, PlayerEntityModel> {

    public WaveyCapesPlayerRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void thm$addWaveyCapeLayer(CallbackInfo ci) {
        addFeature(new WaveyCapeRenderLayer(this));
    }
}
