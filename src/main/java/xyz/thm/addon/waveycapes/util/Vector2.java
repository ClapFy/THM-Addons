/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes.util;

public class Vector2 {
    public float x, y;

    public Vector2(float x, float y) { this.x = x; this.y = y; }

    public void rotateDegrees(float deg) {
        float rad = (float) Math.toRadians(deg);
        float ox = x, oy = y;
        x = Mth.cos(rad) * ox - Mth.sin(rad) * oy;
        y = Mth.sin(rad) * ox + Mth.cos(rad) * oy;
    }
}
