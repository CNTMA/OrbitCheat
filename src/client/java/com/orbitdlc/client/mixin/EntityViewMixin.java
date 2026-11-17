package com.orbitdlc.client.mixin;

import com.orbitdlc.client.ThirdPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityViewMixin {

    @Inject(method = "getViewYRot", at = @At("RETURN"), cancellable = true, require = 0)
    private void orbitdlc$getViewYRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this == Minecraft.getInstance().player && ThirdPerson.INSTANCE.isEnabled()) {
            cir.setReturnValue(ThirdPerson.INSTANCE.freeYaw);
        }
    }

    @Inject(method = "getViewXRot", at = @At("RETURN"), cancellable = true, require = 0)
    private void orbitdlc$getViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        if ((Object) this == Minecraft.getInstance().player && ThirdPerson.INSTANCE.isEnabled()) {
            cir.setReturnValue(ThirdPerson.INSTANCE.freePitch);
        }
    }
}