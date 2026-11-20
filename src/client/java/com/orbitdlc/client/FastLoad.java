package com.orbitdlc.client;

import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import net.minecraft.client.Minecraft;

public final class FastLoad extends Module {
    static final FastLoad INSTANCE = new FastLoad();

    // Флаг для Mixin'а
    public static boolean skipLoadingOverlay = false;

    private FastLoad() {
        super("fast_load", Category.UTILITIES);
    }

    @Override
    public String description() {
        return "Ускоряет загрузку мира, пропуская экран загрузки.";
    }

    @Override
    protected void onEnable() {
        skipLoadingOverlay = true;
    }

    @Override
    protected void onDisable() {
        skipLoadingOverlay = false;
    }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null) return;
    }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("Fast Load", parent) {
            @Override
            protected void init() {
                // Настроек нет, но метод нужен для работы правого клика в ClickGui
            }
        };
    }
}