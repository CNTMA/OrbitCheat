package com.orbitdlc.client;

import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class ThirdPerson extends Module {
    public static final ThirdPerson INSTANCE = new ThirdPerson();

    public float freeYaw = 0f;
    public float freePitch = 0f;
    public float savedBodyYaw = 0f;
    public float savedPitch = 0f;

    private ThirdPerson() { super("third_person", Category.VISUALS); }

    @Override
    protected void onEnable() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Запоминаем углы тела
        savedBodyYaw = mc.player.getYRot();
        savedPitch = mc.player.getXRot();

        // Камера начинается с того же места, куда смотрит тело
        freeYaw = savedBodyYaw;
        freePitch = savedPitch;

        // Включаем вид от третьего лица
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

    }

    @Override
    protected void onDisable() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Возвращаем камеру в исходное положение
            mc.player.setYRot(freeYaw);
            mc.player.setXRot(freePitch);
        }
        // Возвращаем вид от первого лица
        mc.options.setCameraType(CameraType.FIRST_PERSON);


    }

    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null) return;

        // Авто-отключение ТОЛЬКО при открытии игрового инвентаря/чата (НЕ ClickGUI)
        if (mc.gui.screen() != null
                && !(mc.gui.screen() instanceof ClickGui)
                && !(mc.gui.screen() instanceof ModuleSettings)) {
            setEnabled(false);
        }
    }

    public void addMouseLook(float deltaYaw, float deltaPitch) {
        freeYaw += deltaYaw;
        freePitch = Mth.clamp(freePitch + deltaPitch, -89.0f, 89.0f);
    }

    @Override
    public String description() { return "Allows you to look around in third person without changing your movement direction."; }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("ThirdPerson", parent) {
            @Override protected void init() {
                // Настроек больше нет, всё работает по включению
            }
        };
    }
}