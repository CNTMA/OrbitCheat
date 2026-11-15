package com.orbitdlc.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class RotationManager {
    private static final RotationManager INSTANCE = new RotationManager();
    public static RotationManager get() { return INSTANCE; }

    private State state = State.IDLE;
    private Rotation current = new Rotation(0, 0);
    private float aimSpeed = 180f;
    private int aimDuration = 10;
    private int aimTicks = 0;
    private int priority = 0;

    private RotationManager() {}

    public void rotate(Rotation target, float aimSpeed, int mode, int priority) {
        if (this.priority > priority) return;
        this.current = target;
        this.aimSpeed = aimSpeed;
        this.aimDuration = duration(mode);
        this.priority = priority;
        this.state = State.AIM;
        this.aimTicks = 0;
    }

    public void tick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || state == State.IDLE) return;
        if (state == State.AIM) {
            apply(current, aimSpeed);
            if (++aimTicks > aimDuration) state = State.RESET;
        } else if (state == State.RESET) {
            if (++aimTicks > aimDuration + 2) {
                state = State.IDLE;
                priority = 0;
            }
        }
    }

    private void apply(Rotation target, float speed) {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        float yawDelta = Mth.wrapDegrees(target.yaw() - p.getYRot());
        float pitchDelta = target.pitch() - p.getXRot();
        float total = Math.abs(yawDelta) + Math.abs(pitchDelta);
        if (total < 0.5f) return;
        float yawStep = Math.abs(yawDelta / total) * speed;
        float pitchStep = Math.abs(pitchDelta / total) * speed;
        float newYaw = p.getYRot() + Mth.clamp(yawDelta, -yawStep, yawStep);
        float newPitch = p.getXRot() + Mth.clamp(pitchDelta, -pitchStep, pitchStep);
        newYaw = patchGCD(p.getYRot(), newYaw);
        newPitch = Mth.clamp(patchGCD(p.getXRot(), newPitch), -90f, 90f);
        p.setYRot(newYaw);
        p.setXRot(newPitch);
    }

    public void reset() { state = State.IDLE; priority = 0; aimTicks = 0; }

    /** GCD-патч: сервер видит «человеческие» шаги поворота. */
    public static float patchGCD(float lastYaw, float current) {
        var mc = Minecraft.getInstance();
        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        double gcd = sens * sens * sens * 8.0;
        return (float) (lastYaw + Math.ceil(((current - lastYaw) / gcd) / 0.15) * gcd * 0.15);
    }

    private static int duration(int mode) {
        return switch (mode) {
            case 0 -> 1;
            case 1 -> 9;
            case 7 -> 30;
            default -> 10;
        };
    }

    private static Rotation drift(Rotation r) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return r;
        float t = mc.player.tickCount;
        float sw = (float) ((Math.sin(t * 0.8f) * 11.0 + Math.sin(t * 0.04f + 17.2) * 1.5
                + Math.sin(t * 0.11f + 5.8) * 3.0 + Math.sin(t * 0.07f + 12.3)) / 6.0);
        float sh = (float) ((Math.sin(t * 0.1f) + Math.sin(t * 0.03f + 54.1) * 0.5) / 4.0);
        return new Rotation(r.yaw() + Mth.clamp(sw, -0.15f, 0.15f), r.pitch() + Mth.clamp(sh, -0.15f, 0.15f));
    }

    private enum State { AIM, RESET, IDLE }
}