package com.orbitdlc.client.mixin;

import com.orbitdlc.client.ThirdPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityTurnMixin {

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void orbitdlc$onTurn(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player && ThirdPerson.INSTANCE.isEnabled()) {
            // В Entity.turn() дельта умножается на 0.15
            ThirdPerson.INSTANCE.addMouseLook((float)(yaw * 0.15), (float)(pitch * 0.15));
            ci.cancel(); // Отменяем поворот тела
        }
    }
}