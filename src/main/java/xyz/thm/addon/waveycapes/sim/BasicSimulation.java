/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.waveycapes.sim;

import xyz.thm.addon.waveycapes.util.CapePoint;
import xyz.thm.addon.waveycapes.util.Vector3;

import java.util.List;

public interface BasicSimulation {
    boolean init(int partCount);
    void simulate();
    void setGravityDirection(Vector3 direction);
    float getGravity();
    void setGravity(float gravity);
    boolean isSneaking();
    void setSneaking(boolean sneaking);
    boolean empty();
    void applyMovement(Vector3 movement);
    List<CapePoint> getPoints();
    float getDamping();
    void setDamping(float damping);
    int getStiffness();
    void setStiffness(int stiffness);
    float getMaxBend();
    void setMaxBend(float maxBend);
}
