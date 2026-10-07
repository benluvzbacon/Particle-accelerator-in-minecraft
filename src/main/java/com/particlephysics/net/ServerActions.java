package com.particlephysics.net;

import com.particlephysics.accelerator.AcceleratorController;
import com.particlephysics.accelerator.BlueprintActions;
import com.particlephysics.quest.QuestActions;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Server side handler for every message the client sends. Everything is validated here: the player
 * must be in range of the block, the block must exist and must be the right kind, and values are
 * clamped by the machine logic. The client never decides anything by itself.
 */
public final class ServerActions {
    private ServerActions() {
    }

    public static void handle(ServerPlayerEntity player, NbtCompound data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        String action = data.getString("action");
        switch (action) {
            case "open" -> open(player, data);
            case "machine" -> machine(player, data);
            case "computer" -> computer(player, data);
            case "detector" -> detector(player, data);
            case "blueprint" -> BlueprintActions.handle(player, data);
            case "quests" -> QuestActions.handle(player, data);
            case "geiger" -> AcceleratorController.sendGeigerReading(player);
            default -> {
                // unknown action: ignore
            }
        }
    }

    private static void open(ServerPlayerEntity player, NbtCompound data) {
        String screen = data.getString("screen");
        switch (screen) {
            case "blueprint" -> BlueprintActions.sendBlueprintScreen(player);
            case "quests" -> QuestActions.sendQuestScreen(player);
            case "geiger" -> AcceleratorController.sendGeigerReading(player);
            case "machine", "computer", "detector" -> {
                BlockPos pos = readPos(data);
                if (pos != null && withinReach(player, pos)) {
                    AcceleratorController.sendMachineScreen(player, pos);
                }
            }
            default -> {
                // unknown screen: ignore
            }
        }
    }

    private static void machine(ServerPlayerEntity player, NbtCompound data) {
        BlockPos pos = readPos(data);
        if (pos != null && withinReach(player, pos)) {
            AcceleratorController.handleMachineAction(player, pos, data);
        }
    }

    private static void computer(ServerPlayerEntity player, NbtCompound data) {
        BlockPos pos = readPos(data);
        if (pos != null && withinReach(player, pos)) {
            AcceleratorController.handleComputerAction(player, pos, data);
        }
    }

    private static void detector(ServerPlayerEntity player, NbtCompound data) {
        BlockPos pos = readPos(data);
        if (pos != null && withinReach(player, pos)) {
            AcceleratorController.handleDetectorAction(player, pos, data);
        }
    }

    private static BlockPos readPos(NbtCompound data) {
        if (!data.contains("x") || !data.contains("y") || !data.contains("z")) {
            return null;
        }
        return new BlockPos(data.getInt("x"), data.getInt("y"), data.getInt("z"));
    }

    /** A player can only operate a machine within reach, as if they were standing next to it. */
    private static boolean withinReach(ServerPlayerEntity player, BlockPos pos) {
        double distance = player.getPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 0.5);
        return distance <= 400.0;
    }
}
