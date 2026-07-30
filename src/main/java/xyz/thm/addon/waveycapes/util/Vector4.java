/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes.util;

public class Vector4 {
    public float x, y, z, w;

    public Vector4(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; }

    public Vector3 toVec3() { return new Vector3(x, y, z); }
}
