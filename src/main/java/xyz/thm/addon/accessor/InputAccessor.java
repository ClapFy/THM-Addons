/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.accessor;

public interface InputAccessor {
    default float getMovementForward() { return 0; }
    default void setMovementForward(float value) {}
    default float getMovementSideways() { return 0; }
    default void setMovementSideways(float value) {}
}
