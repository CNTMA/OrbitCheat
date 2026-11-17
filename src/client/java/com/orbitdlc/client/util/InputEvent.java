package com.orbitdlc.client.util;

public final class InputEvent {
    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean modified;

    public InputEvent(float forward, float strafe, boolean jump, boolean sneak) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
    }

    public float forward() { return forward; }
    public float strafe()  { return strafe; }
    public boolean jump()  { return jump; }
    public boolean sneak() { return sneak; }
    public boolean modified() { return modified; }

    public void setForward(float f) { this.forward = f; this.modified = true; }
    public void setStrafe(float s)  { this.strafe = s; this.modified = true; }
    public void setJump(boolean j)  { this.jump = j; this.modified = true; }
    public void setSneak(boolean s) { this.sneak = s; this.modified = true; }
}