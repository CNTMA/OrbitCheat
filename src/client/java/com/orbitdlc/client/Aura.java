package com.orbitdlc.client;

import com.google.gson.JsonObject;
import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import com.orbitdlc.client.util.AuraUtil;
import com.orbitdlc.client.util.Rotation;
import com.orbitdlc.client.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Aura extends Module {
    static final Aura INSTANCE = new Aura();

    // === Настройки ===
    private String aimMode = "FanTime";        // FanTime | FanTimeFOV | Legit
    private boolean targetNoArmor = true;
    private boolean targetHostiles = false;
    private boolean targetAnimals = false;
    private boolean targetFriends = false;
    private boolean targetPlayers = true;
    private float attackRange = 3.0f;
    private float extraRange = 0.5f;
    private boolean onlyCrit = true;
    private boolean adaptiveHit = true;
    private boolean noItemInUse = true;
    private boolean noContainerOpen = true;
    private boolean noWallCheck = true;
    private boolean shieldBreak = true;
    private boolean smartSprint = false;
    private String targetPriority = "Crosshair"; // Crosshair | Distance | HP
    private String moveCorrection = "Focus";
    private String targetVisual = "Spheres";

    // === Состояние ===
    private LivingEntity target;
    private final float[] ticks = {-1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1, -1};
    private int hitAge = 0;
    private int originalSlot = -1;

    private Aura() { super("aura", Category.COMBAT); }

    @Override protected void onEnable() {
        if (ticks[9] == -1f) ticks[9] = (int) random(9f, 13f);
        ticks[8] = 2f;
        ticks[10] = 0f;
        ticks[11] = 0f;
        var p = Minecraft.getInstance().player;
        target = null;
        RotationManager.get().reset();
    }

    @Override protected void onDisable() {
        ticks[10] = 0f;
        target = null;
        RotationManager.get().reset();
        restoreSlot();
    }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;
        if (target == null || !isValidTarget(target)) {
            LivingEntity prev = target;
            boolean fresh = prev == null || !isValidTarget(prev);
            target = (fresh && noWallCheck)
                    ? findTarget(false).or(() -> findTarget(true)).orElse(null)
                    : findTarget(true).orElse(null);
            if (target != prev && target != null) {
                ticks[10] = 0f;
                ticks[11] = 0f;
            }
        }
        restoreSlot();
        if (target == null) { ticks[8] = 1f; hitAge++; return; }
        performAttack(mc);
        updateRotation(mc);
        performAttack(mc);
        hitAge++;
    }

    private void performAttack(Minecraft mc) {
        var p = mc.player;
        if (p == null || target == null || mc.gameMode == null) return;
        if (!AuraUtil.aimsAt(p.getYRot(), p.getXRot(), attackRange, target, !noWallCheck)) return;

        if (shieldBreak && target.isBlocking()) {
            if (p.getMainHandItem().is(ItemTags.AXES)) {
                mc.gameMode.attack(p, target);
            }
            int axe = findAxeInHotbar();
            if (axe != -1 && p.getInventory().getSelectedSlot() != axe) {
                if (originalSlot == -1) originalSlot = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(axe);
            }
        }

        if (canAttackNow(mc)) {
            ticks[3] = 0f;
            mc.gameMode.attack(p, target);
            hitAge = 0;
            ticks[5] = random(8f, 10f);
            ticks[9] = (int) random(9f, 13f);
            if (ticks[2] == -1f) ticks[4] = (int) random(30f, 35f);
            ticks[2] += 1f;
        }
    }

    private boolean canAttackNow(Minecraft mc) {
        var p = mc.player;
        if (p == null || target == null) return false;
        if (noItemInUse && p.isUsingItem() && p.getUseItemRemainingTicks() > 0 && hitAge >= 8) {
            hitAge = 8;
            return false;
        }
        if (noContainerOpen && mc.gui.screen() != null
                && !(mc.gui.screen() instanceof ClickGui)
                && !(mc.gui.screen() instanceof ModuleSettings)) return false;
        if (!AuraUtil.withinReach(target, attackRange)) return false;

        if (p.fallDistance > 1.5f) {
            if (p.getCooldowns().isOnCooldown(p.getMainHandItem()) || hitAge <= 3) return false;
        } else if (p.getAttackStrengthScale(0.5f) < 0.9f || hitAge < 10) {
            return false;
        }

        return AuraUtil.isFalling()
                || (adaptiveHit && p.onGround() && !mc.options.keyJump.isDown())
                || !AuraUtil.notInLiquid();
    }

    private void updateRotation(Minecraft mc) {
        var p = mc.player;
        if (p == null || target == null) return;
        Vec3 eye = p.getEyePosition();
        Vec3 offset = AuraUtil.getAimPoint(eye, target, attackRange, !noWallCheck);
        float targetYaw, targetPitch;
        if (offset.lengthSqr() < 1e-6) {
            targetYaw = p.getYRot();
            targetPitch = p.getXRot();
        } else {
            targetYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90.0);
            targetPitch = (float) -Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));
        }

        switch (aimMode) {
            case "FanTime", "FanTimeFOV" -> rotateFanTime(mc, targetYaw, targetPitch);
            case "Legit" -> rotateLegit(mc, targetYaw, targetPitch);
        }
        ticks[3] -= 1;
        ticks[5] -= 1;
        ticks[8] -= 1;
        ticks[1] = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(
                target.getZ() - p.getZ(), target.getX() - p.getX())) - 90.0);
    }

    private void rotateFanTime(Minecraft mc, float targetYaw, float targetPitch) {
        var p = mc.player;
        if (p == null) return;
        float t = p.tickCount;
        float sw = (float) (Math.sin(t * 0.4) * 0.12 + Math.sin(t * 0.95 + 1.4) * 0.08);
        float sh = (float) (Math.cos(t * 0.5 + 0.7) * 0.10);

        // Используем targetPitch напрямую (без массива pitchSamples)
        float finalPitch = AuraUtil.smoothRot(p.getXRot(), targetPitch, 0.35f);
        float finalYaw = AuraUtil.smoothRot(p.getYRot(), targetYaw, 0.35f);

        if (hitAge <= 4 && ticks[2] % 2f == 0f) finalYaw = p.getYRot();
        float outPitch = aimMode.equals("FanTime") ? finalPitch : p.getXRot();
        RotationManager.get().rotate(new Rotation(finalYaw + sw, outPitch + sh), 220f, 1, 1);
    }

    private void rotateLegit(Minecraft mc, float targetYaw, float targetPitch) {
        var p = mc.player;
        if (p == null) return;
        float t = p.tickCount;
        float sin = (float) ((Math.sin(t * 0.31) * 0.5 + Math.sin(t * 1.7 + 2.6) * 0.2) * 8.0) / 4.0f;
        float finalYaw = AuraUtil.smoothRot(p.getYRot(), targetYaw, random(0.2f, 0.35f));
        float finalPitch = AuraUtil.smoothRot(p.getXRot(), targetPitch, random(0.15f, 0.25f));
        if (ticks[3] >= 0f) {
            finalPitch = AuraUtil.smoothRot(p.getXRot(), targetPitch, 0.35f);
            sin /= 3f;
            if (!AuraUtil.aimsAt(p.getYRot(), p.getXRot(), attackRange, target, true))
                finalYaw = AuraUtil.smoothRot(p.getYRot(), targetYaw, random(0.7f, 1f));
        }
        if (ticks[5] >= 0f && hitAge >= 1 && ticks[2] % 5f == 0f)
            finalPitch = AuraUtil.smoothRot(p.getXRot(), -targetPitch, 0.05f);
        RotationManager.get().rotate(new Rotation(finalYaw + sin, finalPitch + sin), 180f, 1, 1);
    }

    private void restoreSlot() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        if (shieldBreak && target != null && target.isBlocking()) return;
        if (originalSlot != -1) {
            p.getInventory().setSelectedSlot(originalSlot);
            originalSlot = -1;
        }
    }

    private int findAxeInHotbar() {
        var p = Minecraft.getInstance().player;
        if (p == null) return -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getItem(i).is(ItemTags.AXES)) return i;
        return -1;
    }

    private Optional<LivingEntity> findTarget(boolean throughWalls) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        var p = mc.player;
        Vec3 eye = p.getEyePosition();
        double reach = attackRange + extraRange;
        Comparator<LivingEntity> order = switch (targetPriority) {
            case "Distance" -> Comparator.comparingDouble(AuraUtil::distanceSq);
            case "HP" -> Comparator.comparingDouble(LivingEntity::getHealth);
            default -> Comparator.comparingDouble(e -> {
                Vec3 dir = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
                return Math.acos(Mth.clamp(dir.dot(
                        e.getBoundingBox().getCenter().subtract(eye).normalize()), -1, 1));
            });
        };
        Stream<LivingEntity> s = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(e -> e != p && e.isAlive())
                .filter(this::isValidTarget);
        if (!throughWalls) s = s.filter(e -> hasLoS(eye, e, reach));
        return s.min(order);
    }

    private static boolean hasLoS(Vec3 eye, LivingEntity e, double reach) {
        Vec3 c = e.getBoundingBox().getCenter();
        double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
        float yaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        return AuraUtil.aimsAt(eye, yaw, pitch, reach, e, false);
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Player p) {
            String name = p.getScoreboardName();
            boolean friend = isFriendSafe(name);
            boolean naked = p.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && p.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && p.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    && p.getItemBySlot(EquipmentSlot.FEET).isEmpty();
            if (!targetPlayers) return false;
            if (friend) return targetFriends;
            return !naked || targetNoArmor;
        }
        // Hostiles: монстры, гаст, дракон, слизь, магма-куб, железный голем, снежный голем
        if (e instanceof Monster || e instanceof Ghast || e instanceof EnderDragon
                || isKind(e, "Slime", "MagmaCube", "IronGolem", "SnowGolem"))
            return targetHostiles;
        // Animals: только животные, амбиентные, аллей
        if (e instanceof Animal || e instanceof AmbientCreature || e instanceof Allay)
            return targetAnimals;
        return false;
    }

    /** Проверка по simpleName класса — работает независимо от маппингов/пакетов. */
    private static boolean isKind(LivingEntity e, String... simpleNames) {
        String sn = e.getClass().getSimpleName();
        for (String n : simpleNames) if (sn.equals(n)) return true;
        return false;
    }

    /** OrbitFriends API неизвестен — пробуем рефлексией. После запуска можно заменить на прямой вызов. */
    private static boolean isFriendSafe(String name) {
        for (String method : new String[]{"isFriend", "contains", "has", "isFriendByName"}) {
            try {
                var m = OrbitFriends.class.getMethod(method, String.class);
                return (boolean) m.invoke(null, name);
            } catch (Exception ignored) {}
        }
        return false;
    }

    // === Настройки ===
    @Override public JsonObject saveSettings() {
        var s = super.saveSettings();
        s.addProperty("aimMode", aimMode);
        s.addProperty("targetNoArmor", targetNoArmor);
        s.addProperty("targetHostiles", targetHostiles);
        s.addProperty("targetAnimals", targetAnimals);
        s.addProperty("targetFriends", targetFriends);
        s.addProperty("targetPlayers", targetPlayers);
        s.addProperty("attackRange", attackRange);
        s.addProperty("extraRange", extraRange);
        s.addProperty("onlyCrit", onlyCrit);
        s.addProperty("adaptiveHit", adaptiveHit);
        s.addProperty("noItemInUse", noItemInUse);
        s.addProperty("noContainerOpen", noContainerOpen);
        s.addProperty("noWallCheck", noWallCheck);
        s.addProperty("shieldBreak", shieldBreak);
        s.addProperty("smartSprint", smartSprint);
        s.addProperty("targetPriority", targetPriority);
        s.addProperty("moveCorrection", moveCorrection);
        s.addProperty("targetVisual", targetVisual);
        return s;
    }

    @Override public void loadSettings(JsonObject s) {
        aimMode = s.has("aimMode") ? s.get("aimMode").getAsString() : "FanTime";
        targetNoArmor = !s.has("targetNoArmor") || s.get("targetNoArmor").getAsBoolean();
        targetHostiles = s.has("targetHostiles") && s.get("targetHostiles").getAsBoolean();
        targetAnimals = s.has("targetAnimals") && s.get("targetAnimals").getAsBoolean();
        targetFriends = s.has("targetFriends") && s.get("targetFriends").getAsBoolean();
        targetPlayers = !s.has("targetPlayers") || s.get("targetPlayers").getAsBoolean();
        attackRange = s.has("attackRange") ? s.get("attackRange").getAsFloat() : 3f;
        extraRange = s.has("extraRange") ? s.get("extraRange").getAsFloat() : 0.5f;
        onlyCrit = !s.has("onlyCrit") || s.get("onlyCrit").getAsBoolean();
        adaptiveHit = !s.has("adaptiveHit") || s.get("adaptiveHit").getAsBoolean();
        noItemInUse = !s.has("noItemInUse") || s.get("noItemInUse").getAsBoolean();
        noContainerOpen = !s.has("noContainerOpen") || s.get("noContainerOpen").getAsBoolean();
        noWallCheck = !s.has("noWallCheck") || s.get("noWallCheck").getAsBoolean();
        shieldBreak = !s.has("shieldBreak") || s.get("shieldBreak").getAsBoolean();
        smartSprint = s.has("smartSprint") && s.get("smartSprint").getAsBoolean();
        targetPriority = s.has("targetPriority") ? s.get("targetPriority").getAsString() : "Crosshair";
        moveCorrection = s.has("moveCorrection") ? s.get("moveCorrection").getAsString() : "Focus";
        targetVisual = s.has("targetVisual") ? s.get("targetVisual").getAsString() : "Spheres";
    }

    @Override public String description() { return "Automatically aims and attacks nearby targets."; }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("Aura", parent) {
            @Override protected void init() {
                int row = 0;
                row = choicesAt(java.util.List.of("FanTime", "FanTime FOV", "Legit"),
                        i -> switch (i) {
                            case 0 -> aimMode.equals("FanTime");
                            case 1 -> aimMode.equals("FanTimeFOV");
                            default -> aimMode.equals("Legit");
                        },
                        i -> aimMode = switch (i) {
                            case 0 -> "FanTime";
                            case 1 -> "FanTimeFOV";
                            default -> "Legit";
                        }, row);
                row += 4; // отступ после кнопок
                toggleAt("No armor", row, () -> targetNoArmor, () -> targetNoArmor = !targetNoArmor); row += 22;
                toggleAt("Hostiles", row, () -> targetHostiles, () -> targetHostiles = !targetHostiles); row += 22;
                toggleAt("Animals", row, () -> targetAnimals, () -> targetAnimals = !targetAnimals); row += 22;
                toggleAt("Friends", row, () -> targetFriends, () -> targetFriends = !targetFriends); row += 22;
                toggleAt("Players", row, () -> targetPlayers, () -> targetPlayers = !targetPlayers); row += 22;
                slider("Attack range", " m", row, 1, 60,
                        () -> Math.round(attackRange * 10), v -> attackRange = v / 10f); row += 24;
                slider("Extra range", " m", row, 1, 30,
                        () -> Math.round(extraRange * 10), v -> extraRange = v / 10f); row += 24;
                toggleAt("Only crits", row, () -> onlyCrit, () -> onlyCrit = !onlyCrit); row += 22;
                toggleAt("Adaptive hit", row, () -> adaptiveHit, () -> adaptiveHit = !adaptiveHit); row += 22;
                toggleAt("No item in use", row, () -> noItemInUse, () -> noItemInUse = !noItemInUse); row += 22;
                toggleAt("No container", row, () -> noContainerOpen, () -> noContainerOpen = !noContainerOpen); row += 22;
                toggleAt("No wall check", row, () -> noWallCheck, () -> noWallCheck = !noWallCheck); row += 22;
                toggleAt("Shield break", row, () -> shieldBreak, () -> shieldBreak = !shieldBreak); row += 22;
                toggleAt("Smart sprint", row, () -> smartSprint, () -> smartSprint = !smartSprint); row += 22;
            }
        };
    }

    private static float random(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }
}