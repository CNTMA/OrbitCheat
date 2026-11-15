package com.orbitdlc.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AuraUtil {
    private AuraUtil() {}

    public static double distanceSq(Vec3 eye, Entity e) {
        AABB bb = e.getBoundingBox();
        double cx = Mth.clamp(eye.x, bb.minX, bb.maxX);
        double cy = Mth.clamp(eye.y, bb.minY, bb.maxY);
        double cz = Mth.clamp(eye.z, bb.minZ, bb.maxZ);
        double dx = cx - eye.x, dy = cy - eye.y, dz = cz - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceSq(Entity e) {
        var p = Minecraft.getInstance().player;
        return p == null ? Double.POSITIVE_INFINITY : distanceSq(p.getEyePosition(), e);
    }

    public static boolean withinReach(Entity e, double reach) { return distanceSq(e) <= reach * reach; }

    public static boolean withinReach(LivingEntity e, double dist) {
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        Vec3 eye = p.getEyePosition();
        AABB bb = e.getBoundingBox();
        Vec3 d = new Vec3(
                Mth.clamp(eye.x, bb.minX, bb.maxX) - eye.x,
                Mth.clamp(eye.y, bb.minY, bb.maxY) - eye.y,
                Mth.clamp(eye.z, bb.minZ, bb.maxZ) - eye.z);
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        return aimsAt(yaw, pitch, dist, e, true);
    }

    public static boolean aimsAt(float yaw, float pitch, double dist, Entity e, boolean throughWalls) {
        var p = Minecraft.getInstance().player;
        return p != null && aimsAt(p.getEyePosition(), yaw, pitch, dist, e, throughWalls);
    }

    public static boolean aimsAt(Vec3 from, float yaw, float pitch, double dist, Entity e, boolean throughWalls) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        Vec3 end = from.add(Vec3.directionFromRotation(pitch, yaw).scale(dist));
        Optional<Vec3> hit = e.getBoundingBox().contains(from)
                ? Optional.of(from)
                : e.getBoundingBox().clip(from, end);
        if (hit.isEmpty()) return false;
        if (!throughWalls) {
            var block = mc.level.clip(new ClipContext(from, hit.get(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (block.getType() != HitResult.Type.MISS) return false;
        }
        return true;
    }

    /** 9x9x9 grid line-of-sight check. */
    public static boolean hasLineOfSight(Vec3 from, LivingEntity e, double reach) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        AABB bb = e.getBoundingBox();
        double[] t = {0, .125, .25, .375, .5, .625, .75, .875, 1};
        int last = t.length - 1;
        double reachSq = reach * reach;
        for (int a = 0; a <= last; a++)
            for (int b = 0; b <= last; b++)
                for (int c = 0; c <= last; c++) {
                    if (a > 0 && a < last && b > 0 && b < last && c > 0 && c < last) continue;
                    Vec3 pt = new Vec3(Mth.lerp(t[a], bb.minX, bb.maxX), Mth.lerp(t[b], bb.minY, bb.maxY), Mth.lerp(t[c], bb.minZ, bb.maxZ));
                    double dSq = from.distanceToSqr(pt);
                    if (dSq > reachSq) continue;
                    Vec3 end = pt.add(from.subtract(pt).scale(0.05 / Math.sqrt(dSq)));
                    var hit = mc.level.clip(new ClipContext(from, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() == HitResult.Type.MISS) return true;
                }
        return false;
    }

    public static boolean canHit(int ticks, LivingEntity target, boolean checks) {
        var mc = Minecraft.getInstance();
        return !checks && ticks >= 7 && withinReach(target, 3.0) && mc.player != null
                && mc.player.getAttackStrengthScale(0.5f) > 0.7f && willLand();
    }

    public static boolean willLand() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        double dy = (mc.player.getDeltaMovement().y - 0.08) * 0.98;
        if (dy >= 0) return false;
        AABB moved = mc.player.getBoundingBox().move(0, dy, 0);
        AABB feet = new AABB(moved.minX, moved.minY - 0.01, moved.minZ, moved.maxX, moved.minY, moved.maxZ);
        return mc.level.noCollision(mc.player, feet);
    }

    /** Возвращает направление от `eye` к точке прицеливания (или Vec3.ZERO, если не видно). */
    public static Vec3 getAimPoint(Vec3 eye, LivingEntity target, double reach, boolean throughWalls) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Vec3.ZERO;
        AABB bb = target.getBoundingBox();
        double mx = (bb.minX + bb.maxX) * 0.5, mz = (bb.minZ + bb.maxZ) * 0.5;
        Vec3 targetEye = target.getEyePosition();
        double d = eye.distanceTo(targetEye);
        double ay = Mth.lerp(Mth.clamp(d / 3.0, 0, 1), bb.minY, Mth.clamp(eye.y, bb.minY, bb.maxY));
        List<Vec3> pts = new ArrayList<>();
        pts.add(new Vec3(mx, ay, mz));
        double[] t = {0, .125, .25, .375, .5, .625, .75, .875, 1};
        int last = t.length - 1;
        for (int a = 0; a < t.length; a++)
            for (int b = 0; b < t.length; b++)
                for (int c = 0; c < t.length; c++)
                    if (a == 0 || a == last || b == 0 || b == last || c == 0 || c == last)
                        pts.add(new Vec3(Mth.lerp(t[a], bb.minX, bb.maxX), Mth.lerp(t[b], bb.minY, bb.maxY), Mth.lerp(t[c], bb.minZ, bb.maxZ)));
        for (double pad : new double[]{0, 0.2}) {
            for (boolean walls : new boolean[]{false, throughWalls}) {
                List<Vec3> ok = new ArrayList<>();
                for (Vec3 p : pts) {
                    Vec3 d2 = p.subtract(eye);
                    if (d2.length() > reach + pad) continue;
                    float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(d2.z, d2.x)) - 90.0);
                    float pitch = (float) -Math.toDegrees(Math.atan2(d2.y, Math.hypot(d2.x, d2.z)));
                    if (aimsAt(eye, yaw, pitch, (float) (reach + pad), target, walls)) ok.add(p);
                }
                if (!ok.isEmpty()) {
                    Vec3 c = ok.stream().reduce(Vec3.ZERO, Vec3::add).scale(1.0 / ok.size());
                    return ok.stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(c))).get().subtract(eye);
                }
                if (!walls) break;
            }
        }
        return Vec3.ZERO;
    }

    public static boolean notInLiquid() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        var p = mc.player;
        var fluid = mc.level.getFluidState(BlockPos.containing(p.getEyePosition()));
        if (p.hasEffect(MobEffects.LEVITATION) || p.hasEffect(MobEffects.BLINDNESS)) return false;
        if (!fluid.isEmpty()) return false;
        if (p.getAbilities().flying || p.isFallFlying() || p.onClimbable() || p.isPassenger()) return false;
        return true;
    }

    public static boolean isFalling() {
        var mc = Minecraft.getInstance();
        return mc.player != null && notInLiquid() && mc.player.fallDistance > 0f && !mc.player.onGround();
    }

    public static float smoothRot(float start, float end, float amount) {
        float a = Mth.clamp(amount, 0, 1);
        float d = Mth.wrapDegrees(end - start);
        if (Math.abs(d) < 0.5f) return end;
        float stepped = Mth.wrapDegrees(start + d * a);
        float patched = RotationManager.patchGCD(start, stepped);
        return Math.abs(Mth.wrapDegrees(end - patched)) < 0.5f ? end : patched;
    }
}