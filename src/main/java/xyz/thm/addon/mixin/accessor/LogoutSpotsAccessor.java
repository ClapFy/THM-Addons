/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.LogoutSpots", remap = false)
public interface LogoutSpotsAccessor {
    @Accessor("players")
    List<?> thm$getPlayers();
}
