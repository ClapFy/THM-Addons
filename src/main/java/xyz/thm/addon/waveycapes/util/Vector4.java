package xyz.thm.addon.waveycapes.util;

public class Vector4 {
    public float x, y, z, w;

    public Vector4(float x, float y, float z, float w) { this.x = x; this.y = y; this.z = z; this.w = w; }

    public Vector3 toVec3() { return new Vector3(x, y, z); }
}
