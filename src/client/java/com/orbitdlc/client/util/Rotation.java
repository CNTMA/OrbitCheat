package com.orbitdlc.client.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public record Rotation(float yaw, float pitch) {
    public static Rotation fromVec(Vec3 from, Vec3 to) {
        Vec3 diff = to.subtract(from);
        double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horiz));
        return new Rotation(Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0f, 90.0f));
    }

    public double distanceTo(Rotation other) {
        if (other == null) return 0.0;
        double dy = Mth.wrapDegrees(other.yaw - this.yaw);
        double dp = Mth.wrapDegrees(other.pitch - this.pitch);
        return Math.hypot(Math.abs(dy), Math.abs(dp));
    }
}