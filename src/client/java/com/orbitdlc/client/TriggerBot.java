package com.orbitdlc.client;

import com.google.gson.JsonObject;
import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import com.orbitdlc.client.mixin.MinecraftAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class TriggerBot extends Module {
    public static final TriggerBot INSTANCE = new TriggerBot();

    // Настройки
    private float attackRange = 3.0f;
    private boolean onlyCrit = false;
    private boolean weaponsOnly = false;
    private boolean noItemInUse = true;
    private boolean noContainer = true;
    private boolean targetPlayers = true;
    private boolean targetMobs = true;

    private TriggerBot() { super("trigger_bot", Category.COMBAT); }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        // Не бьем, если открыт инвентарь
        if (noContainer && mc.gui.screen() != null
                && !(mc.gui.screen() instanceof ClickGui)
                && !(mc.gui.screen() instanceof ModuleSettings)) return;

        // Не бьем, если едим или пьем
        if (noItemInUse && mc.player.isUsingItem() && mc.player.getUseItemRemainingTicks() > 0) return;

        // Ждем полного кулдауна атаки
        if (mc.player.getAttackStrengthScale(0.5f) < 1.0f) return;

        // Проверка оружия (меч или топор) через теги
        if (weaponsOnly) {
            var stack = mc.player.getMainHandItem();
            if (!stack.is(net.minecraft.tags.ItemTags.SWORDS) && !stack.is(net.minecraft.tags.ItemTags.AXES)) {
                return;
            }
        }

        // Получаем сущность, на которую смотрит игрок (прицел)
        Entity target = mc.crosshairPickEntity;
        if (target == null || target == mc.player) return;

        // Проверяем, что это живая сущность
        if (!(target instanceof LivingEntity living)) return;
        if (!living.isAlive() || living.isDeadOrDying()) return;

        // Фильтр целей (Игроки / Мобы)
        if (living instanceof Player && !targetPlayers) return;
        if (!(living instanceof Player) && !targetMobs) return;

        // Проверка дистанции
        double reach = Math.min(attackRange, 3.0) - 0.05;
        if (mc.player.distanceTo(living) > reach) return;

        // Криты (опционально)
        if (onlyCrit) {
            if (mc.player.onGround() || mc.player.isInWater() || mc.player.onClimbable() || mc.player.isSprinting()) return;
        }

        // ПРИНУДИТЕЛЬНЫЙ СБРОС СПРИНТА (для Grim и критов)
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // БЬЕМ! (Имитация клика мышью)
        ((MinecraftAccessor) mc).orbitdlc$startAttack();
    }

    @Override public String description() { return "Attacks entities in your crosshair."; }

    @Override public JsonObject saveSettings() {
        var s = super.saveSettings();
        s.addProperty("attackRange", attackRange);
        s.addProperty("onlyCrit", onlyCrit);
        s.addProperty("weaponsOnly", weaponsOnly);
        s.addProperty("noItemInUse", noItemInUse);
        s.addProperty("noContainer", noContainer);
        s.addProperty("targetPlayers", targetPlayers);
        s.addProperty("targetMobs", targetMobs);
        return s;
    }

    @Override public void loadSettings(JsonObject s) {
        attackRange = s.has("attackRange") ? s.get("attackRange").getAsFloat() : 3.0f;
        onlyCrit = s.has("onlyCrit") && s.get("onlyCrit").getAsBoolean();
        weaponsOnly = s.has("weaponsOnly") && s.get("weaponsOnly").getAsBoolean();
        noItemInUse = !s.has("noItemInUse") || s.get("noItemInUse").getAsBoolean();
        noContainer = !s.has("noContainer") || s.get("noContainer").getAsBoolean();
        targetPlayers = !s.has("targetPlayers") || s.get("targetPlayers").getAsBoolean();
        targetMobs = !s.has("targetMobs") || s.get("targetMobs").getAsBoolean();
    }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("TriggerBot", parent) {
            @Override protected void init() {
                int row = 0;
                slider("Attack range", " m", row, 10, 60,
                        () -> Math.round(attackRange * 10), v -> attackRange = v / 10f); row += 24;
                toggleAt("Only crits", row, () -> onlyCrit, () -> onlyCrit = !onlyCrit); row += 22;
                toggleAt("Weapons only", row, () -> weaponsOnly, () -> weaponsOnly = !weaponsOnly); row += 22;
                toggleAt("No item in use", row, () -> noItemInUse, () -> noItemInUse = !noItemInUse); row += 22;
                toggleAt("No container", row, () -> noContainer, () -> noContainer = !noContainer); row += 22;
                toggleAt("Players", row, () -> targetPlayers, () -> targetPlayers = !targetPlayers); row += 22;
                toggleAt("Mobs", row, () -> targetMobs, () -> targetMobs = !targetMobs); row += 22;
            }
        };
    }
}