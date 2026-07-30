/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.utils;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class JoinPayload implements CustomPayload {
    public static final CustomPayload.Id<JoinPayload> ID = new CustomPayload.Id<>(Identifier.of("anarchymod", "join"));
    public static final PacketCodec<PacketByteBuf, JoinPayload> CODEC = PacketCodec.of((payload, buf) -> {},buf -> new JoinPayload());
    public static final CustomPayload.Type<PacketByteBuf, JoinPayload> TYPE = new CustomPayload.Type<>(ID, CODEC);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {return ID;}
}
