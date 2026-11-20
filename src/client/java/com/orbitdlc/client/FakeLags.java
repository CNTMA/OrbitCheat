package com.orbitdlc.client;

import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

/**
 * Holds all outgoing (client -> server) packets and releases them in one batch
 * after a fixed delay of 3 seconds, counted from the first held packet.
 * Nothing else flushes the queue except the timer and disabling the module.
 */
public final class FakeLags extends Module {
    public static final FakeLags INSTANCE = new FakeLags();

    /** Fixed delay: 3 seconds = 60 ticks. */
    private static final int DELAY_TICKS = 60;

    private final Queue<Packet<?>> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean flushing;
    private int ticks;

    private FakeLags() {
        super("fakelags", Category.PLAYER);
    }

    /** Call from the same tick loop as AutoTool.tick(). */
    void tick(Minecraft mc) {
        if (!isEnabled()) {
            return;
        }
        if (mc.player == null || mc.getConnection() == null) {
            // Left the world: never carry old packets over to a new connection.
            queue.clear();
            ticks = 0;
            return;
        }
        if (queue.isEmpty()) {
            ticks = 0;
            return;
        }
        if (++ticks >= DELAY_TICKS) {
            flush();
        }
    }

    /**
     * Called from the Connection.send mixin. Returns true if the packet was
     * queued and must NOT be sent right now (cancel the original call).
     */
    public boolean intercept(Packet<?> packet) {
        if (!isEnabled() || flushing) {
            return false;
        }
        // Only client -> server packets (the integrated server also uses Connection.send).
        if (packet.type().flow() != PacketFlow.SERVERBOUND) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        // Do not hold login/configuration packets: only while in a world.
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            return false;
        }
        queue.offer(packet);
        return true;
    }

    /**
     * Kept only so an old mixin call does not break compilation.
     * FakeLags no longer flushes on knockback: remove the call from the mixin.
     */
    @Deprecated
    public void onVelocity() {
    }

    private void flush() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            queue.clear();
            ticks = 0;
            return;
        }
        Connection connection = mc.getConnection().getConnection();
        flushing = true;
        try {
            Packet<?> packet;
            while ((packet = queue.poll()) != null) {
                connection.send(packet);
            }
        } finally {
            flushing = false;
        }
        ticks = 0;
    }

    @Override
    protected void onEnable() {
        queue.clear();
        ticks = 0;
    }

    @Override
    protected void onDisable() {
        flush();
    }

    @Override
    public String description() {
        return "Delays outgoing packets by 3 seconds to simulate server lag.";
    }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("Fake Lags", parent) {
            @Override
            protected void init() {
                // No settings: fixed 3 second delay.
            }
        };
    }
}