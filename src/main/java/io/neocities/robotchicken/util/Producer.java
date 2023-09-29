/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.neocities.robotchicken.util;

public interface Producer<T> {
    T create();
}
