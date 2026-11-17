package com.orbitdlc.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class MoveUtil {
    private static int priority = Integer.MAX_VALUE;
    private static float targetYaw;
    private static boolean following = false;

    private MoveUtil() {}

    public static void setFollowing(boolean b) {
        following = b;
    }

    public static void request(float yaw, int prio) {
        if (prio >= priority) return;
        priority = prio;
        targetYaw = yaw;
    }

    /**
     * Возвращает готовый вектор движения (x = стрейф, y = вперёд/назад).
     * Если коррекция не нужна, возвращает null.
     */
    public static Vec2 getCorrectedMovement() {
        if (following) {
            // Всегда идём вперёд. RotationManager повернёт тело к цели.
            // Никаких cos(-1) — никакого бега назад.
            return new Vec2(0.0f, 1.0f);
        }
        return null;
    }
}