package xyz.thm.addon.mixin;

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
import xyz.thm.addon.utils.ThmMembers;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class ThmCapeRenderMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void thm$injectCape(CallbackInfoReturnable<SkinTextures> cir) {
        THMSystem system = THMSystem.get();
        if (system == null || system.cape.get() == THMSystem.CapeType.None) return;

        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!ThmMembers.isThmMember(self)) return;

        SkinTextures original = cir.getReturnValue();
        if (original == null) return;

        Identifier id = switch (system.cape.get()) {
            case THM -> Identifier.of("thm-addon", "cape/thm.png");
            default -> null;
        };
        if (id == null) return;

        AssetInfo.TextureAssetInfo capeAsset = new AssetInfo.TextureAssetInfo(id, id);
        cir.setReturnValue(new SkinTextures(
            original.body(),
            capeAsset,
            capeAsset,
            original.model(),
            original.secure()
        ));
    }
}
