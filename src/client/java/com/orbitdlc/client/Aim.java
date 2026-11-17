package com.orbitdlc.client;

import com.google.gson.JsonObject;
import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import com.orbitdlc.client.util.AuraUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class Aim extends Module {
    public static final Aim INSTANCE = new Aim();

    // === Настройки ===
    private String aimMode = "FanTime";           // FanTime | Legit
    private boolean targetPlayers = true;
    private boolean targetHostiles = false;
    private boolean targetAnimals = false;
    private boolean targetNoArmor = true;
    private boolean targetFriends = false;
    private float attackRange = 4.0f;             // Дистанция наводки (чуть больше, чем у Aura)

    // Скорости (взяты из идеальных настроек Aura)
    private float yawSpeed = 26f;
    private float pitchSpeed = 25f;
    private float smoothFactor = 0.18f;
    private float predictTicks = 2.7f;
    private boolean antiJitter = true;

    // === Состояние ===
    private LivingEntity target;
    private float currentYawSpeed = 0f;
    private float currentPitchSpeed = 0f;
    private float currentPredictTicks = 0f;
    private Vec3 smoothedPredict = Vec3.ZERO;

    private Aim() { super("aim", Category.COMBAT); }

    @Override
    protected void onDisable() {
        target = null;
        currentYawSpeed = 0f;
        currentPitchSpeed = 0f;
        currentPredictTicks = 0f;
    }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.level == null) return;

        // Если Aura включена — Aim не работает, чтобы не было конфликтов
        if (Aura.INSTANCE.isEnabled()) {
            target = null;
            return;
        }

        // Поиск цели
        if (target == null || !isValidTarget(target)) {
            target = findTarget().orElse(null);
            if (target != null) {
                smoothedPredict = Vec3.ZERO;
            }
        }

        if (target == null) {
            currentYawSpeed = 0f;
            currentPitchSpeed = 0f;
            currentPredictTicks = 0f;
            return;
        }

        // Наводимся (без атаки!)
        updateRotation(mc);
    }

    // ==================== НАВОДКА ====================

    private void updateRotation(Minecraft mc) {
        var p = mc.player;
        if (p == null || target == null) return;

        Vec3 velocity = target.getDeltaMovement();
        boolean isMoving = (velocity.x * velocity.x + velocity.z * velocity.z > 0.0025);

        float targetTick = isMoving ? predictTicks : 0f;
        float step = 0.1f;
        if (currentPredictTicks < targetTick) {
            currentPredictTicks = Math.min(targetTick, currentPredictTicks + step);
        } else if (currentPredictTicks > targetTick) {
            currentPredictTicks = Math.max(targetTick, currentPredictTicks - step);
        }

        double predictX = velocity.x * currentPredictTicks;
        double predictZ = velocity.z * currentPredictTicks;
        double predictY = target.onGround() ? velocity.y * currentPredictTicks : 0;

        Vec3 playerVelocity = p.getDeltaMovement();
        Vec3 playerEye = p.getEyePosition().add(playerVelocity.scale(currentPredictTicks * 0.5));

        Vec3 aimPos = target.getBoundingBox().getCenter().add(predictX, predictY, predictZ);
        Vec3 offset = aimPos.subtract(playerEye);

        float targetYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));

        float currentPitchSpeedTarget = pitchSpeed;
        if (!p.onGround()) currentPitchSpeedTarget *= 1.3f;

        switch (aimMode) {
            case "FanTime" -> rotateFanTime(mc, targetYaw, targetPitch, currentPitchSpeedTarget);
            case "Legit" -> rotateLegit(mc, targetYaw, targetPitch, currentPitchSpeedTarget);
        }
    }

    private void rotateFanTime(Minecraft mc, float targetYaw, float targetPitch, float targetPitchSpeed) {
        var p = mc.player;
        if (p == null) return;

        if (currentYawSpeed < yawSpeed) currentYawSpeed = Math.min(yawSpeed, currentYawSpeed + 1.0f);
        else if (currentYawSpeed > yawSpeed) currentYawSpeed = Math.max(yawSpeed, currentYawSpeed - 1.0f);

        if (currentPitchSpeed < targetPitchSpeed) currentPitchSpeed = Math.min(targetPitchSpeed, currentPitchSpeed + 1.0f);
        else if (currentPitchSpeed > targetPitchSpeed) currentPitchSpeed = Math.max(targetPitchSpeed, currentPitchSpeed - 1.0f);

        float deltaYaw = AuraUtil.getMouseDelta(p.getYRot(), targetYaw, currentYawSpeed, smoothFactor, antiJitter);
        float deltaPitch = AuraUtil.getMouseDelta(p.getXRot(), targetPitch, currentPitchSpeed, smoothFactor, antiJitter);

        p.setYRot(p.getYRot() + deltaYaw);
        p.setXRot(Mth.clamp(p.getXRot() + deltaPitch, -90f, 90f));
    }

    private void rotateLegit(Minecraft mc, float targetYaw, float targetPitch, float targetPitchSpeed) {
        var p = mc.player;
        if (p == null) return;

        float legitMaxYaw = yawSpeed * 0.9f;
        float legitMaxPitch = targetPitchSpeed * 0.9f;

        if (currentYawSpeed < legitMaxYaw) currentYawSpeed = Math.min(legitMaxYaw, currentYawSpeed + 1.2f);
        else if (currentYawSpeed > legitMaxYaw) currentYawSpeed = Math.max(legitMaxYaw, currentYawSpeed - 1.2f);

        if (currentPitchSpeed < legitMaxPitch) currentPitchSpeed = Math.min(legitMaxPitch, currentPitchSpeed + 1.2f);
        else if (currentPitchSpeed > legitMaxPitch) currentPitchSpeed = Math.max(legitMaxPitch, currentPitchSpeed - 1.2f);

        float deltaYaw = AuraUtil.getMouseDelta(p.getYRot(), targetYaw, currentYawSpeed, smoothFactor * 0.8f, antiJitter);
        float deltaPitch = AuraUtil.getMouseDelta(p.getXRot(), targetPitch, currentPitchSpeed, smoothFactor * 0.8f, antiJitter);

        p.setYRot(p.getYRot() + deltaYaw);
        p.setXRot(Mth.clamp(p.getXRot() + deltaPitch, -90f, 90f));
    }

    // ==================== ПОИСК ЦЕЛИ ====================

    private Optional<LivingEntity> findTarget() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return Optional.empty();
        var p = mc.player;
        Vec3 eye = p.getEyePosition();
        double reach = attackRange;

        Comparator<LivingEntity> order = Comparator.comparingDouble(e -> {
            Vec3 dir = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
            return Math.acos(Mth.clamp(dir.dot(e.getBoundingBox().getCenter().subtract(eye).normalize()), -1, 1));
        });

        Stream<LivingEntity> s = StreamSupport.stream(mc.level.entitiesForRendering().spliterator(), false)
                .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast)
                .filter(e -> e != p && e.isAlive())
                .filter(e -> AuraUtil.distanceSq(eye, e) <= reach * reach)
                .filter(this::isValidTarget);

        return s.min(order);
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == null || !e.isAlive()) return false;
        if (e instanceof Player p) {
            String name = p.getScoreboardName();
            boolean friend = isFriendSafe(name);
            boolean naked = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()
                    && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()
                    && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()
                    && p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
            if (!targetPlayers) return false;
            if (friend) return targetFriends;
            return !naked || targetNoArmor;
        }
        if (e instanceof Mob || isKind(e, "Slime", "MagmaCube", "IronGolem", "SnowGolem"))
            return targetHostiles;
        if (e instanceof Animal || e instanceof AmbientCreature || isKind(e, "Allay"))
            return targetAnimals;
        return false;
    }

    private static boolean isKind(LivingEntity e, String... names) {
        String sn = e.getClass().getSimpleName();
        for (String n : names) if (sn.equals(n)) return true;
        return false;
    }

    private static boolean isFriendSafe(String name) {
        try {
            var method = OrbitFriends.class.getMethod("isFriend", String.class);
            return (boolean) method.invoke(null, name);
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== СОХРАНЕНИЕ ====================

    @Override public JsonObject saveSettings() {
        var s = super.saveSettings();
        s.addProperty("aimMode", aimMode);
        s.addProperty("targetPlayers", targetPlayers);
        s.addProperty("targetHostiles", targetHostiles);
        s.addProperty("targetAnimals", targetAnimals);
        s.addProperty("targetNoArmor", targetNoArmor);
        s.addProperty("targetFriends", targetFriends);
        s.addProperty("attackRange", attackRange);
        s.addProperty("yawSpeed", yawSpeed);
        s.addProperty("pitchSpeed", pitchSpeed);
        s.addProperty("smoothFactor", smoothFactor);
        s.addProperty("predictTicks", predictTicks);
        s.addProperty("antiJitter", antiJitter);
        return s;
    }

    @Override public void loadSettings(JsonObject s) {
        aimMode = s.has("aimMode") ? s.get("aimMode").getAsString() : "FanTime";
        targetPlayers = !s.has("targetPlayers") || s.get("targetPlayers").getAsBoolean();
        targetHostiles = s.has("targetHostiles") && s.get("targetHostiles").getAsBoolean();
        targetAnimals = s.has("targetAnimals") && s.get("targetAnimals").getAsBoolean();
        targetNoArmor = !s.has("targetNoArmor") || s.get("targetNoArmor").getAsBoolean();
        targetFriends = s.has("targetFriends") && s.get("targetFriends").getAsBoolean();
        attackRange = s.has("attackRange") ? s.get("attackRange").getAsFloat() : 4.0f;
        yawSpeed = s.has("yawSpeed") ? s.get("yawSpeed").getAsFloat() : 26f;
        pitchSpeed = s.has("pitchSpeed") ? s.get("pitchSpeed").getAsFloat() : 25f;
        smoothFactor = s.has("smoothFactor") ? s.get("smoothFactor").getAsFloat() : 0.18f;
        predictTicks = s.has("predictTicks") ? s.get("predictTicks").getAsFloat() : 2.7f;
        antiJitter = !s.has("antiJitter") || s.get("antiJitter").getAsBoolean();
    }

    @Override public String description() { return "Smooth aim assist without auto-attack."; }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("Aim", parent) {
            @Override protected void init() {
                int row = 0;

                row = choicesAt(java.util.List.of("FanTime", "Legit"),
                        i -> (aimMode.equals("FanTime") && i == 0) || (aimMode.equals("Legit") && i == 1),
                        i -> aimMode = (i == 0 ? "FanTime" : "Legit"), row);
                row += 4;

                toggleAt("Players", row, () -> targetPlayers, () -> targetPlayers = !targetPlayers); row += 22;
                toggleAt("Hostiles", row, () -> targetHostiles, () -> targetHostiles = !targetHostiles); row += 22;
                toggleAt("Animals", row, () -> targetAnimals, () -> targetAnimals = !targetAnimals); row += 22;
                toggleAt("Friends", row, () -> targetFriends, () -> targetFriends = !targetFriends); row += 22;
                toggleAt("No armor", row, () -> targetNoArmor, () -> targetNoArmor = !targetNoArmor); row += 22;

                slider("Range", " m", row, 20, 60, () -> Math.round(attackRange * 10), v -> attackRange = v / 10f); row += 24;
                slider("Yaw Speed", "°", row, 5, 60, () -> Math.round(yawSpeed), v -> yawSpeed = v); row += 24;
                slider("Pitch Speed", "°", row, 3, 40, () -> Math.round(pitchSpeed), v -> pitchSpeed = v); row += 24;
                slider("Smooth", "%", row, 10, 100, () -> Math.round(smoothFactor * 100), v -> smoothFactor = v / 100f); row += 24;
                slider("Predict", " t", row, 0, 30, () -> Math.round(predictTicks * 10), v -> predictTicks = v / 10f); row += 24;
                toggleAt("Anti-Jitter", row, () -> antiJitter, () -> antiJitter = !antiJitter); row += 22;
            }
        };
    }
}