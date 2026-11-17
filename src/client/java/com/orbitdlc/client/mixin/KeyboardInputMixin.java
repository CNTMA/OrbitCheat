package com.orbitdlc.client.mixin;

import com.orbitdlc.client.util.MoveUtil;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void orbitdlc$applyMoveCorrection(CallbackInfo ci) {
        // MoveUtil теперь должен возвращать Vec2 (x = стрейф, y = вперёд/назад)
        Vec2 corrected = MoveUtil.getCorrectedMovement();

        if (corrected != null) {
            // Напрямую перезаписываем вектор движения, который игра использует для ходьбы
            this.moveVector = corrected;
        }
    }
}