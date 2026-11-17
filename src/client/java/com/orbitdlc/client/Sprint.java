package com.orbitdlc.client;

import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import net.minecraft.client.Minecraft;

public final class Sprint extends Module {
    static final Sprint INSTANCE = new Sprint();

    private Sprint() { super("sprint", Category.MOVEMENT); }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null) return;

        // Если игрок движется вперёд (нажата W), но не спринтует — включаем спринт
        if (mc.options.keyUp.isDown() && !mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }

    @Override
    public String description() { return "Automatically sprints when moving forward."; }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("Sprint", parent) {
            @Override protected void init() {
                // Настроек нет, но метод нужен для работы правого клика
            }
        };
    }
}