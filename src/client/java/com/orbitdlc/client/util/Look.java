package com.orbitdlc.client.util;

import net.minecraft.client.Minecraft;

public final class Look {
    private static float yaw;
    private static float pitch;

    private Look() {}

    public static void update() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            yaw = player.getYRot();
            pitch = player.getXRot();
        }
    }

    public static void set(float y, float p) {
        yaw = y;
        pitch = p;
    }

    public static float yaw() { return yaw; }
    public static float pitch() { return pitch; }
}