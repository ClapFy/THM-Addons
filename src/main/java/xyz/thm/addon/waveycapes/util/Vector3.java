package xyz.thm.addon.waveycapes.util;

public class Vector3 {
    public float x, y, z;

    public Vector3() {}
    public Vector3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public Vector3 clone() { return new Vector3(x, y, z); }

    public void copy(Vector3 v) { this.x = v.x; this.y = v.y; this.z = v.z; }

    public Vector3 add(Vector3 v) { x += v.x; y += v.y; z += v.z; return this; }

    public Vector3 subtract(Vector3 v) { x -= v.x; y -= v.y; z -= v.z; return this; }

    public Vector3 div(float f) { x /= f; y /= f; z /= f; return this; }

    public Vector3 mul(float f) { x *= f; y *= f; z *= f; return this; }

    public Vector3 cross(Vector3 v) {
        float fx = x, fy = y, fz = z;
        x = fy * v.z - fz * v.y;
        y = fz * v.x - fx * v.z;
        z = fx * v.y - fy * v.x;
        return this;
    }

    public Vector3 normalize() {
        float f = Mth.sqrt(x * x + y * y + z * z);
        if (f < 1e-4f) { x = 0; y = 0; z = 0; }
        else { x /= f; y /= f; z /= f; }
        return this;
    }

    public Vector3 rotateDegrees(float deg) {
        float ox = x, oy = y;
        float rad = (float) Math.toRadians(deg);
        x = Mth.cos(rad) * ox - Mth.sin(rad) * oy;
        y = Mth.sin(rad) * ox + Mth.cos(rad) * oy;
        return this;
    }

    public float sqrMagnitude() { return x * x + y * y + z * z; }
}
