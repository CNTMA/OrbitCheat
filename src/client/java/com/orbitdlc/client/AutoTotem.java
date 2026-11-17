package com.orbitdlc.client;

import com.google.gson.JsonObject;
import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

public final class AutoTotem extends Module {
    static final AutoTotem INSTANCE = new AutoTotem();

    // Настройка задержки (в тиках) и внутренний таймер
    private int delaySetting = 2;
    private int timer = 0;

    private AutoTotem() { super("auto_totem", Category.COMBAT); }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.gameMode == null) return;

        // Если таймер тикает — ждём
        if (timer > 0) {
            timer--;
            return;
        }

        var p = mc.player;
        var inv = p.getInventory();

        // Если в левой руке уже тотем — ничего не делаем
        if (inv.getItem(40).is(Items.TOTEM_OF_UNDYING)) return;

        // Ищем тотем в инвентаре (слоты 0-35)
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                // Переводим слот из Inventory (0-35) в слот InventoryMenu (9-44)
                int menuSlot = i < 9 ? 36 + i : i;

                // Свапаем с офхендом
                mc.gameMode.handleContainerInput(
                        p.inventoryMenu.containerId,
                        menuSlot,
                        40,
                        ContainerInput.SWAP,
                        p
                );

                timer = delaySetting; // Сбрасываем таймер
                return;
            }
        }
    }

    @Override
    public String description() { return "Automatically puts a totem in the offhand."; }

    @Override
    public JsonObject saveSettings() {
        var s = super.saveSettings();
        s.addProperty("delay", delaySetting);
        return s;
    }

    @Override
    public void loadSettings(JsonObject s) {
        delaySetting = s.has("delay") ? s.get("delay").getAsInt() : 2;
    }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("AutoTotem", parent) {
            @Override protected void init() {
                slider("Delay", " t", 0, 0, 10, () -> delaySetting, v -> delaySetting = v);
            }
        };
    }
}