/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.interfaces;

public interface LogoutSpotsPoseData {
    String thm$getName();
    float thm$getBodyYaw();
    float thm$getYaw();
    float thm$getPitch();
    float thm$getHeadYaw();
    float thm$getLimbPos();
    float thm$getLimbSpeed();
    float thm$getLimbAmplitude();
    boolean thm$isSneaking();
    boolean thm$isLowPose();
}
