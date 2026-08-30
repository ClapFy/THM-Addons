/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.PacketListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.common.CommonPacketTypes;
import net.minecraft.network.protocol.game.GamePacketTypes;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.utils.PacketFilters;

import java.util.Set;

public class PaketLimiter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
        .name("packet-limit")
        .description("Max packets per tick (0 = no limit).")
        .defaultValue(23)
        .min(0)
        .sliderRange(0, 1000)
        .build()
    );

    public final Setting<Boolean> allowBursts = sgGeneral.add(new BoolSetting.Builder()
        .name("allow-bursts")
        .description("Let one tick exceed the packet limit, at most once every 20 ticks.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Integer> burstLimit = sgGeneral.add(new IntSetting.Builder()
        .name("burst-limit")
        .description("Max packets on a burst tick.")
        .defaultValue(60)
        .min(0)
        .sliderRange(0, 1000)
        .visible(allowBursts::get)
        .build()
    );

    @SuppressWarnings("unchecked")
    public final Setting<Set<PacketType<? extends Packet<?>>>> bypass = sgGeneral.add(new PacketListSetting.Builder()
        .name("bypass")
        .description("C2S packets that bypass the limiter.")
        .filter(PacketFilters.serverbound())
        .build()
    );
    @SuppressWarnings("unchecked")
    public final Setting<Set<PacketType<? extends Packet<?>>>> alwaysBlock = sgGeneral.add(new PacketListSetting.Builder()
        .name("always-block")
        .description("C2S packets that are always cancelled, even if in bypass.")
        .filter(PacketFilters.serverbound())
        .build()
    );

    private int sentThisTick = 0;
    private int tick = 0;
    private int lastBurstTick = -20;

    public PaketLimiter() {
        super(THMAddon.MAIN, "paket-limiter", "Limits outgoing packets per tick with a bypass list.");
    }

    @Override
    public void onActivate() {
        if (bypass.get().isEmpty() && alwaysBlock.get().isEmpty()) {
            applyPresets();
        }
    }

    public void applyPresets() {
        bypass.get().clear();
        bypass.get().add(GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS);
        bypass.get().add(GamePacketTypes.SERVERBOUND_MOVE_PLAYER_POS_ROT);
        bypass.get().add(GamePacketTypes.SERVERBOUND_MOVE_PLAYER_ROT);
        bypass.get().add(GamePacketTypes.SERVERBOUND_MOVE_PLAYER_STATUS_ONLY);
        bypass.get().add(GamePacketTypes.SERVERBOUND_MOVE_VEHICLE);
        bypass.get().add(GamePacketTypes.SERVERBOUND_ACCEPT_TELEPORTATION);
        bypass.get().add(CommonPacketTypes.SERVERBOUND_KEEP_ALIVE);
        bypass.get().add(CommonPacketTypes.SERVERBOUND_PONG);
        bypass.get().add(GamePacketTypes.SERVERBOUND_PLAYER_COMMAND);
        alwaysBlock.get().clear();
        alwaysBlock.get().add(GamePacketTypes.SERVERBOUND_SWING);
        if (!isActive()) toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        sentThisTick = 0;
        tick++;
    }

    @EventHandler(priority = EventPriority.HIGHEST + 2)
    private void onSendPacket(PacketEvent.Send event) {
        int max = limit.get();
        if (max == 0) return;
        PacketType<?> type = event.packet.type();
        if (alwaysBlock.get().contains(type)) {
            event.cancel();
            return;
        }
        if (bypass.get().contains(type)) return;

        if (sentThisTick >= max) {
            // ponytail: fixed 20-tick burst cooldown, make it a setting if someone asks
            if (!allowBursts.get()) {
                event.cancel();
                return;
            }
            if (tick != lastBurstTick) { // not already bursting this tick — start a new one if off cooldown
                if (tick - lastBurstTick < 20) {
                    event.cancel();
                    return;
                }
                lastBurstTick = tick;
            }
            if (sentThisTick >= burstLimit.get()) {
                event.cancel();
                return;
            }
        }
        sentThisTick++;
    }
}
