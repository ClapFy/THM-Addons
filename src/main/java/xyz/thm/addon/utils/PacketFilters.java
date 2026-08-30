/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.PacketType;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Packet-list helpers that do not call Meteor {@code PacketUtils}.
 * Meteor 26.1.2-22 (and other mid-26.1 builds) do not have
 * {@code getClientboundPackets()} / {@code getServerboundPackets()}; those
 * names landed later. Vanilla {@link PacketType#flow()} is stable on 26.x.
 *
 * Filters are raw so older Meteor builds that still pass {@code Class} into
 * {@code PacketListSetting} do not ClassCastException in the GUI.
 */
public final class PacketFilters {
    private PacketFilters() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Predicate clientbound() {
        return (Predicate) (Object packet) -> matchesFlow(packet, PacketFlow.CLIENTBOUND);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Predicate serverbound() {
        return (Predicate) (Object packet) -> matchesFlow(packet, PacketFlow.SERVERBOUND);
    }

    public static boolean selected(Set<?> selected, Packet<?> packet) {
        if (selected == null || selected.isEmpty()) return true;
        if (selected.contains(packet.type())) return true;
        return selected.contains(packet.getClass());
    }

    private static boolean matchesFlow(Object packet, PacketFlow flow) {
        if (packet instanceof PacketType<?> type) {
            return type.flow() == flow;
        }
        if (packet instanceof Class<?> cls) {
            String name = cls.getSimpleName();
            boolean clientbound = name.startsWith("Clientbound") || name.contains("S2C");
            boolean serverbound = name.startsWith("Serverbound") || name.contains("C2S");
            return flow == PacketFlow.CLIENTBOUND ? clientbound : serverbound;
        }
        return true;
    }
}
