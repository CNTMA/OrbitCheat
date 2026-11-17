package com.orbitdlc.client;

import com.orbitdlc.client.module.Category;
import com.orbitdlc.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Automatically selects the most effective tool from the hotbar
 * while the player is mining a block.
 */
public final class AutoTool extends Module {
    public static final AutoTool INSTANCE = new AutoTool();

    private int previousSlot = -1;

    private AutoTool() {
        super("autotool", Category.PLAYER);
    }

    /**
     * Call this from the same client tick/event loop that calls Aim.tick()
     * and Aura.tick().
     */
    void tick(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.level == null) {
            restoreSlot(mc);
            return;
        }

        if (!mc.options.keyAttack.isDown()
                || !(mc.hitResult instanceof BlockHitResult hit)) {
            restoreSlot(mc);
            return;
        }

        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        if (state.isAir()) {
            restoreSlot(mc);
            return;
        }

        Inventory inventory = mc.player.getInventory();

        // Remember the slot selected before AutoTool first changes it.
        if (previousSlot == -1) {
            previousSlot = inventory.getSelectedSlot();
        }

        int bestSlot = findBestSlot(inventory, state);
        if (bestSlot != inventory.getSelectedSlot()) {
            inventory.setSelectedSlot(bestSlot);
        }
    }

    private int findBestSlot(Inventory inventory, BlockState state) {
        int bestSlot = inventory.getSelectedSlot();
        ItemStack current = inventory.getItem(bestSlot);
        float bestSpeed = current.isEmpty() ? 1.0F : current.getDestroySpeed(state);

        // Hotbar = slots 0..8
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private void restoreSlot(Minecraft mc) {
        if (mc.player != null && previousSlot != -1) {
            mc.player.getInventory().setSelectedSlot(previousSlot);
        }
        previousSlot = -1;
    }

    @Override
    protected void onDisable() {
        restoreSlot(Minecraft.getInstance());
    }

    ModuleSettings settings(ClickGui parent) {
        return new ModuleSettings("AutoTool", parent) {
            @Override protected void init() {
                int row = 0;


            }
        };
    }

    @Override
    public String description() {
        return "Automatically selects the best tool when mining blocks.";
    }
}