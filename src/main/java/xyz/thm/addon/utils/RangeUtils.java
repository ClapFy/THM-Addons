/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Direction-aware, AABB-based reach checks — the same measure the server uses.
 *
 * <p>Vanilla's {@code Entity#canInteractWithBlockAt} measures from the eyes to the <b>nearest point
 * of the block's full cube</b>, not to the block's centre and not from the player's feet. Measuring
 * to the centre is up to {@code sqrt(3)/2 ≈ 0.87} blocks pessimistic, which is why a block you can
 * mine by hand at reach 5.2 got rejected by a module configured for exactly 5.2.
 *
 * <p>The block's real {@code VoxelShape} is deliberately ignored — the server checks the full cube.
 */
public final class RangeUtils {
    private RangeUtils() {}

    /** Nearest point on {@code pos}'s full cube to {@code from}. */
    public static Vec3d nearestPoint(BlockPos pos, Vec3d from) {
        return new Vec3d(
            MathHelper.clamp(from.x, pos.getX(), pos.getX() + 1.0),
            MathHelper.clamp(from.y, pos.getY(), pos.getY() + 1.0),
            MathHelper.clamp(from.z, pos.getZ(), pos.getZ() + 1.0)
        );
    }

    /** Nearest point on {@code pos}'s full cube to the player's eyes — the spot to aim/click at. */
    public static Vec3d nearestPoint(BlockPos pos) {
        return nearestPoint(pos, mc.player.getEyePos());
    }

    public static double squaredDistance(BlockPos pos, Vec3d from) {
        return from.squaredDistanceTo(nearestPoint(pos, from));
    }

    /** Eyes to the nearest point of the block. */
    public static double distanceTo(BlockPos pos) {
        if (mc.player == null) return Double.MAX_VALUE;
        return Math.sqrt(squaredDistance(pos, mc.player.getEyePos()));
    }

    public static boolean isInRange(double reach, BlockPos pos, Vec3d from) {
        return squaredDistance(pos, from) <= reach * reach;
    }

    /** Whether {@code pos} is within {@code reach} of the player's eyes. */
    public static boolean isInRange(double reach, BlockPos pos) {
        if (mc.player == null) return false;
        return isInRange(reach, pos, mc.player.getEyePos());
    }

    /** Whether the server would accept an interaction with {@code pos} at all. */
    public static boolean isInReach(BlockPos pos) {
        if (mc.player == null) return false;
        return isInRange(mc.player.getBlockInteractionRange(), pos);
    }

    /**
     * The block face facing {@code from} — the one to click when breaking or interacting.
     *
     * <p>Picks the face whose outward side {@code from} is furthest beyond, i.e. the most face-on
     * (and so most visible) one. When {@code from} is inside the block every value is negative and
     * the same comparison yields the closest wall instead.
     */
    public static Direction nearestFace(BlockPos pos, Vec3d from) {
        Direction best = Direction.UP;
        double bestOutside = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            double sign = dir.getDirection().offset();
            double plane = dir.getAxis().choose(pos.getX(), pos.getY(), pos.getZ()) + (sign > 0 ? 1.0 : 0.0);
            double outside = (dir.getAxis().choose(from.x, from.y, from.z) - plane) * sign;

            if (outside > bestOutside) {
                bestOutside = outside;
                best = dir;
            }
        }

        return best;
    }

    /** The block face facing the player's eyes. */
    public static Direction nearestFace(BlockPos pos) {
        if (mc.player == null) return Direction.UP;
        return nearestFace(pos, mc.player.getEyePos());
    }
}
