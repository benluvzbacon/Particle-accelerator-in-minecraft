package com.particlephysics;

import com.particlephysics.accelerator.AcceleratorController;
import com.particlephysics.command.AcceleratorCommand;
import com.particlephysics.config.ModConfig;
import com.particlephysics.net.ModNetworking;
import com.particlephysics.registry.ModBlockEntities;
import com.particlephysics.registry.ModBlocks;
import com.particlephysics.registry.ModItems;
import com.particlephysics.registry.ModOreGeneration;
import com.particlephysics.world.ModState;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of the mod.
 *
 * <p>Everything that exists in the world is registered here: blocks, items, block entities, the
 * network protocol, the ore that makes lead obtainable in survival, the world data that stores
 * research, radiation and accelerator sites, the server ticks that advance the machines, and the
 * commands used by the control room.
 */
public class ParticleAcceleratorMod implements ModInitializer {
    public static final String MOD_ID = "particleaccelerator";
    public static final Logger LOGGER = LoggerFactory.getLogger("Particle Accelerator");

    @Override
    public void onInitialize() {
        ModConfig.get();

        ModBlocks.initialize();
        ModBlockEntities.register();
        ModItems.register();
        ModOreGeneration.register();

        ModNetworking.registerPayloads();
        ModNetworking.registerServerReceiver();
        AcceleratorController.register();

        ServerLifecycleEvents.SERVER_STARTED.register(ModState::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(ModState::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(AcceleratorController::onServerTick);
        PlayerBlockBreakEvents.AFTER.register(ModState::onBlockBroken);
        AcceleratorCommand.register();

        LOGGER.info("Particle Accelerator initialised: {} components, {} block entities",
                ModBlocks.machines().size(), ModBlockEntities.count());
    }
}
