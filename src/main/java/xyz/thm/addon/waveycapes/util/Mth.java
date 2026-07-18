/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.waveycapes.util;

public class Mth {
    public static float sqrt(float f) { return (float) Math.sqrt(f); }
    public static float sin(float f) { return (float) Math.sin(f); }
    public static float cos(float f) { return (float) Math.cos(f); }
    public static double atan2(double y, double x) { return Math.atan2(y, x); }
    public static float lerp(float delta, float from, float to) { return from + delta * (to - from); }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
