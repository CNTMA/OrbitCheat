package com.orbitdlc.client.mixin;

import com.orbitdlc.client.FastLoad;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// В Minecraft 26.3 метод setScreen находится в классе Gui, а не в Minecraft!
@Mixin(targets = "net.minecraft.client.gui.Gui")
public abstract class FastLoadMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void orbitdlc$skipLoadingScreen(Screen screen, CallbackInfo ci) {
        // Пропускаем экран загрузки, если модуль включен
        if (FastLoad.skipLoadingOverlay && screen != null
                && screen.getClass().getSimpleName().contains("LoadingOverlay")) {
            ci.cancel();
        }
    }
}