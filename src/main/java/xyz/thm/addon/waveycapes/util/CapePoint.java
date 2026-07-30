/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes.util;

public interface CapePoint {
    float getLerpX(float delta);
    float getLerpY(float delta);
    float getLerpZ(float delta);

    default Vector3 getLerpedPos(float delta) {
        return new Vector3(getLerpX(delta), getLerpY(delta), getLerpZ(delta));
    }
}
