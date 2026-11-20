package com.orbitdlc.client.mixin;

import com.orbitdlc.client.FakeLags;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD"), cancellable = true)
    private void orbit$fakeLags(Packet<?> packet, CallbackInfo ci) {
        if (FakeLags.INSTANCE.intercept(packet)) {
            ci.cancel();
        }
    }
}