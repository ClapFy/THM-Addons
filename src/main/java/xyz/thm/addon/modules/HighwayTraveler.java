package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.HorizontalDirection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.BlockTags;
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

    private enum TravelState { FORWARD, STOPPED, PATH_FOLLOW, BACKUP }
    private enum ObstacleKind { CLEAR, JUMPABLE, WALL }

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgObstacle = settings.createGroup("Obstacle Avoidance");
    private final SettingGroup sgAutoRepair = settings.createGroup("Auto Repair Handoff");
    private final SettingGroup sgBounce = settings.createGroup("Elytra Bounce", false);

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

    private final Setting<Boolean> autoRepairSwap = sgAutoRepair.add(
        new BoolSetting.Builder()
            .name("auto-repair-swap")
            .description("When the highway ahead is broken (missing floor block or an obstruction), hand control to Highway Builder to repair it, then resume traveling.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> repairScanBlocks = sgAutoRepair.add(
        new IntSetting.Builder()
            .name("repair-scan")
            .description("How many blocks ahead to check for a broken highway.")
            .defaultValue(20)
            .min(5)
            .sliderMax(64)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Integer> repairCheckInterval = sgAutoRepair.add(
        new IntSetting.Builder()
            .name("repair-check-interval")
            .description("How often (in ticks) to re-scan the highway ahead for breaks. Lower is more accurate but more expensive.")
            .defaultValue(10)
            .min(1)
            .sliderMax(40)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Boolean> repairToleratesBounce = sgAutoRepair.add(
        new BoolSetting.Builder()
            .name("tolerate-bounce")
            .description("Also accepts a healthy highway a couple blocks above/below your current height, so normal elytra-bounce altitude changes aren't mistaken for a broken highway.")
            .defaultValue(true)
            .visible(autoRepairSwap::get)
            .build()
    );

    private final Setting<Boolean> autoBounce = sgBounce.add(
        new BoolSetting.Builder()
            .name("auto-bounce")
            .description("Deploys and continuously recasts your elytra the same way Elytra Fly's Bounce mode does, so traveling doesn't depend on Elytra Fly being separately configured.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> bounceAutoJump = sgBounce.add(
        new BoolSetting.Builder()
            .name("auto-jump")
            .description("Automatically jumps to keep the elytra bounce going.")
            .defaultValue(true)
            .visible(autoBounce::get)
            .build()
    );

    private final Setting<Boolean> bounceLockPitch = sgBounce.add(
        new BoolSetting.Builder()
            .name("pitch-lock")
            .description("Whether to lock your pitch angle while bouncing.")
            .defaultValue(true)
            .visible(autoBounce::get)
            .build()
    );

    private final Setting<Double> bouncePitch = sgBounce.add(
        new DoubleSetting.Builder()
            .name("pitch")
            .description("The pitch angle to look at while bouncing.")
            .defaultValue(85)
            .range(0, 90)
            .sliderRange(0, 90)
            .visible(() -> autoBounce.get() && bounceLockPitch.get())
            .build()
    );

    private final Setting<Boolean> bounceRestart = sgBounce.add(
        new BoolSetting.Builder()
            .name("restart")
            .description("Restarts flying with the elytra when rubberbanding.")
            .defaultValue(true)
            .visible(autoBounce::get)
            .build()
    );

    private final Setting<Integer> bounceRestartDelay = sgBounce.add(
        new IntSetting.Builder()
            .name("restart-delay")
            .description("How many ticks to wait before restarting the elytra again after rubberbanding.")
            .defaultValue(7)
            .min(0)
            .sliderRange(0, 20)
            .visible(() -> autoBounce.get() && bounceRestart.get())
            .build()
    );

    private final Setting<Boolean> bounceManualTakeoff = sgBounce.add(
        new BoolSetting.Builder()
            .name("manual-takeoff")
            .description("Does not automatically take off; you still have to jump to start gliding.")
            .defaultValue(false)
            .visible(autoBounce::get)
            .build()
    );

    private static final int OBSTACLE_WAIT_TICKS = 10;
    private static final int BOUNCE_Y_TOLERANCE = 2;

    private TravelState     travelState = TravelState.FORWARD;
    private TravelDirection activeDir   = TravelDirection.POS_Z;
    private float           hwYaw       = 0f;
    private int             fwdX        = 0;
    private int             fwdZ        = 1;

    private final Deque<BlockPos> path = new ArrayDeque<>();

    private int     backupTicks       = 0;
    private Vec3d   prevPos           = Vec3d.ZERO;
    private int     noMoveTicks       = 0;
    private int     obstacleWaitTicks = 0;
    private int     repairCheckTicks  = 0;
    private boolean bounceRubberbanded = false;
    private int     bounceRestartTicks = 0;

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
        noMoveTicks        = 0;
        backupTicks        = 0;
        obstacleWaitTicks  = 0;
        repairCheckTicks   = 0;
        bounceRubberbanded = false;
        bounceRestartTicks = bounceRestartDelay.get();
        prevPos     = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        if (autoBounce.get()) bounceRecast(mc.player);

        info("Heading " + activeDir.label + " (yaw " + (int) hwYaw + ")");
    }

    @Override
    public void onDeactivate() {
        releaseAll();
        mc.options.jumpKey.setPressed(false);
        bounceRubberbanded = false;
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

        if (autoRepairSwap.get() && ++repairCheckTicks >= repairCheckInterval.get()) {
            repairCheckTicks = 0;
            if (checkForBrokenHighwayAndHandoff()) return;
        }

        if (travelState != TravelState.PATH_FOLLOW) {
            p.setYaw(hwYaw);
        }

        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());
        noMoveTicks = pos.subtract(prevPos).lengthSquared() < 1e-4 ? noMoveTicks + 1 : 0;
        prevPos     = pos;

        if (noMoveTicks > 25 && travelState != TravelState.BACKUP && travelState != TravelState.STOPPED) {
            beginBackup();
            return;
        }

        switch (travelState) {
            case FORWARD     -> tickForward(p);
            case STOPPED     -> tickStopped(p);
            case PATH_FOLLOW -> tickPathFollow(p);
            case BACKUP      -> tickBackup(p);
        }

        if (sprintSetting.get() && travelState != TravelState.BACKUP && travelState != TravelState.STOPPED) {
            p.setSprinting(true);
        }

        // Bounce owns pitch (needs to dive to build speed) while it's active; otherwise keep it level.
        if (!autoBounce.get()) p.setPitch(0);
    }

    /**
     * Auto elytra bounce, ported from Elytra Fly's Bounce mode (same recast-on-rubberband
     * mechanics) so traveling doesn't depend on that module being separately active. Traveler
     * already owns yaw/forward-key steering itself, so (unlike Elytra Fly's Bounce) this only
     * drives takeoff, pitch and the rubberband recast — not direction or movement keys.
     */
    @EventHandler
    private void onBouncePostTick(TickEvent.Post event) {
        if (!autoBounce.get() || mc.player == null) return;
        ClientPlayerEntity p = mc.player;

        if (mc.options.jumpKey.isPressed() && !p.isGliding() && !bounceManualTakeoff.get()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        if (!bounceConditionsMet(p)) return;

        if (!bounceRubberbanded) {
            if (bounceAutoJump.get()) mc.options.jumpKey.setPressed(true);
            if (bounceLockPitch.get()) p.setPitch(bouncePitch.get().floatValue());
        }

        if (!sprintSetting.get()) {
            p.setSprinting(p.isGliding() ? p.isOnGround() : true);
        }

        if (bounceRubberbanded && bounceRestart.get()) {
            if (bounceRestartTicks > 0) {
                bounceRestartTicks--;
            } else {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                bounceRubberbanded = false;
                bounceRestartTicks = bounceRestartDelay.get();
            }
        }
    }

    @EventHandler
    private void onBouncePreTick(TickEvent.Pre event) {
        if (!autoBounce.get() || mc.player == null || sprintSetting.get()) return;
        if (bounceConditionsMet(mc.player)) mc.player.setSprinting(true);
    }

    @EventHandler
    private void onBouncePacketReceive(PacketEvent.Receive event) {
        if (!autoBounce.get() || mc.player == null) return;
        if (event.packet instanceof PlayerPositionLookS2CPacket) {
            bounceRubberbanded = true;
            mc.player.stopGliding();
        }
    }

    @EventHandler
    private void onBouncePacketSend(PacketEvent.Send event) {
        if (!autoBounce.get() || mc.player == null || sprintSetting.get()) return;
        if (event.packet instanceof ClientCommandC2SPacket cmd && cmd.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            mc.player.setSprinting(true);
        }
    }

    private boolean bounceConditionsMet(ClientPlayerEntity p) {
        BlockState blockState = p.getBlockStateAtPos();
        boolean isClimbing = blockState.isIn(BlockTags.CLIMBABLE) && !blockState.isIn(BlockTags.CAN_GLIDE_THROUGH);
        return !p.getAbilities().flying && !p.hasVehicle() && !isClimbing && !p.isTouchingWater() && !p.hasStatusEffect(StatusEffects.LEVITATION);
    }

    private boolean bounceStartGliding(ClientPlayerEntity p) {
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (LivingEntity.canGlideWith(p.getEquippedStack(slot), slot)) {
                mc.executeSync(p::startGliding);
                return true;
            }
        }
        return false;
    }

    private void bounceRecast(ClientPlayerEntity p) {
        if (bounceConditionsMet(p) && bounceStartGliding(p)) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(p, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
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
                releaseAll();
                p.setSprinting(false);
                obstacleWaitTicks = 0;
                travelState = TravelState.STOPPED;
            }
        }
    }

    private void tickStopped(ClientPlayerEntity p) {
        releaseAll();
        p.setSprinting(false);

        if (++obstacleWaitTicks < OBSTACLE_WAIT_TICKS) return;

        obstacleWaitTicks = 0;

        Deque<BlockPos> route = bfsRoute(p);
        if (route != null && !route.isEmpty()) {
            path.clear();
            path.addAll(route);
            travelState = TravelState.PATH_FOLLOW;
        } else {
            beginBackup();
        }
    }

    private static final int STEER_LOOKAHEAD = 3; 

    private void tickPathFollow(ClientPlayerEntity p) {
        Vec3d pos = new Vec3d(p.getX(), p.getY(), p.getZ());

        while (!path.isEmpty()) {
            BlockPos wp = path.peek();
            double   dx = (wp.getX() + 0.5) - pos.x;
            double   dz = (wp.getZ() + 0.5) - pos.z;
            if (dx * dx + dz * dz < 0.49) path.poll();
            else break;
        }

        if (path.isEmpty()) {
            p.setYaw(hwYaw);
            travelState = TravelState.FORWARD;
            return;
        }


        BlockPos aim = null;
        int      i   = 0;
        for (BlockPos wp : path) {
            aim = wp;
            if (++i >= STEER_LOOKAHEAD) break;
        }

        double dx = (aim.getX() + 0.5) - pos.x;
        double dz = (aim.getZ() + 0.5) - pos.z;
        p.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));

        BlockPos nextWp = path.peek();
        if (nextWp.getY() > p.getBlockPos().getY() && p.isOnGround()) p.jump();

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

    /** Returns true and hands control to Highway Builder if the highway ahead is broken. */
    private boolean checkForBrokenHighwayAndHandoff() {
        HighwayBuilderTHM builder = Modules.get().get(HighwayBuilderTHM.class);
        if (builder == null) return false;

        HorizontalDirection travelDir = HorizontalDirection.get(hwYaw);
        int tolerance = repairToleratesBounce.get() ? BOUNCE_Y_TOLERANCE : 0;
        if (builder.isHighwayAheadClean(travelDir, repairScanBlocks.get(), tolerance)) return false;

        releaseAll();
        if (isActive()) toggle();
        // Builder is usually already active but paused from the previous handoff — just resume
        // it so its stats session keeps running. If it was never on, start it fresh.
        if (builder.isActive()) builder.resumeAfterTravelHandoff();
        else builder.toggle();
        return true;
    }

    private ObstacleKind detectObstacle(ClientPlayerEntity p) {
        BlockPos feet  = p.getBlockPos();
        int      steps = lookAheadSetting.get();

        for (int step = 1; step <= steps; step++) {
            BlockPos diag = new BlockPos(feet.getX() + fwdX * step, feet.getY(), feet.getZ() + fwdZ * step);

            boolean blocked;
            if (fwdX != 0 && fwdZ != 0) {

                BlockPos cornerX = new BlockPos(feet.getX() + fwdX * step, feet.getY(), feet.getZ());
                BlockPos cornerZ = new BlockPos(feet.getX(), feet.getY(), feet.getZ() + fwdZ * step);
                blocked = solid(diag) || (solid(cornerX) && solid(cornerZ));
            } else {
                blocked = solid(diag);
            }

            if (!blocked) continue;

            boolean headBlocked  = solid(diag.up());
            boolean aboveBlocked = solid(diag.up(2));

            if (step == 1) {
                if (!headBlocked && !aboveBlocked) return ObstacleKind.JUMPABLE;
                return ObstacleKind.WALL;
            }
            if (headBlocked || aboveBlocked) return ObstacleKind.WALL;
        }
        return ObstacleKind.CLEAR;
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

        long   goalKey = Long.MIN_VALUE;
        int    iter    = 0;
        int    maxIter = W * W * 8;
        double dirLen = Math.sqrt((double)(fwdX * fwdX + fwdZ * fwdZ));

        while (!queue.isEmpty() && iter++ < maxIter) {
            long[] cur = queue.poll();
            int cx = (int) cur[0], cy = (int) cur[1], cz = (int) cur[2];

            double fwd = ((cx - sx) * fwdX + (cz - sz) * fwdZ) / dirLen;
            double lat = ((cx - sx) * fwdZ - (cz - sz) * fwdX) / dirLen;

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
