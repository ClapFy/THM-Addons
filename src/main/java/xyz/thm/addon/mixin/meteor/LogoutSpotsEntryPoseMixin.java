/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.render.LogoutSpots;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.interfaces.LogoutSpotsPoseData;
import xyz.thm.addon.mixin.accessor.LivingEntityAccessor;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.LogoutSpots$Entry", remap = false)
public class LogoutSpotsEntryPoseMixin implements LogoutSpotsPoseData {
    @Unique private String thm$name;
    @Unique private float thm$bodyYaw;
    @Unique private float thm$yaw;
    @Unique private float thm$pitch;
    @Unique private float thm$headYaw;
    @Unique private float thm$limbPos;
    @Unique private float thm$limbSpeed;
    @Unique private float thm$limbAmplitude;
    @Unique private boolean thm$sneaking;
    @Unique private boolean thm$lowPose;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$capturePose(LogoutSpots outer, Player entity, CallbackInfo ci) {
        WalkAnimationState limbAnimator = ((LivingEntityAccessor) entity).thm$getLimbAnimator();

        thm$name = entity.getName().getString();
        thm$bodyYaw = entity.getVisualRotationYInDegrees();
        thm$yaw = entity.getYRot();
        thm$pitch = entity.getXRot();
        thm$headYaw = entity.yHeadRot;
        thm$limbPos = limbAnimator.position();
        thm$limbSpeed = limbAnimator.speed();
        thm$limbAmplitude = limbAnimator.speed(1);
        thm$sneaking = entity.isShiftKeyDown();
        thm$lowPose = entity.isVisuallyCrawling() || entity.isSwimming() || entity.getPose() == Pose.SWIMMING;
    }

    @Override
    public float thm$getBodyYaw() {
        return thm$bodyYaw;
    }

    @Override
    public String thm$getName() {
        return thm$name;
    }

    @Override
    public float thm$getYaw() {
        return thm$yaw;
    }

    @Override
    public float thm$getPitch() {
        return thm$pitch;
    }

    @Override
    public float thm$getHeadYaw() {
        return thm$headYaw;
    }

    @Override
    public float thm$getLimbPos() {
        return thm$limbPos;
    }

    @Override
    public float thm$getLimbSpeed() {
        return thm$limbSpeed;
    }

    @Override
    public float thm$getLimbAmplitude() {
        return thm$limbAmplitude;
    }

    @Override
    public boolean thm$isSneaking() {
        return thm$sneaking;
    }

    @Override
    public boolean thm$isLowPose() {
        return thm$lowPose;
    }
}
