/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Session-scoped totem-of-undying pop counter, keyed by player UUID. Resets on disconnect. */
public final class TotemTracker {
    private static final Map<UUID, Integer> pops = new HashMap<>();
    private static boolean subscribed = false;

    private TotemTracker() {
    }

    private static final Object LISTENER = new Object() {
        @EventHandler
        private void onPacket(PacketEvent.Receive event) {
            if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
            if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return;

            Entity entity = packet.getEntity(mc.world);
            if (!(entity instanceof PlayerEntity player)) return;

            pops.merge(player.getUuid(), 1, Integer::sum);
        }

        @EventHandler
        private void onGameLeft(GameLeftEvent event) {
            pops.clear();
        }
    };

    public static synchronized void initialize() {
        if (subscribed) return;
        subscribed = true;
        MeteorClient.EVENT_BUS.subscribe(LISTENER);
    }

    public static int get(UUID uuid) {
        return uuid == null ? 0 : pops.getOrDefault(uuid, 0);
    }

    public static int get(PlayerEntity player) {
        return player == null ? 0 : get(player.getUuid());
    }
}
