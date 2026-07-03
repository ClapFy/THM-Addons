package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;

import java.util.*;

public class HighwayTraveler extends Module {

    public enum TravelDirection {
        AUTO("Auto"),
        POS_X("East (+X)"),
        NEG_X("West (-X)"),
        POS_Z("South (+Z)"),
        NEG_Z("North (-Z)"),
        POS_X_POS_Z("SE (+X +Z)"),
        POS_X_NEG_Z("NE (+X -Z)"),
        NEG_X_POS_Z("SW (-X +Z)"),
        NEG_X_NEG_Z("NW (-X -Z)");

        public final String label;
        TravelDirection(String l) { label = l; }

        @Override
        public String toString() { return label; }
    }

    private enum TravelState { FORWARD, PATH_FOLLOW, BACKUP }
    private enum ObstacleKind { CLEAR, JUMPABLE, WALL }

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgObstacle = settings.createGroup("Obstacle Avoidance");

    private final Setting<TravelDirection> dirSetting = sgGeneral.add(
        new EnumSetting.Builder<TravelDirection>()
            .name("direction")
            .description("Highway direction. AUTO snaps to your current facing when the module is enabled.")
            .defaultValue(TravelDirection.AUTO)
            .build()
    );

    private final Setting<Boolean> sprintSetting = sgGeneral.add(
        new BoolSetting.Builder()
            .name("sprint")
            .description("Sprint continuously while traveling.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoJumpSetting = sgObstacle.add(
        new BoolSetting.Builder()
            .name("auto-jump")
            .description("Jump over 1-block-tall obstacles in the path.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> avoidSetting = sgObstacle.add(
        new BoolSetting.Builder()
            .name("pathfind")
            .description("BFS pathfind around walls and multi-block obstacles.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> searchWidthSetting = sgObstacle.add(
        new IntSetting.Builder()
            .name("search-width")
            .description("Max lateral blocks to explore when routing around an obstacle.")
            .defaultValue(8)
            .min(2)
            .sliderMax(24)
            .build()
    );

    private final Setting<Integer> lookAheadSetting = sgObstacle.add(
        new IntSetting.Builder()
            .name("look-ahead")
            .description("How many blocks ahead to scan for obstacles.")
            .defaultValue(3)
            .min(1)
            .sliderMax(6)
            .build()
    );

    private TravelState     travelState = TravelState.FORWARD;
    private TravelDirection activeDir   = TravelDirection.POS_Z;
    private float           hwYaw       = 0f;
    private int             fwdX        = 0;
    private int             fwdZ        = 1;

    private final Deque<BlockPos> path = new ArrayDeque<>();

    private int   backupTicks = 0;
    private Vec3d prevPos     = Vec3d.ZERO;
    private int   noMoveTicks = 0;

    public HighwayTraveler() {
        super(THMAddon.MAIN, "highway-traveler",
            "Travels along anarchy highways with smart BFS obstacle avoidance.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        applyDirectionSetting();

        travelState = TravelState.FORWARD;
        path.clear();
        noMoveTicks = 0;
        backupTicks = 0;
        prevPos     = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        info("Heading " + activeDir.label + " (yaw " + (int) hwYaw + ")");
    }

    @Override
    public void onDeactivate() {
        releaseAll();
        if (mc.player != null) {
            mc.player.setPitch(0);
        }
    }

    @Override
    public String getInfoString() {
        return activeDir != null ? activeDir.name() : "?";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity p = mc.player;

        if (travelState != TravelState.PATH_FOLLOW) {
            p.setYaw(hwYaw);
        }

        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        noMoveTicks = pos.subtract(prevPos).lengthSquared() < 1e-4 ? noMoveTicks + 1 : 0;
        prevPos     = pos;

        if (noMoveTicks > 25 && travelState != TravelState.BACKUP) {
            beginBackup();
            return;
        }

        switch (travelState) {
            case FORWARD     -> tickForward(p);
            case PATH_FOLLOW -> tickPathFollow(p);
            case BACKUP      -> tickBackup(p);
        }

        if (sprintSetting.get() && travelState != TravelState.BACKUP) {
            p.setSprinting(true);
        }

        p.setPitch(0);
    }

    private void tickForward(ClientPlayerEntity p) {
        switch (detectObstacle(p)) {
            case CLEAR    -> pressFwd();
            case JUMPABLE -> {
                pressFwd();
                if (autoJumpSetting.get() && p.isOnGround()) p.jump();
            }
            case WALL -> {
                if (!avoidSetting.get()) {
                    pressFwd();
                    return;
                }
                Deque<BlockPos> route = bfsRoute(p);
                if (route != null && !route.isEmpty()) {
                    path.clear();
                    path.addAll(route);
                    travelState = TravelState.PATH_FOLLOW;
                } else {
                    beginBackup();
                }
            }
        }
    }

    private void tickPathFollow(ClientPlayerEntity p) {
        if (path.isEmpty()) {
            p.setYaw(hwYaw);
            travelState = TravelState.FORWARD;
            return;
        }

        BlockPos wp  = path.peek();
        Vec3d    pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        double   dx  = (wp.getX() + 0.5) - pos.x;
        double   dz  = (wp.getZ() + 0.5) - pos.z;

        if (dx * dx + dz * dz < 0.49) {
            path.poll();
            tickPathFollow(p);
            return;
        }

        p.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));

        if (wp.getY() > p.getBlockPos().getY() && p.isOnGround()) p.jump();

        pressFwd();
    }

    private void beginBackup() {
        travelState = TravelState.BACKUP;
        backupTicks = 0;
        path.clear();
        noMoveTicks = 0;
    }

    private void tickBackup(ClientPlayerEntity p) {
        releaseAll();
        mc.options.backKey.setPressed(true);
        if (++backupTicks >= 30) {
            mc.options.backKey.setPressed(false);
            travelState = TravelState.FORWARD;
        }
    }

    private ObstacleKind detectObstacle(ClientPlayerEntity p) {
        BlockPos feet      = p.getBlockPos();
        int      steps     = lookAheadSetting.get();
        boolean  foundJump = false;

        for (int step = 1; step <= steps; step++) {
            for (BlockPos col : fwdColumns(feet, step)) {
                boolean fBlocked = solid(col);
                boolean bBlocked = solid(col.up());
                boolean hBlocked = solid(col.up(2));

                if (fBlocked || bBlocked) {
                    if (!hBlocked && !solid(col.up(3))) {
                        if (step == 1) foundJump = true;
                    } else {
                        return ObstacleKind.WALL;
                    }
                }
            }
        }
        return foundJump ? ObstacleKind.JUMPABLE : ObstacleKind.CLEAR;
    }

    private List<BlockPos> fwdColumns(BlockPos feet, int step) {
        List<BlockPos> cols = new ArrayList<>(3);
        if (fwdX != 0 && fwdZ != 0) {
            cols.add(new BlockPos(feet.getX() + fwdX * step, feet.getY(), feet.getZ()));
            cols.add(new BlockPos(feet.getX(), feet.getY(), feet.getZ() + fwdZ * step));
        }
        cols.add(new BlockPos(feet.getX() + fwdX * step, feet.getY(), feet.getZ() + fwdZ * step));
        return cols;
    }

    private static final int[][] DIRS4 = {{1,0},{-1,0},{0,1},{0,-1}};

    private Deque<BlockPos> bfsRoute(ClientPlayerEntity p) {
        int sx = p.getBlockPos().getX();
        int sy = p.getBlockPos().getY();
        int sz = p.getBlockPos().getZ();
        int W  = searchWidthSetting.get();

        Map<Long, Long>    cameFrom = new LinkedHashMap<>();
        Map<Long, Integer> yAt      = new HashMap<>();

        long startKey = xzKey(sx, sz);
        cameFrom.put(startKey, -1L);
        yAt.put(startKey, sy);

        Queue<long[]> queue  = new ArrayDeque<>();
        queue.add(new long[]{sx, sy, sz});

        long goalKey = Long.MIN_VALUE;
        int  iter    = 0;
        int  maxIter = W * W * 8;

        while (!queue.isEmpty() && iter++ < maxIter) {
            long[] cur = queue.poll();
            int cx = (int) cur[0], cy = (int) cur[1], cz = (int) cur[2];

            int fwd = (cx - sx) * fwdX + (cz - sz) * fwdZ;
            int lat = (cx - sx) * fwdZ - (cz - sz) * fwdX;

            if (fwd >= 4 && Math.abs(lat) <= 1) {
                goalKey = xzKey(cx, cz);
                break;
            }
            if (Math.abs(lat) > W) continue;

            for (int[] d : DIRS4) {
                int nx = cx + d[0], nz = cz + d[1];
                long key = xzKey(nx, nz);
                if (cameFrom.containsKey(key)) continue;

                Integer ny = walkableYNear(nx, cy, nz, 3);
                if (ny == null) continue;

                cameFrom.put(key, xzKey(cx, cz));
                yAt.put(key, ny);
                queue.add(new long[]{nx, ny, nz});
            }
        }

        if (goalKey == Long.MIN_VALUE) return null;

        Deque<BlockPos> result = new ArrayDeque<>();
        long key = goalKey;
        while (key != -1L) {
            int x = decodeX(key);
            int z = decodeZ(key);
            int y = yAt.getOrDefault(key, sy);
            result.addFirst(new BlockPos(x, y, z));

            Long parent = cameFrom.get(key);
            if (parent == null || parent == -1L) break;
            key = parent;
        }

        if (!result.isEmpty()) result.pollFirst();

        return result;
    }

    private Integer walkableYNear(int x, int nearY, int z, int range) {
        for (int dy = 0; dy <= range; dy++) {
            if (dy > 0 && canStand(x, nearY + dy, z)) return nearY + dy;
            if (canStand(x, nearY - dy, z))            return nearY - dy;
        }
        return null;
    }

    private boolean canStand(int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        return solid(feet.down()) && passable(feet) && passable(feet.up());
    }

    private boolean solid(BlockPos pos) {
        if (mc.world == null) return false;
        var state = mc.world.getBlockState(pos);

        if (state.isAir()) return false;
        if (state.getBlock() instanceof FluidBlock) return false;
        if (!state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof NetherPortalBlock) return false;
        if (state.getBlock() instanceof EndPortalBlock) return false;

        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private boolean passable(BlockPos pos) { return !solid(pos); }

    private static long xzKey(int x, int z) {
        return ((long)(x + 30_000_000) << 32) | ((z + 30_000_000) & 0xFFFFFFFFL);
    }

    private static int decodeX(long key) { return (int)(key >> 32) - 30_000_000; }
    private static int decodeZ(long key) { return (int)(key & 0xFFFFFFFFL) - 30_000_000; }

    private void pressFwd() {
        mc.options.forwardKey.setPressed(true);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void releaseAll() {
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
    }

    private void applyDirectionSetting() {
        TravelDirection d = dirSetting.get();
        if (d == TravelDirection.AUTO) {
            d = snapYawToHighway(mc.player.getYaw());
        }
        activeDir = d;
        hwYaw     = yawOf(d);
        int[] s   = stepOf(d);
        fwdX      = s[0];
        fwdZ      = s[1];
    }

    private TravelDirection snapYawToHighway(float rawYaw) {
        float yaw = ((rawYaw % 360f) + 360f) % 360f;
        TravelDirection best     = TravelDirection.POS_Z;
        float           bestDiff = Float.MAX_VALUE;

        for (TravelDirection d : TravelDirection.values()) {
            if (d == TravelDirection.AUTO) continue;
            float dy   = ((yawOf(d) % 360f) + 360f) % 360f;
            float diff = Math.abs(yaw - dy);
            if (diff > 180f) diff = 360f - diff;
            if (diff < bestDiff) { bestDiff = diff; best = d; }
        }
        return best;
    }

    private static float yawOf(TravelDirection d) {
        return switch (d) {
            case POS_Z       ->    0f;
            case NEG_X       ->   90f;
            case NEG_Z       ->  180f;
            case POS_X       ->  -90f;
            case NEG_X_POS_Z ->   45f;
            case POS_X_POS_Z ->  -45f;
            case NEG_X_NEG_Z ->  135f;
            case POS_X_NEG_Z -> -135f;
            default          ->    0f;
        };
    }

    private static int[] stepOf(TravelDirection d) {
        return switch (d) {
            case POS_X       -> new int[]{ 1,  0};
            case NEG_X       -> new int[]{-1,  0};
            case POS_Z       -> new int[]{ 0,  1};
            case NEG_Z       -> new int[]{ 0, -1};
            case POS_X_POS_Z -> new int[]{ 1,  1};
            case POS_X_NEG_Z -> new int[]{ 1, -1};
            case NEG_X_POS_Z -> new int[]{-1,  1};
            case NEG_X_NEG_Z -> new int[]{-1, -1};
            default          -> new int[]{ 0,  1};
        };
    }
}