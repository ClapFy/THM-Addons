/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.PlayerInteractEntityC2SPacketAccessor;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.RenderUtilsTHM;
import xyz.thm.addon.utils.RotationUtils;
import xyz.thm.addon.utils.ThmMembers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Custom CrystalAura implementation, algorithmically inspired by BlackOut's AutoCrystalPlus
 * (https://github.com/H1ggsK/BlackOut). Unlike Meteor's stock CrystalAura, every candidate
 * placement/explosion is checked against friend splash damage (not just "don't target friends"),
 * and a force-pop override lets a lethal hit bypass the normal min-damage/ratio gate while self-
 * and friend-safety limits stay hard limits. Recently-used placement spots are put on a short
 * cooldown to avoid the instant re-place-same-block packet spam pattern.
 *
 * All rotation configuration (mode, yaw-step pacing, priority) lives in this module's own
 * settings and only ever reads from RotationUtils (getServerYaw/canSeePosition) or calls its
 * per-invocation rotate methods - it never touches RotationUtils' shared singleton fields
 * (movementFix/mouseSensFix/preserveTicks/webJumpFixEnabled), which are global to every module.
 *
 * ID-predict is ported (see "ID Predict" settings): on 2b2t-style anarchy servers it's the
 * single biggest edge a crystal aura can have, since it lets the explode happen the same tick
 * the crystal is placed instead of waiting a tick for the spawn packet to round-trip - a real
 * client-vs-client fight is usually decided by whoever's aura reacts faster. It works by
 * guessing the new crystal's entity id (ids are assigned sequentially by the server) and firing
 * a raw attack packet at that guessed id after a short delay, via a mixin-exposed setter on
 * PlayerInteractEntityC2SPacket's normally-final entityId field (PlayerInteractEntityC2SPacketAccessor).
 * If the guess is wrong the packet is just a harmless no-op server-side.
 *
 * Deliberately NOT ported from BlackOut: movement extrapolation (multi-tick position prediction -
 * DamageUtils.crystalDamage's own predict-movement flag, exposed here as predict-movement, covers
 * the common case) and "only own crystals" tracking for the explode phase - add if ever needed.
 */
public class CrystalAuraTHM extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgExplode = settings.createGroup("Explode");
    private final SettingGroup sgIdPredict = settings.createGroup("ID Predict");
    private final SettingGroup sgSwitch = settings.createGroup("Switch");
    private final SettingGroup sgRotation = settings.createGroup("Rotation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players").description("Targets players.").defaultValue(true).build());

    private final Setting<Boolean> mobs = sgGeneral.add(new BoolSetting.Builder()
        .name("mobs").description("Targets hostile mobs.").defaultValue(false).build());

    private final Setting<Boolean> animals = sgGeneral.add(new BoolSetting.Builder()
        .name("animals").description("Targets animals.").defaultValue(false).build());

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range").description("Range in which to look for targets.")
        .defaultValue(10).min(0).sliderMax(16).build());

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement").description("Predicts target movement one tick ahead when calculating damage.")
        .defaultValue(false).build());

    private final Setting<Boolean> sameTick = sgGeneral.add(new BoolSetting.Builder()
        .name("same-tick").description("Allows exploding and placing in the same tick.")
        .defaultValue(false).build());

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-eat").description("Pauses while eating/drinking/using an item.")
        .defaultValue(true).build());

    private final Setting<Double> pauseHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("pause-health").description("Pauses entirely when your health drops to or below this.")
        .defaultValue(0).min(0).sliderMax(20).build());

    // Damage

    private final Setting<Double> minDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("min-damage").description("Minimum damage a crystal must deal to the best enemy target.")
        .defaultValue(6).min(0).sliderMax(36).build());

    private final Setting<Double> maxSelfDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("Maximum damage a crystal is allowed to deal to you.")
        .defaultValue(7).min(0).sliderMax(36).build());

    private final Setting<Double> minRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("min-ratio").description("Minimum enemy/self damage ratio required.")
        .defaultValue(1.2).min(0).sliderMax(5).build());

    private final Setting<Double> maxFriendDamage = sgDamage.add(new DoubleSetting.Builder()
        .name("max-friend-damage").description("Maximum splash damage a crystal is allowed to deal to nearby friends.")
        .defaultValue(6).min(0).sliderMax(36).build());

    private final Setting<Boolean> antiSuicide = sgDamage.add(new BoolSetting.Builder()
        .name("anti-suicide").description("Never place/explode a crystal that would kill you.")
        .defaultValue(true).build());

    private final Setting<Boolean> forcePop = sgDamage.add(new BoolSetting.Builder()
        .name("force-pop").description("Ignores damage checks for a lethal hit. Safety limits still apply.")
        .defaultValue(true).build());

    private final Setting<Integer> forcePopHits = sgDamage.add(new IntSetting.Builder()
        .name("force-pop-hits").description("Ignores min-damage/ratio if the target would die within this many hits of this damage.")
        .defaultValue(1).min(1).sliderMax(5).visible(forcePop::get).build());

    // Place

    private final Setting<Boolean> doPlace = sgPlace.add(new BoolSetting.Builder()
        .name("place").description("Places crystals.").defaultValue(true).build());

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range").description("Range in which to place crystals.")
        .defaultValue(4.5).min(0).sliderMax(6).build());

    private final Setting<Boolean> oldPlacement = sgPlace.add(new BoolSetting.Builder()
        .name("1.12-placement").description("Requires two air blocks above the base, like pre-1.13 clients.")
        .defaultValue(false).build());

    private final Setting<Integer> existedCooldown = sgPlace.add(new IntSetting.Builder()
        .name("existed-cooldown").description("Ticks before placing again on a spot just placed or broken on.")
        .defaultValue(10).min(0).sliderMax(40).build());

    private final Setting<Integer> placeCPT = sgPlace.add(new IntSetting.Builder()
        .name("place-cpt").description("Crystals to place per tick.")
        .defaultValue(1).min(1).sliderMax(6).build());

    private final Setting<Integer> placeCooldown = sgPlace.add(new IntSetting.Builder()
        .name("place-cooldown").description("Ticks to wait before placing the next batch of crystals.")
        .defaultValue(0).min(0).sliderMax(20).build());

    // Explode

    private final Setting<Boolean> doExplode = sgExplode.add(new BoolSetting.Builder()
        .name("explode").description("Attacks/explodes crystals.").defaultValue(true).build());

    private final Setting<Double> explodeRange = sgExplode.add(new DoubleSetting.Builder()
        .name("explode-range").description("Range in which to attack crystals.")
        .defaultValue(4.5).min(0).sliderMax(6).build());

    private final Setting<Integer> explodeCPT = sgExplode.add(new IntSetting.Builder()
        .name("explode-cpt").description("Crystals to attack per tick.")
        .defaultValue(2).min(1).sliderMax(6).build());

    private final Setting<Integer> explodeCooldown = sgExplode.add(new IntSetting.Builder()
        .name("explode-cooldown").description("Ticks to wait before attacking the next batch of crystals.")
        .defaultValue(0).min(0).sliderMax(20).build());

    // ID Predict

    private final Setting<Boolean> idPredict = sgIdPredict.add(new BoolSetting.Builder()
        .name("id-predict").description("Attacks a new crystal by guessing its entity id, without waiting a tick.")
        .defaultValue(false).build());

    private final Setting<Integer> idStartOffset = sgIdPredict.add(new IntSetting.Builder()
        .name("id-start-offset").description("How many ids ahead of the last confirmed entity id to start guessing from.")
        .defaultValue(1).min(0).sliderMax(10).visible(idPredict::get).build());

    private final Setting<Integer> idPackets = sgIdPredict.add(new IntSetting.Builder()
        .name("id-packets").description("How many guessed ids to shoot at, to cover uncertainty in the actual assigned id.")
        .defaultValue(2).min(1).sliderMax(5).visible(idPredict::get).build());

    private final Setting<Integer> idOffset = sgIdPredict.add(new IntSetting.Builder()
        .name("id-offset").description("Spacing between guessed ids.")
        .defaultValue(1).min(1).sliderMax(5).visible(idPredict::get).build());

    private final Setting<Integer> idDelayTicks = sgIdPredict.add(new IntSetting.Builder()
        .name("id-delay-ticks").description("Ticks after placement to fire the predicted attack(s).")
        .defaultValue(1).min(0).sliderMax(10).visible(idPredict::get).build());

    // Switch

    private final Setting<Boolean> autoSwitch = sgSwitch.add(new BoolSetting.Builder()
        .name("auto-switch").description("Automatically switches to crystals in your hotbar to place.")
        .defaultValue(true).build());

    private final Setting<Boolean> silentSwitch = sgSwitch.add(new BoolSetting.Builder()
        .name("silent-switch").description("Switches back to your previous slot after placing.")
        .defaultValue(true).visible(autoSwitch::get).build());

    private final Setting<Boolean> antiWeakness = sgSwitch.add(new BoolSetting.Builder()
        .name("anti-weakness").description("Switches to your strongest weapon while you have weakness so crystals still break.")
        .defaultValue(true).build());

    // Rotation - module-local only; reads RotationUtils' public getters/rotate methods but
    // never writes to its shared singleton fields (those are global across every module).

    private final Setting<Boolean> rotate = sgRotation.add(new BoolSetting.Builder()
        .name("rotate").description("Rotates towards crystals being placed/broken.")
        .defaultValue(true).build());

    private final Setting<RotationMode> rotationMode = sgRotation.add(new EnumSetting.Builder<RotationMode>()
        .name("rotation-mode").description("Silent only sends the rotation to the server. Client also turns your camera.")
        .defaultValue(RotationMode.Silent).visible(rotate::get).build());

    private final Setting<Double> yawStepLimit = sgRotation.add(new DoubleSetting.Builder()
        .name("yaw-step-limit").description("Maximum degrees allowed to rotate per tick. 180 = unlimited (instant snap).")
        .defaultValue(180).range(1, 180).visible(rotate::get).build());

    private final Setting<Integer> rotationPriority = sgRotation.add(new IntSetting.Builder()
        .name("rotation-priority").description("Priority of this module's rotations relative to other modules using silent rotation.")
        .defaultValue(1000).min(0).sliderMax(5000).visible(() -> rotate.get() && rotationMode.get() == RotationMode.Silent).build());

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render").description("Renders placed/broken crystal positions.")
        .defaultValue(true).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How the render is shaped.")
        .defaultValue(ShapeMode.Both).build());

    private final Setting<SettingColor> placeSideColor = sgRender.add(new ColorSetting.Builder()
        .name("place-side-color").description("Side color for placements.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, 60)).build());

    private final Setting<SettingColor> placeLineColor = sgRender.add(new ColorSetting.Builder()
        .name("place-line-color").description("Line color for placements.")
        .defaultValue(new SettingColor(THMAddon.THMColor.r, THMAddon.THMColor.g, THMAddon.THMColor.b, THMAddon.THMColor.a)).build());

    private final Setting<SettingColor> breakSideColor = sgRender.add(new ColorSetting.Builder()
        .name("break-side-color").description("Side color for explosions.")
        .defaultValue(new SettingColor(255, 60, 60, 60)).build());

    private final Setting<SettingColor> breakLineColor = sgRender.add(new ColorSetting.Builder()
        .name("break-line-color").description("Line color for explosions.")
        .defaultValue(new SettingColor(255, 60, 60)).build());

    private final Setting<Integer> renderDuration = sgRender.add(new IntSetting.Builder()
        .name("render-duration").description("How long placements/explosions render for, in ticks.")
        .defaultValue(10).min(0).sliderMax(40).build());

    // State

    private final List<LivingEntity> targets = new ArrayList<>();
    private final Map<BlockPos, Integer> recentlyUsed = new HashMap<>();
    private final List<PendingPredict> pendingPredicts = new ArrayList<>();
    private int ticksEnabled;
    private int explodeTimer, placeTimer;
    private int confirmedEntityId = Integer.MIN_VALUE;
    private boolean ignoreThmMembers;
    private LivingEntity lastTarget;
    private int lastTargetTimer;

    public CrystalAuraTHM() {
        super(THMAddon.PVP, "crystal-aura-thm", "Custom crystal PvP automation with friend-safety, force-pop and id-predict logic.");
    }

    @Override
    public void onActivate() {
        targets.clear();
        recentlyUsed.clear();
        pendingPredicts.clear();
        ticksEnabled = 0;
        explodeTimer = 0;
        placeTimer = 0;
        confirmedEntityId = Integer.MIN_VALUE;
        lastTarget = null;
        lastTargetTimer = 0;
    }

    @Override
    public String getInfoString() {
        return lastTarget != null && lastTargetTimer > 0 ? lastTarget.getName().getString() : null;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (event.entity.getId() > confirmedEntityId) confirmedEntityId = event.entity.getId();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ticksEnabled++;
        if (explodeTimer > 0) explodeTimer--;
        if (placeTimer > 0) placeTimer--;
        if (lastTargetTimer > 0) lastTargetTimer--;
        recentlyUsed.entrySet().removeIf(e -> ticksEnabled - e.getValue() >= existedCooldown.get());
        tickPendingPredicts();

        double health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (health <= pauseHealth.get()) return;
        if (pauseEat.get() && mc.player.isUsingItem()) return;

        buildTargets();
        if (targets.isEmpty()) return;

        boolean exploded = false;
        if (doExplode.get() && explodeTimer <= 0) exploded = tryExplode();
        if (doPlace.get() && placeTimer <= 0 && (sameTick.get() || !exploded)) tryPlace();
    }

    // Targets

    private void buildTargets() {
        targets.clear();
        THMSystem system = THMSystem.get();
        ignoreThmMembers = system != null && system.ignoreThmMembers.get();
        double rangeSq = targetRange.get() * targetRange.get();

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || living == mc.player || !living.isAlive()) continue;

            if (living instanceof PlayerEntity player) {
                if (!players.get()) continue;
                if (player.isSpectator() || player.getAbilities().invulnerable) continue;
                if (Friends.get().isFriend(player)) continue;
                if (ignoreThmMembers && ThmMembers.isThmMember(player)) continue;
            } else if (living instanceof HostileEntity) {
                if (!mobs.get()) continue;
            } else if (living instanceof AnimalEntity) {
                if (!animals.get()) continue;
            } else {
                continue;
            }

            if (mc.player.squaredDistanceTo(living) > rangeSq) continue;
            targets.add(living);
        }
    }

    // Explode

    private boolean tryExplode() {
        List<EndCrystalEntity> crystals = new ArrayList<>();
        for (var entity : mc.world.getEntities()) {
            if (entity instanceof EndCrystalEntity crystal) crystals.add(crystal);
        }
        crystals.sort((a, b) -> Double.compare(mc.player.squaredDistanceTo(a), mc.player.squaredDistanceTo(b)));

        int hits = 0;
        for (EndCrystalEntity crystal : crystals) {
            if (hits >= explodeCPT.get()) break;
            if (mc.player.distanceTo(crystal) > explodeRange.get()) continue;

            BlockPos obsidianPos = crystal.getBlockPos().down();
            DamageResult result = computeDamage(crystal.getEntityPos(), obsidianPos);
            if (!isSafe(result)) continue;

            if (antiWeakness.get() && mc.player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                if (!switchToWeaponFor(crystal)) continue;
            }

            performRotation(crystal.getEntityPos().add(0, 0.5, 0));

            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);

            finishRotation();

            recentlyUsed.remove(obsidianPos.up());
            if (render.get()) {
                RenderUtilsTHM.renderTickingBlock(obsidianPos.up(), breakSideColor.get(), breakLineColor.get(),
                    shapeMode.get(), 0, renderDuration.get(), true, false);
            }

            lastTarget = result.enemyEntity;
            lastTargetTimer = 20;

            hits++;
        }

        if (hits > 0) explodeTimer = explodeCooldown.get();
        return hits > 0;
    }

    private boolean switchToWeaponFor(EndCrystalEntity crystal) {
        if (DamageUtils.getAttackDamage(mc.player, crystal, mc.player.getMainHandStack()) > 0) return true;

        FindItemResult weapon = InvUtils.findInHotbar(stack -> DamageUtils.getAttackDamage(mc.player, crystal, stack) > 0);
        if (!weapon.found()) return false;

        return InvUtils.swap(weapon.slot(), false);
    }

    // Place

    private void tryPlace() {
        FindItemResult hotbarCrystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
        boolean mainHand = mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL;
        boolean offHand = mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL;
        if (!mainHand && !offHand && !(autoSwitch.get() && hotbarCrystal.found())) return;

        if (!sameTick.get() && hasExplodableCrystal()) return;

        int r = (int) Math.ceil(placeRange.get());
        BlockPos base = BlockPos.ofFloored(mc.player.getEyePos());

        BlockPos bestFloor = null;
        Direction bestDir = null;
        Vec3d bestHitVec = null;
        DamageResult bestResult = null;
        double bestDamage = 0;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos floor = base.add(x, y, z);
                    if (floor.getSquaredDistance(base) > (double) r * r) continue;
                    if (floor.getY() < mc.world.getBottomY() || floor.getY() >= mc.world.getTopYInclusive()) continue;

                    var floorBlock = mc.world.getBlockState(floor).getBlock();
                    if (floorBlock != Blocks.OBSIDIAN && floorBlock != Blocks.BEDROCK) continue;

                    BlockPos above = floor.up();
                    if (!mc.world.getBlockState(above).isAir()) continue;
                    if (oldPlacement.get() && !mc.world.getBlockState(above.up()).isAir()) continue;
                    if (recentlyUsed.containsKey(above)) continue;

                    Vec3d crystalPos = new Vec3d(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
                    if (crystalPos.distanceTo(mc.player.getEyePos()) > placeRange.get() + 0.6) continue;

                    Direction dir = null;
                    Vec3d hitVec = null;
                    for (Direction d : Direction.values()) {
                        Vec3d face = Vec3d.ofCenter(floor).add(d.getOffsetX() * 0.5, d.getOffsetY() * 0.5, d.getOffsetZ() * 0.5);
                        if (face.distanceTo(mc.player.getEyePos()) > placeRange.get()) continue;
                        if (RotationUtils.canSeePosition(mc.player.getEyePos(), face)) {
                            dir = d;
                            hitVec = face;
                            break;
                        }
                    }
                    if (dir == null) continue;

                    Box occupied = new Box(floor.getX(), floor.getY() + 1, floor.getZ(),
                        floor.getX() + 1, floor.getY() + 1 + (oldPlacement.get() ? 2 : 1), floor.getZ() + 1);
                    if (EntityUtils.intersectsWithEntity(occupied, e -> !(e instanceof PlayerEntity p && p.isSpectator()))) continue;

                    DamageResult result = computeDamage(crystalPos, floor);
                    if (!isSafe(result)) continue;

                    if (result.enemyDamage > bestDamage) {
                        bestDamage = result.enemyDamage;
                        bestFloor = floor;
                        bestDir = dir;
                        bestHitVec = hitVec;
                        bestResult = result;
                    }
                }
            }
        }

        if (bestFloor == null) return;
        placeCrystal(bestFloor, bestDir, bestHitVec, hotbarCrystal, mainHand, offHand, bestResult);
    }

    private boolean hasExplodableCrystal() {
        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (mc.player.distanceTo(crystal) > explodeRange.get()) continue;
            if (isSafe(computeDamage(crystal.getEntityPos(), crystal.getBlockPos().down()))) return true;
        }
        return false;
    }

    private void placeCrystal(BlockPos floor, Direction dir, Vec3d hitVec, FindItemResult hotbarCrystal,
                               boolean mainHand, boolean offHand, DamageResult result) {
        boolean switched = false;
        Hand hand;

        if (mainHand) {
            hand = Hand.MAIN_HAND;
        } else if (offHand) {
            hand = Hand.OFF_HAND;
        } else {
            if (!InvUtils.swap(hotbarCrystal.slot(), silentSwitch.get())) return;
            switched = true;
            hand = Hand.MAIN_HAND;
        }

        performRotation(hitVec);

        mc.interactionManager.interactBlock(mc.player, hand, new BlockHitResult(hitVec, dir, floor, false));
        mc.player.swingHand(hand);

        finishRotation();
        if (switched && silentSwitch.get()) InvUtils.swapBack();

        BlockPos above = floor.up();
        recentlyUsed.put(above, ticksEnabled);
        if (render.get()) {
            RenderUtilsTHM.renderTickingBlock(above, placeSideColor.get(), placeLineColor.get(),
                shapeMode.get(), 0, renderDuration.get(), true, false);
        }

        lastTarget = result.enemyEntity;
        lastTargetTimer = 20;
        placeTimer = placeCooldown.get();

        if (idPredict.get()) schedulePredicts();
    }

    // Rotation - module-local helpers, no shared/global RotationUtils state is written.

    private void performRotation(Vec3d target) {
        if (!rotate.get()) return;

        float[] rot = RotationUtils.getRotationsTo(mc.player.getEyePos(), target);
        float yaw = stepYaw(RotationUtils.getInstance().getServerYaw(), rot[0], yawStepLimit.get().floatValue());

        if (rotationMode.get() == RotationMode.Client) {
            RotationUtils.getInstance().setRotationClient(yaw, rot[1]);
        } else {
            RotationUtils.getInstance().setRotationSilent(yaw, rot[1], rotationPriority.get());
        }
    }

    private void finishRotation() {
        if (!rotate.get() || rotationMode.get() != RotationMode.Silent) return;
        RotationUtils.getInstance().setRotationSilentSync();
    }

    private static float stepYaw(float current, float target, float maxStep) {
        if (maxStep >= 180) return target;

        float delta = RotationUtils.wrapDegrees(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    // ID Predict

    private void schedulePredicts() {
        int base = confirmedEntityId + idStartOffset.get();
        for (int i = 0; i < idPackets.get(); i++) {
            pendingPredicts.add(new PendingPredict(base + i * idOffset.get(), ticksEnabled + idDelayTicks.get()));
        }
    }

    private void tickPendingPredicts() {
        if (pendingPredicts.isEmpty()) return;

        pendingPredicts.removeIf(predict -> {
            if (ticksEnabled < predict.fireAtTick()) return false;
            sendPredictedAttack(predict.entityId());
            return true;
        });
    }

    private void sendPredictedAttack(int entityId) {
        if (mc.player == null) return;

        PlayerInteractEntityC2SPacket packet = PlayerInteractEntityC2SPacket.attack(mc.player, mc.player.isSneaking());
        ((PlayerInteractEntityC2SPacketAccessor) packet).setEntityId(entityId);
        mc.getNetworkHandler().sendPacket(packet);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private record PendingPredict(int entityId, int fireAtTick) {}

    // Damage / safety

    private DamageResult computeDamage(Vec3d crystalPos, BlockPos obsidianPos) {
        boolean predict = predictMovement.get();
        float self = DamageUtils.crystalDamage(mc.player, crystalPos, predict, obsidianPos);

        float bestEnemy = 0;
        LivingEntity bestEnemyEntity = null;
        float bestEnemyHealth = 0;
        for (LivingEntity target : targets) {
            float dmg = DamageUtils.crystalDamage(target, crystalPos, predict, obsidianPos);
            if (dmg > bestEnemy) {
                bestEnemy = dmg;
                bestEnemyEntity = target;
                bestEnemyHealth = target.getHealth() + target.getAbsorptionAmount();
            }
        }

        float bestFriend = 0;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            boolean protect = Friends.get().isFriend(player) || (ignoreThmMembers && ThmMembers.isThmMember(player));
            if (!protect) continue;

            float dmg = DamageUtils.crystalDamage(player, crystalPos, predict, obsidianPos);
            if (dmg > bestFriend) bestFriend = dmg;
        }

        return new DamageResult(bestEnemy, bestEnemyEntity, bestEnemyHealth, bestFriend, self);
    }

    private boolean isSafe(DamageResult result) {
        if (result.enemyEntity == null) return false;

        double selfHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        if (antiSuicide.get() && result.selfDamage >= selfHealth) return false;
        if (result.selfDamage > maxSelfDamage.get()) return false;
        if (result.friendDamage > maxFriendDamage.get()) return false;

        boolean pops = forcePop.get() && result.enemyDamage * forcePopHits.get() >= result.enemyHealth;
        if (pops) return true;

        if (result.enemyDamage < minDamage.get()) return false;
        return result.selfDamage <= 0 || result.enemyDamage / result.selfDamage >= minRatio.get();
    }

    private record DamageResult(float enemyDamage, LivingEntity enemyEntity, float enemyHealth,
                                 float friendDamage, float selfDamage) {}

    public enum RotationMode {
        Silent,
        Client
    }
}
