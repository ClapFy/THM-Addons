/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.modules;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.WorldRendererAccessor;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.PearlPhaser;

/**
 * Reacts to your surround being mined out.
 *
 * PEARL — phases into the block a player is breaking at your feet (moved here out of {@link Phase}).
 * The throw runs through this module's own {@link PearlPhaser}, so it has its own full copy of the
 * Pearl / Self Place settings — pitch, swap, attack, swing, self-fill, self-place and its rotate.
 *
 * CLIP — no pearls, just position. A crystal can't be placed where an entity's hitbox already is, so
 * standing on the corner shared by a mined-out 2x2 puts your 0.6-wide hitbox inside all four columns
 * at once and denies every one of them. With only one neighbour broken there is no corner to take, so
 * it straddles the edge between your block and that hole instead, denying both.
 */
public class AntiMine extends Module {

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Pearl: phase into the block being mined. Clip: stand between blocks so crystals can't be placed.")
        .defaultValue(Mode.Clip)
        .build());

    /**
     * Owns the Pearl / Self Place setting groups and the throw itself, minus attack and self-fill.
     * Every setting it creates is hidden unless the mode is Pearl.
     */
    private final PearlPhaser pearl = new PearlPhaser(settings, false, () -> mode.get() == Mode.Pearl);

    private BlockPos lastPearlTarget;
    /** Both modes fire once per break — cleared again when the surround is whole. */
    private boolean triggered;

    public AntiMine() {
        super(THMAddon.PVP, "anti-mine", "Phases or clips when your surround gets mined out.");
    }

    @Override
    public void onActivate() {
        if (mode.get() == Mode.Pearl && mc.player != null) mc.player.noClip = true;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.noClip = false;
        lastPearlTarget = null;
        triggered = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (mode.get() == Mode.Pearl) {
            mc.player.noClip = true;
            tickPearl();
        } else {
            mc.player.noClip = false;
            tickClip();
        }
    }

    // ── Pearl ─────────────────────────────────────────────────────────────────

    private void tickPearl() {
        if (!pearl.canThrow()) return;

        BlockPos target = findBreakingNeighbor();
        if (target == null) {
            lastPearlTarget = null;
            return;
        }
        // One phase per block being mined, not one per tick while it's being mined
        if (target.equals(lastPearlTarget)) return;
        lastPearlTarget = target;

        straddleInto(target);
        pearl.throwPearl();
    }

    // ponytail: only checks the 4 horizontal neighbors at feet level, matches the 1x1 tower defense case; add head/vertical if a different break angle needs covering too.
    private BlockPos findBreakingNeighbor() {
        Int2ObjectMap<BlockBreakingInfo> infos = ((WorldRendererAccessor) mc.worldRenderer).meteor$getBlockBreakingInfos();
        if (infos.isEmpty()) return null;

        BlockPos feet = mc.player.getBlockPos();
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos candidate = feet.offset(dir);
            for (BlockBreakingInfo info : infos.values()) {
                if (info.getActorId() != mc.player.getId() && info.getPos().equals(candidate)) return candidate;
            }
        }
        return null;
    }

    // ── Clip ──────────────────────────────────────────────────────────────────

    private void tickClip() {
        BlockPos feet = mc.player.getBlockPos();
        // Re-arms only once the surround is whole again, so this is one clip per break, not one per tick
        if (!surroundBroken(feet)) {
            triggered = false;
            return;
        }
        if (triggered) return;
        triggered = true;

        BlockPos corner = bestOpenCorner(feet);
        if (corner != null) {
            mc.player.setPosition(corner.getX(), mc.player.getY(), corner.getZ());
            return;
        }

        // No 2x2 to sit in the middle of — straddle the edge into the single hole instead
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos hole = feet.offset(dir);
            if (open(hole.getX(), hole.getZ(), feet.getY())) {
                straddleInto(hole);
                return;
            }
        }
    }

    /** Some of the surround is gone but not all of it — i.e. we're in a hole, not standing in the open. */
    private boolean surroundBroken(BlockPos feet) {
        int holes = 0;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos n = feet.offset(dir);
            if (open(n.getX(), n.getZ(), feet.getY())) holes++;
        }
        return holes > 0 && holes < 4;
    }

    /**
     * The nearest of our feet block's four corners whose whole 2x2 of columns is mined out. Standing
     * exactly on it puts the hitbox in all four, so none of them can take a crystal.
     */
    private BlockPos bestOpenCorner(BlockPos feet) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                int cx = feet.getX() + dx, cz = feet.getZ() + dz;
                if (!open(cx - 1, cz - 1, feet.getY()) || !open(cx, cz - 1, feet.getY())
                    || !open(cx - 1, cz, feet.getY()) || !open(cx, cz, feet.getY())) continue;

                double dist = mc.player.squaredDistanceTo(cx, mc.player.getY(), cz);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = new BlockPos(cx, feet.getY(), cz);
                }
            }
        }
        return best;
    }

    /** Both the feet and head block of this column are walk-through. */
    private boolean open(int x, int z, int y) {
        for (int dy = 0; dy < 2; dy++) {
            BlockPos pos = new BlockPos(x, y + dy, z);
            if (!mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty()) return false;
        }
        return true;
    }

    /** Sits on the boundary between our block and {@code target}, so the hitbox covers both. */
    private void straddleInto(BlockPos target) {
        BlockPos feet = mc.player.getBlockPos();
        double x = mc.player.getX();
        double z = mc.player.getZ();

        int dx = target.getX() - feet.getX();
        int dz = target.getZ() - feet.getZ();
        if (dx != 0) x = target.getX() + (dx > 0 ? 0.0 : 1.0);
        if (dz != 0) z = target.getZ() + (dz > 0 ? 0.0 : 1.0);

        mc.player.setPosition(x, mc.player.getY(), z);
    }

    @Override
    public String getInfoString() {
        return mode.get().name();
    }

    public enum Mode {
        Pearl, Clip
    }
}
