package com.orbitdlc.client;

import com.google.gson.JsonObject;
import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import com.orbitdlc.client.util.AuraUtil;
import com.orbitdlc.client.util.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
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

    private String aimMode = "FanTime";
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
    private boolean antiGrim = true;
    private String targetPriority = "Crosshair";
    private String moveCorrection = "Follow"; // было "Focus"

    private LivingEntity target;
    private final float[] ticks = {-1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1, -1};
    private final float[] pitchSamples = new float[30];
    private int hitAge = 0;
    private int aimTicks = 0;
    private int originalSlot = -1;
    public double reach;
    // Зашитые идеальные настройки наводки
    // Зашитые идеальные настройки наводки
    private final float yawSpeed = 26f;
    private final float pitchSpeed = 25f;
    private final float smoothFactor = 0.18f; // <-- УМЕНЬШИЛИ ДЛЯ ПЛАВНОСТИ (было 0.59f)
    private final float predictTicks = 2.7f;
    private final boolean antiJitter = true; // 27 на слайдере = 2.7 тика
    // Плавные значения скоростей (нарастают от 0 до максимума)
    private float currentYawSpeed = 0f;
    private float currentPitchSpeed = 0f;

    private Aura() { super("aura", Category.COMBAT); }

    @Override protected void onEnable() {
        if (ticks[9] == -1f) ticks[9] = (int) random(9f, 13f);
        ticks[8] = 2f;
        ticks[10] = 0f;
        ticks[11] = 0f;
        var p = Minecraft.getInstance().player;
        Arrays.fill(pitchSamples, p != null ? p.getXRot() : 0f);
        target = null;
        hitAge = 0;
    }

    @Override protected void onDisable() {
        ticks[10] = 0f;
        target = null;
        restoreSlot();
        com.orbitdlc.client.util.MoveUtil.setFollowing(false); // <--- ДОБАВИТЬ ЭТО
    }

    /** Реальный лимит атрибута — сервер не засчитает удар дальше этого значения. */
    private double getRealReach() {
        var p = Minecraft.getInstance().player;
        if (p == null) return 3.0;
        var attr = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        return attr == null ? 3.0 : Math.min(attr.getValue(), 64.0);
    }

    private boolean pendingClick = false;

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            com.orbitdlc.client.util.MoveUtil.setFollowing(false);
            return;
        }

        if (target == null || !isValidTarget(target)) {
            LivingEntity prev = target;
            boolean fresh = prev == null || !isValidTarget(prev);
            target = (fresh && noWallCheck)
                    ? findTarget(false).or(() -> findTarget(true)).orElse(null)
                    : findTarget(true).orElse(null);
            if (target != prev && target != null) {
                ticks[10] = 0f;
                ticks[11] = 0f;
                Arrays.fill(pitchSamples, mc.player.getXRot());
            }
        }

        restoreSlot();

        if (target == null) {
            ticks[8] = 1f;
            com.orbitdlc.client.util.MoveUtil.setFollowing(false);

            // СБРОС ВСЕХ ПЛАВНЫХ ЗНАЧЕНИЙ
            currentYawSpeed = 0f;
            currentPitchSpeed = 0f;
            currentPredictTicks = 0f;

            hitAge++;
            return;
        }

        // ОДИН вызов updateRotation — он двигает камеру
        updateRotation(mc);

        // Атака (если можно)
        if (canAttackNow(mc)) {
            if (mc.player.isSprinting()) {
                mc.player.setSprinting(false);
            }
            ((com.orbitdlc.client.mixin.MinecraftAccessor) mc).orbitdlc$startAttack();
            hitAge = 0;
            ticks[5] = random(8f, 10f);
            ticks[9] = (int) random(9f, 13f);
            if (ticks[2] == -1f) ticks[4] = (int) random(30f, 35f);
            ticks[2] += 1f;
        }

        hitAge++;
    }


    private boolean canAttackNow(Minecraft mc) {
        var p = mc.player;
        if (p == null || target == null) return false;

        if (p.getAttackStrengthScale(0.5f) < 1.0f) return false;

        double reach = Math.min(attackRange, 3.0) - 0.05;
        if (!AuraUtil.withinReach(target, reach)) return false;

        if (noContainerOpen && mc.gui.screen() != null
                && !(mc.gui.screen() instanceof ClickGui)
                && !(mc.gui.screen() instanceof ModuleSettings)) return false;

        return true;
    }

    // Поле для плавного наращивания предсказания (1 -> 2 -> 3 ... 27)
    private float currentPredictTicks = 0f;

    private void updateRotation(Minecraft mc) {
        var p = mc.player;
        if (p == null || target == null) return;

        Vec3 velocity = target.getDeltaMovement();
        // Проверяем, движется ли цель по горизонтали
        boolean isMoving = (velocity.x * velocity.x + velocity.z * velocity.z > 0.0025);

        // Если цель бежит — цель предсказания 2.7 (27 в GUI). Если стоит — 0.
        float targetTick = isMoving ? predictTicks : 0f;

        // ПЛАВНОЕ НАРАЩИВАНИЕ (шаг 0.1, как ты просил: 1, 2, 3... 27)
        // 0.1f в коде = 1 в GUI.
        float step = 0.1f;
        if (currentPredictTicks < targetTick) {
            currentPredictTicks = Math.min(targetTick, currentPredictTicks + step);
        } else if (currentPredictTicks > targetTick) {
            currentPredictTicks = Math.max(targetTick, currentPredictTicks - step);
        }

        // 1. Предсказание ЦЕЛИ на основе плавного наращивания
        double predictX = velocity.x * currentPredictTicks;
        double predictZ = velocity.z * currentPredictTicks;
        double predictY = target.onGround() ? velocity.y * currentPredictTicks : 0;

        // 2. Предсказание ИГРОКА (чтобы не трясло при твоем прыжке)
        Vec3 playerVelocity = p.getDeltaMovement();
        Vec3 playerEye = p.getEyePosition().add(playerVelocity.scale(currentPredictTicks * 0.5));

        // 3. Точка прицеливания (центр + предсказание)
        Vec3 aimPos = target.getBoundingBox().getCenter().add(predictX, predictY, predictZ);
        Vec3 offset = aimPos.subtract(playerEye);

        float targetYaw = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(offset.z, offset.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(offset.y, Math.hypot(offset.x, offset.z)));

        // 4. Скорость поворота
        float currentPitchSpeed = pitchSpeed;
        if (!p.onGround()) currentPitchSpeed *= 1.3f;

        switch (aimMode) {
            case "FanTime", "FanTimeFOV" -> rotateFanTime(mc, targetYaw, targetPitch, currentPitchSpeed);
            case "Legit" -> rotateLegit(mc, targetYaw, targetPitch, currentPitchSpeed);
        }
        ticks[1] = (float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(
                target.getZ() - p.getZ(), target.getX() - p.getX())) - 90.0);
    }

    /** Дробная доля между тиками (0..1) — для плавного синуса. */
    private static float getPartialTick(Minecraft mc) {
        try {
            var tracker = mc.getClass().getMethod("getDeltaTracker").invoke(mc);
            return (float) tracker.getClass().getMethod("getGameTimeDeltaPartialTick", boolean.class)
                    .invoke(tracker, false);
        } catch (Exception ignored) {}
        try {
            var timer = mc.getClass().getMethod("getTimer").invoke(mc);
            return (float) timer.getClass().getMethod("getGameTimeDeltaPartialTick", boolean.class)
                    .invoke(timer, false);
        } catch (Exception ignored) {}
        return 0f;
    }

    /** FanTime — оригинальная логика: ±5° по yaw, ±2° по pitch. */
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
        // Дистанция поиска: атрибут + extraRange (но не больше attackRange + extraRange)
        double reach = Math.min(attackRange, getRealReach()) + extraRange;
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
                .filter(e -> AuraUtil.distanceSq(eye, e) <= reach * reach)
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
        if (e instanceof Monster || e instanceof Ghast || e instanceof EnderDragon
                || isKind(e, "Slime", "MagmaCube", "IronGolem", "SnowGolem"))
            return targetHostiles;
        if (e instanceof Animal || e instanceof AmbientCreature || e instanceof Allay)
            return targetAnimals;
        return false;
    }

    private static boolean isKind(LivingEntity e, String... names) {
        String sn = e.getClass().getSimpleName();
        for (String n : names) if (sn.equals(n)) return true;
        return false;
    }

    private static boolean isFriendSafe(String name) {
        for (String method : new String[]{"isFriend", "contains", "has", "isFriendByName"}) {
            try {
                var m = OrbitFriends.class.getMethod(method, String.class);
                return (boolean) m.invoke(null, name);
            } catch (Exception ignored) {}
        }
        return false;
    }

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
        s.addProperty("yawSpeed", yawSpeed);
        s.addProperty("pitchSpeed", pitchSpeed);
        s.addProperty("smoothFactor", smoothFactor);
        s.addProperty("predictTicks", predictTicks);
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
        moveCorrection = s.has("moveCorrection") ? s.get("moveCorrection").getAsString() : "Follow";
    }

    @Override public String description() { return "Automatically aims and attacks nearby targets."; }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("Aura", parent) {
            @Override protected void init() {
                int row = 0;

                // === 1. Режим наведения ===
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
                row += 4;

                // === 2. Режим движения ===
                row = choicesAt(java.util.List.of("Focus", "Free", "Follow"),
                        i -> switch (i) {
                            case 0 -> moveCorrection.equals("Focus");
                            case 1 -> moveCorrection.equals("Free");
                            default -> moveCorrection.equals("Follow");
                        },
                        i -> moveCorrection = switch (i) {
                            case 0 -> "Focus";
                            case 1 -> "Free";
                            default -> "Follow";
                        }, row);
                row += 4;

                // === 3. Приоритет цели ===
                row = choicesAt(java.util.List.of("Crosshair", "Distance", "HP"),
                        i -> switch (i) {
                            case 0 -> targetPriority.equals("Crosshair");
                            case 1 -> targetPriority.equals("Distance");
                            default -> targetPriority.equals("HP");
                        },
                        i -> targetPriority = switch (i) {
                            case 0 -> "Crosshair";
                            case 1 -> "Distance";
                            default -> "HP";
                        }, row);
                row += 4;

                // === 4. Цели ===
                toggleAt("No armor", row, () -> targetNoArmor, () -> targetNoArmor = !targetNoArmor); row += 22;
                toggleAt("Hostiles", row, () -> targetHostiles, () -> targetHostiles = !targetHostiles); row += 22;
                toggleAt("Animals", row, () -> targetAnimals, () -> targetAnimals = !targetAnimals); row += 22;
                toggleAt("Friends", row, () -> targetFriends, () -> targetFriends = !targetFriends); row += 22;
                toggleAt("Players", row, () -> targetPlayers, () -> targetPlayers = !targetPlayers); row += 22;

                // === 5. Дистанции ===
                slider("Attack range", " m", row, 1, 60,
                        () -> Math.round(attackRange * 10), v -> attackRange = v / 10f); row += 24;
                slider("Extra range", " m", row, 1, 30,
                        () -> Math.round(extraRange * 10), v -> extraRange = v / 10f); row += 24;

                // === 6. Флаги поведения ===
                toggleAt("Only crits", row, () -> onlyCrit, () -> onlyCrit = !onlyCrit); row += 22;
                toggleAt("Adaptive hit", row, () -> adaptiveHit, () -> adaptiveHit = !adaptiveHit); row += 22;
                toggleAt("No item in use", row, () -> noItemInUse, () -> noItemInUse = !noItemInUse); row += 22;
                toggleAt("No container", row, () -> noContainerOpen, () -> noContainerOpen = !noContainerOpen); row += 22;
                toggleAt("No wall check", row, () -> noWallCheck, () -> noWallCheck = !noWallCheck); row += 22;
                toggleAt("Shield break", row, () -> shieldBreak, () -> shieldBreak = !shieldBreak); row += 22;
                toggleAt("Smart sprint", row, () -> smartSprint, () -> smartSprint = !smartSprint); row += 22;
                toggleAt("Anti-Grim", row, () -> antiGrim, () -> antiGrim = !antiGrim); row += 22;
            }
        };
    }

    private static float random(float min, float max) {
        return (float) (Math.random() * (max - min) + min);
    }
}