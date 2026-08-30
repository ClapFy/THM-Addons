/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.compat;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/** Entity type constants moved to {@link EntityTypes} in Minecraft 26.2. */
public final class ClientEntities {
    private ClientEntities() {}

    public static EntityType<EndCrystal> endCrystal() {
        return EntityTypes.END_CRYSTAL;
    }
}
