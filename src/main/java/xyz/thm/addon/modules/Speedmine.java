package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldEvents;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.mixin.accessor.ClientPlayerInteractionManagerTHMAccessor;
import xyz.thm.addon.mixin.accessor.PlayerInventoryAccessor;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Grim-safe packet miner.
 *
 * HOW THE 20 BPS INSTA-BREAK WORKS
 * ─────────────────────────────────
 * Minecraft calculates a "break delta" each tick:
 *   delta = miningSpeed / hardness / (requiresTool && !correctTool ? 100 : 30)
 *
 * When delta >= 1.0 the block breaks in a single tick (vanilla "instant break").
 * When delta >= breakThreshold (default 0.7) we treat it as *effectively* instant —
 * a single START+STOP pair sent in the same tick breaks the block server-side.
 * At 20 ticks/second that gives up to 20 blocks/second (20 BPS).
 *
 * GRIM BYPASS
 * ───────────
 * Normally the client sends START then STOP for each block.
 * With grimBypass enabled we send STOP *before* START, which confuses Grim's
 * sequence validator (it expects START → STOP, not STOP → START).
 *
 * CLIENT-SIDE REMOVAL (validateBreak = false)
 * ───────────────────────────────────────────
 * On high-ping servers, waiting for the server to confirm each break adds lag.
 * With validateBreak disabled we immediately set the block to AIR on the client
 * and play the break particles/sound, trusting the server will agree.
 *
 * DOUBLE BREAK
 * ────────────
 * Tracks two blocks simultaneously (primary and secondary slots).  When the
 * primary's progress hits the threshold, a STOP is sent for it and a new block
 * can start immediately — overlapping the server round-trip.
 */
public class Speedmine extends Module {

    private final SettingGroup sgMine   = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<Boolean> grimBypass = sgMine.add(new BoolSetting.Builder()
        .name("grim-bypass")
        .description("Send STOP_DESTROY_BLOCK before START to bypass Grim's sequence check.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> doubleBreak = sgMine.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Track a primary and secondary block simultaneously.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> queueEnabled = sgMine.add(new BoolSetting.Builder()
        .name("queue")
        .description("Queue extra blocks when both break slots are occupied.")
        .defaultValue(true)
        .build());

    public final Setting<Double> breakThreshold = sgMine.add(new DoubleSetting.Builder()
        .name("break-threshold")
        .description("Break-delta fraction at which a block is treated as instant. "
                   + "0.7 = 20-BPS sweet spot.")
        .defaultValue(0.7).min(0.1).max(1.0).decimalPlaces(2)
        .build());

    public final Setting<Boolean> validateBreak = sgMine.add(new BoolSetting.Builder()
        .name("validate-break")
        .description("Wait for the server to confirm each break. Disable on high ping.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> autoRebreak = sgMine.add(new BoolSetting.Builder()
        .name("auto-rebreak")
        .description("Rebreak the last position if a block reappears there.")
        .defaultValue(true)
        .build());

    public final Setting<Boolean> silentSwap = sgMine.add(new BoolSetting.Builder()
        .name("silent-swap")
        .description("Swap to the best tool via packet without visually changing your held item.")
        .defaultValue(true)
        .build());

    public final Setting<Double> range = sgMine.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum block-breaking distance.")
        .defaultValue(4.5).min(1).max(6).decimalPlaces(1)
        .build());

    private final Setting<SettingColor> renderColor = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .defaultValue(new SettingColor(0, 225, 255, 200))
        .build());

    // ── State ─────────────────────────────────────────────────────────────────

    public static Speedmine INSTANCE;

    private MineContext primary;
    private MineContext secondary;
    public  BlockPos    lastBrokenPos;
    public final Deque<BlockPos> queue = new ArrayDeque<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public Speedmine() {
        super(THMAddon.PVP, "speedmine", "Grim-safe packet miner with queue and double break.");
        INSTANCE = this;
    }

    // ── Module lifecycle ──────────────────────────────────────────────────────

    @Override
    public void onDeactivate() {
        primary       = null;
        secondary     = null;
        lastBrokenPos = null;
        queue.clear();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    private void onStartBreaking(StartBreakingBlockEvent event) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(event.blockPos);
        if (!BlockUtils.canBreak(event.blockPos, state)) return;
        if (outOfRange(event.blockPos)) return;
        event.cancel();
        if (!isMining(event.blockPos)) {
            handleBlockClick(event.blockPos, state);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // Auto-rebreak: if the last broken position got a block placed in it, break it again
        if (lastBrokenPos != null
                && autoRebreak.get()
                && primary == null && secondary == null
                && !mc.world.getBlockState(lastBrokenPos).isAir()) {
            MineContext rebreakCtx = new MineContext(lastBrokenPos, mc.world.getBlockState(lastBrokenPos), false);
            sendStopPacket(rebreakCtx, silentSwap.get());
            return;
        }

        pruneCompletedOrInvalid();

        if (secondary != null && secondary.progress() >= 1.0) finishBreak(secondary, silentSwap.get());
        if (primary   != null && primary.progress()   >= 1.0) finishBreak(primary,   silentSwap.get());

        drainQueue();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (BlockPos pos : queue) renderBlock(event, pos);
        if (secondary != null) renderMineContext(event, secondary);
        if (primary   != null) renderMineContext(event, primary);

        if (lastBrokenPos != null && autoRebreak.get()
                && !mc.world.getBlockState(lastBrokenPos).isAir()) {
            renderBlock(event, lastBrokenPos);
        }
    }

    // ── Core break logic ──────────────────────────────────────────────────────

    private void handleBlockClick(BlockPos pos, BlockState state) {
        if (isMining(pos)) return;

        boolean canAddSecondary = secondary == null && doubleBreak.get();

        if (primary == null) {
            equipBestTool(state);
            primary = new MineContext(pos, state, true);
            sendStart(pos);
        } else if (canAddSecondary) {
            sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, primary.pos);
            secondary = new MineContext(primary.pos, primary.state, false);
            primary   = new MineContext(pos, state, true);
            sendStart(pos);
        } else {
            if (queueEnabled.get() && !queue.contains(pos)) queue.addLast(pos);
        }
    }

    private void pruneCompletedOrInvalid() {
        if (primary   != null && shouldRemove(primary.pos))   primary   = null;
        if (secondary != null && shouldRemove(secondary.pos)) secondary = null;
        queue.removeIf(this::shouldRemove);
    }

    private boolean shouldRemove(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir() || outOfRange(pos);
    }

    private void drainQueue() {
        if (!queueEnabled.get() || queue.isEmpty()) return;

        if (primary == null) {
            BlockPos   pos   = queue.pollFirst();
            BlockState state = mc.world.getBlockState(pos);
            equipBestTool(state);
            primary = new MineContext(pos, state, true);
            sendStart(pos);
        } else if (doubleBreak.get() && secondary == null) {
            sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, primary.pos);
            BlockPos   nextPos   = queue.pollFirst();
            BlockState nextState = mc.world.getBlockState(nextPos);
            secondary = new MineContext(primary.pos, primary.state, false);
            primary   = new MineContext(nextPos, nextState, true);
            sendStart(nextPos);
        }
    }

    // ── Packet building ───────────────────────────────────────────────────────

    private void sendStart(BlockPos pos) {
        if (grimBypass.get()) {
            sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos);
        }
        sendSequencedAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos);
    }

    private void sendStopPacket(MineContext ctx, boolean silent) {
        if (mc.world == null || mc.player == null) return;

        int bestSlot = findBestHotbarSlot(ctx.state);
        int prevSlot = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        boolean needSwap = silent && bestSlot != -1 && bestSlot != prevSlot;

        if (!ctx.instaBreak) {
            if (needSwap) sendSequencedUpdateSlot(bestSlot);
            sendSequencedAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, ctx.pos);
            if (needSwap) sendSequencedUpdateSlot(prevSlot);
        } else if (needSwap) {
            // Vanilla insta-break: START+STOP already in sendStart, just fix the server slot
            sendSequencedUpdateSlot(bestSlot);
        }
    }

    private void finishBreak(MineContext ctx, boolean silent) {
        if (mc.world == null || mc.player == null) return;

        sendStopPacket(ctx, silent);

        if ((ctx.instaBreak || ctx.aboveThreshold) && !validateBreak.get()) {
            mc.world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, ctx.pos, Block.getRawIdFromState(ctx.state));
            mc.world.setBlockState(ctx.pos, Blocks.AIR.getDefaultState(), 3);
        }

        lastBrokenPos = ctx.pos;
        ctx.active    = false;
        if (ctx == primary)        primary   = null;
        else if (ctx == secondary) secondary = null;
    }

    // ── Silent swap ───────────────────────────────────────────────────────────

    /**
     * Silently swaps to the best hotbar tool for {@code state}, runs {@code action},
     * then swaps back — all via sequenced packets so the visual held item never changes.
     *
     * <pre>{@code
     * BlockState state = mc.world.getBlockState(pos);
     * Speedmine.INSTANCE.withSilentTool(state, () -> {
     *     mc.interactionManager.sendSequencedPacket(mc.world, seq ->
     *         new PlayerActionC2SPacket(STOP_DESTROY_BLOCK, pos, dir, seq));
     * });
     * }</pre>
     */
    public void withSilentTool(BlockState state, Runnable action) {
        if (mc.player == null) { action.run(); return; }
        int best = findBestHotbarSlot(state);
        int prev = ((PlayerInventoryAccessor) mc.player.getInventory()).getSelectedSlot();
        boolean swap = best != -1 && best != prev;
        if (swap) sendSequencedUpdateSlot(best);
        action.run();
        if (swap) sendSequencedUpdateSlot(prev);
    }

    // ── Sequenced packet helpers ──────────────────────────────────────────────

    private void sendSequencedAction(PlayerActionC2SPacket.Action action, BlockPos pos) {
        if (mc.interactionManager == null || mc.world == null) return;
        ((ClientPlayerInteractionManagerTHMAccessor) mc.interactionManager)
            .thm$sendSequencedPacket(mc.world, seq -> new PlayerActionC2SPacket(action, pos, Direction.DOWN, seq));
    }

    private void sendSequencedUpdateSlot(int slot) {
        if (mc.interactionManager == null || mc.world == null || slot < 0) return;
        ((ClientPlayerInteractionManagerTHMAccessor) mc.interactionManager)
            .thm$sendSequencedPacket(mc.world, seq -> new UpdateSelectedSlotC2SPacket(slot));
    }

    // ── Tool selection ────────────────────────────────────────────────────────

    private void equipBestTool(BlockState state) {
        if (silentSwap.get()) return;
        int slot = findBestHotbarSlot(state);
        if (slot != -1 && mc.player != null) {
            ((PlayerInventoryAccessor) mc.player.getInventory()).setSelectedSlot(slot);
        }
    }

    private int findBestHotbarSlot(BlockState state) {
        if (mc.player == null) return -1;
        int   best      = -1;
        float bestSpeed = -1;
        for (int i = 0; i < 9; i++) {
            float s = mc.player.getInventory().getStack(i).getMiningSpeedMultiplier(state);
            if (s > bestSpeed) { bestSpeed = s; best = i; }
        }
        return best;
    }

    // ── Util ─────────────────────────────────────────────────────────────────

    public void requestBreak(BlockPos pos) {
        if (mc.world == null || mc.player == null) return;
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;
        if (!isMining(pos)) handleBlockClick(pos, state);
    }

    public boolean isMining(BlockPos pos) {
        return (primary   != null && primary.pos.equals(pos))
            || (secondary != null && secondary.pos.equals(pos))
            || queue.contains(pos);
    }

    public boolean outOfRange(BlockPos pos) {
        if (mc.player == null) return true;
        double r = range.get() + 0.5;
        return mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > r * r;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderMineContext(Render3DEvent event, MineContext ctx) {
        double offset = (1.0 - ctx.progress()) / 2.0;
        Box box = new Box(
            ctx.pos.getX() + offset,       ctx.pos.getY() + offset,       ctx.pos.getZ() + offset,
            ctx.pos.getX() + 1.0 - offset, ctx.pos.getY() + 1.0 - offset, ctx.pos.getZ() + 1.0 - offset
        );
        event.renderer.box(box, renderColor.get(), renderColor.get(), ShapeMode.Lines, 0);
    }

    private void renderBlock(Render3DEvent event, BlockPos pos) {
        event.renderer.box(pos, renderColor.get(), renderColor.get(), ShapeMode.Lines, 0);
    }

    // ── MineContext ───────────────────────────────────────────────────────────

    public class MineContext {

        public final BlockPos   pos;
        public final BlockState state;
        public final long       startMs;
        public final float      hardness;
        public final boolean    isPrimary;
        public final boolean    instaBreak;
        public final boolean    aboveThreshold;
        public boolean          active = true;

        public MineContext(BlockPos pos, BlockState state, boolean isPrimary) {
            this.pos            = pos.toImmutable();
            this.state          = state;
            this.hardness       = mc.world != null ? state.getHardness(mc.world, pos) : 0;
            this.isPrimary      = isPrimary;
            this.startMs        = System.currentTimeMillis();
            float delta         = calcDelta();
            this.instaBreak     = delta >= 1.0f;
            this.aboveThreshold = delta >= breakThreshold.get().floatValue();
        }

        public double progress() {
            if (mc.player == null || mc.world == null || hardness < 0) return 0;
            float perTick = calcDelta();
            if (perTick <= 0) return Double.MAX_VALUE;
            float elapsed = Math.max((System.currentTimeMillis() - startMs) / 50f + 1f, 1f);
            float target  = isPrimary ? breakThreshold.get().floatValue() : 1.0f;
            return Math.min((perTick * elapsed) / target, 1.0);
        }

        private float calcDelta() {
            if (mc.player == null || mc.world == null) return 0;
            if (hardness <= 0) return hardness == 0f ? Float.MAX_VALUE : 0f;

            int       bestSlot = findBestHotbarSlot(state);
            ItemStack tool     = mc.player.getInventory().getStack(bestSlot < 0 ? 0 : bestSlot);

            int divisor = state.isToolRequired() && !tool.isSuitableFor(state) ? 100 : 30;

            float speed = tool.getMiningSpeedMultiplier(state);

            if (!tool.isEmpty() && speed > 1.0f) {
                int effLevel = 0;
                for (var entry : tool.getEnchantments().getEnchantmentEntries()) {
                    if (entry.getKey().matchesKey(Enchantments.EFFICIENCY)) {
                        effLevel = entry.getIntValue();
                        break;
                    }
                }
                if (effLevel > 0) speed += effLevel * effLevel + 1;
            }

            if (StatusEffectUtil.hasHaste(mc.player)) {
                speed *= 1.0f + (StatusEffectUtil.getHasteAmplifier(mc.player) + 1) * 0.2f;
            }

            if (mc.player.hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                float penalty = switch (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) {
                    case 0  -> 0.3f;
                    case 1  -> 0.09f;
                    case 2  -> 0.0027f;
                    default -> 8.1e-4f;
                };
                speed *= penalty;
            }

            if (mc.player.isSubmergedIn(FluidTags.WATER)) {
                speed *= (float) mc.player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED);
            }

            if (!mc.player.isOnGround()) speed /= 5.0f;

            return speed / hardness / divisor;
        }
    }
}
