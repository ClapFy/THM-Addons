package xyz.thm.addon.waveycapes.sim;

import xyz.thm.addon.waveycapes.util.CapePoint;
import xyz.thm.addon.waveycapes.util.Mth;
import xyz.thm.addon.waveycapes.util.Vector3;

import java.util.ArrayList;
import java.util.List;

public class StickSimulation3d implements BasicSimulation {

    public final List<Point> points = new ArrayList<>();
    public final List<Stick> sticks = new ArrayList<>();
    public Vector3 gravityDirection = new Vector3(0, -1, 0);
    public float gravity = 0;
    public int stiffness = 3;
    public float maxBend = 20;
    public float damping = 0.85f;
    public boolean sneaking = false;

    @Override
    public boolean init(int partCount) {
        if (points.size() != partCount) {
            points.clear();
            sticks.clear();
            for (int i = 0; i < partCount; i++) {
                Point point = new Point();
                point.position.y = -i;
                point.position.x = -i;
                point.locked = i == 0;
                points.add(point);
                if (i > 0) sticks.add(new Stick(points.get(i - 1), point, 1f));
            }
            return true;
        }
        return false;
    }

    @Override
    public void simulate() {
        applyGravity();
        preventClipping();
        preventSelfClipping();
        applyMotion();
        preventSelfClipping();
        preventHardBends();
        limitLength();
    }

    private void applyGravity() {
        float deltaTime = 50f / 1000f;
        Vector3 down = gravityDirection.clone().mul(gravity * deltaTime);
        for (Point p : points) {
            if (!p.locked) {
                Vector3 vel = p.position.clone().subtract(p.prevPosition).mul(damping);
                p.prevPosition.copy(p.position);
                p.position.add(vel).add(down);
            }
        }
    }

    private void applyMotion() {
        for (int i = 0; i < stiffness; i++) {
            for (int x = sticks.size() - 1; x >= 0; x--) {
                Stick stick = sticks.get(x);
                Vector3 centre = stick.pointA.position.clone().add(stick.pointB.position).div(2);
                Vector3 dir = stick.pointA.position.clone().subtract(stick.pointB.position).normalize();
                if (!stick.pointA.locked) stick.pointA.position = centre.clone().add(dir.clone().mul(stick.length / 2));
                if (!stick.pointB.locked) stick.pointB.position = centre.clone().subtract(dir.clone().mul(stick.length / 2));
            }
        }
    }

    private void limitLength() {
        for (Stick stick : sticks) {
            Vector3 dir = stick.pointA.position.clone().subtract(stick.pointB.position).normalize();
            if (!stick.pointB.locked) stick.pointB.position = stick.pointA.position.clone().subtract(dir.mul(stick.length));
        }
    }

    private void preventSelfClipping() {
        boolean clipped;
        int runs = 0;
        do {
            clipped = false;
            for (int a = 0; a < points.size(); a++) {
                for (int b = a + 1; b < points.size(); b++) {
                    Point pA = points.get(a), pB = points.get(b);
                    Vector3 dir = pA.position.clone().subtract(pB.position);
                    if (dir.sqrMagnitude() < 0.99f) {
                        clipped = true;
                        runs++;
                        dir.normalize();
                        Vector3 centre = pA.position.clone().add(pB.position).div(2);
                        if (!pA.locked) pA.position = centre.clone().add(dir.clone().mul(0.5f));
                        if (!pB.locked) pB.position = centre.clone().subtract(dir.clone().mul(0.5f));
                    }
                }
            }
        } while (clipped && runs < 32);
    }

    private void preventHardBends() {
        for (int i = 1; i < points.size() - 2; i++) {
            double angle = getAngle(points.get(i).position, points.get(i - 1).position, points.get(i + 1).position);
            if (angle < -maxBend) points.get(i + 1).position = getReplacement(points.get(i).position, points.get(i - 1).position, -maxBend * 2);
            if (angle > maxBend)  points.get(i + 1).position = getReplacement(points.get(i).position, points.get(i - 1).position, maxBend * 2);
        }
    }

    private void preventClipping() {
        Point basePoint = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Point p = points.get(i);
            if (p.position.x - basePoint.position.x > 0) p.position.x = basePoint.position.x;
            float maxZ = ((float) i / points.size()) * ((float) i / points.size()) * 5;
            float z = basePoint.position.z - p.position.z;
            if (z > maxZ) p.position.z = basePoint.position.z - maxZ;
            if (z < -maxZ) p.position.z = basePoint.position.z + maxZ;
        }
    }

    private Vector3 getReplacement(Vector3 middle, Vector3 prev, double target) {
        return middle.clone().subtract(prev).rotateDegrees((float) target).add(middle);
    }

    private double getAngle(Vector3 a, Vector3 b, Vector3 c) {
        float abx = b.x - a.x, aby = b.y - a.y;
        float cbx = b.x - c.x, cby = b.y - c.y;
        float dot = abx * cbx + aby * cby;
        float cross = abx * cby - aby * cbx;
        return Mth.atan2(cross, dot) * 180 / Math.PI;
    }

    @Override public void setGravityDirection(Vector3 d) { this.gravityDirection = d; }
    @Override public float getGravity() { return gravity; }
    @Override public void setGravity(float g) { this.gravity = g; }
    @Override public boolean isSneaking() { return sneaking; }
    @Override public void setSneaking(boolean s) { this.sneaking = s; }
    @Override public boolean empty() { return sticks.isEmpty(); }
    @Override public float getDamping() { return damping; }
    @Override public void setDamping(float d) { this.damping = d; }
    @Override public int getStiffness() { return stiffness; }
    @Override public void setStiffness(int s) { this.stiffness = s; }
    @Override public float getMaxBend() { return maxBend; }
    @Override public void setMaxBend(float b) { this.maxBend = b; }

    @Override
    public void applyMovement(Vector3 movement) {
        points.get(0).prevPosition.copy(points.get(0).position);
        points.get(0).position.add(movement);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<CapePoint> getPoints() { return (List<CapePoint>) (Object) points; }

    public static class Point implements CapePoint {
        public Vector3 position = new Vector3(0, 0, 0);
        public Vector3 prevPosition = new Vector3(0, 0, 0);
        public boolean locked;

        @Override public float getLerpX(float delta) { return Mth.lerp(delta, prevPosition.x, position.x); }
        @Override public float getLerpY(float delta) { return Mth.lerp(delta, prevPosition.y, position.y); }
        @Override public float getLerpZ(float delta) { return Mth.lerp(delta, prevPosition.z, position.z); }
    }

    public static class Stick {
        public final Point pointA, pointB;
        public final float length;
        public Stick(Point a, Point b, float length) { this.pointA = a; this.pointB = b; this.length = length; }
    }
}
