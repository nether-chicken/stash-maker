/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.neocities.robotchicken.util;

import net.minecraft.util.math.*;
import org.joml.*;

public interface IVec3d {
    void set(double x, double y, double z);

    default void set(Vec3i vec) {
        set(vec.getX(), vec.getY(), vec.getZ());
    }
    default void set(Vector3d vec) {
        set(vec.x, vec.y, vec.z);
    }

    void setXZ(double x, double z);

    void setY(double y);
}
