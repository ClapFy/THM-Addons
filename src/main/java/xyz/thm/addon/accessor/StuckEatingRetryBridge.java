/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 */

package xyz.thm.addon.accessor;

public interface StuckEatingRetryBridge {
    boolean thm$isActivelyEating();

    boolean thm$stillNeedsToEat();

    boolean thm$hasValidCurrentEatingItem();

    long thm$beginWatchdogRecovery();

    void thm$endWatchdogRecovery(long token);

    void thm$forceStopEating(long token);

    StuckEatingRetryResult thm$forceRestartEating(long token);
}
