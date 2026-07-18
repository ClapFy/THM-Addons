/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.mixin.accessor;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface LivingEntityRotationAccessor {
    @Accessor("lastBodyYaw")
    void thm$setLastBodyYaw(float value);

    @Accessor("headYaw")
    void thm$setHeadYaw(float value);

    @Accessor("lastHeadYaw")
    void thm$setLastHeadYaw(float value);
}
