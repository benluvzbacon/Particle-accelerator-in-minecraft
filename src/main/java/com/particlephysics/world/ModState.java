package com.particlephysics.world;

import java.util.List;

import com.particlephysics.accelerator.AcceleratorController;
import com.particlephysics.elements.Element;
import com.particlephysics.elements.Elements;
import com.particlephysics.elements.ItemElements;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The three persistent stores of the mod, plus the hooks that keep them up to date.
 *
 * <p>Everything the player earns lives here: the accelerator sites and their configuration, the
 * discovered elements, isotopes, quest progress and experiment log, and the radiation inventory of
 * the world. Data is loaded when the server starts, written on shutdown and every autosave
 * interval, and it survives world restarts.
 */
public final class ModState {
    private static final SiteState SITES = new SiteState();
    private static final ResearchState RESEARCH = new ResearchState();
    private static final RadiationState RADIATION = new RadiationState();
    private static MinecraftServer server;
    private static long lastSaveMillis;

    private ModState() {
    }

    public static SiteState sites() {
        return SITES;
    }

    public static ResearchState research() {
        return RESEARCH;
    }

    public static RadiationState radiation() {
        return RADIATION;
    }

    public static ResearchState.PlayerResearch research(PlayerEntity player) {
        return RESEARCH.forPlayer(player.getUuid());
    }

    public static void onServerStarted(MinecraftServer minecraftServer) {
        server = minecraftServer;
        SITES.load(minecraftServer);
        RESEARCH.load(minecraftServer);
        RADIATION.load(minecraftServer);
        RADIATION.setCompression(com.particlephysics.config.ModConfig.get().halfLifeCompression);
        com.particlephysics.ParticleAcceleratorMod.LOGGER.info(
                "Loaded {} accelerator site(s) and {} researched player(s)", SITES.count(),
                RESEARCH.allPlayers().size());
    }

    public static void onServerStopping(MinecraftServer minecraftServer) {
        save(minecraftServer, true);
        server = null;
    }

    /** Called once per second from the accelerator controller. */
    public static void tick(MinecraftServer minecraftServer, double seconds) {
        RADIATION.tick(seconds);
        RADIATION.setCompression(com.particlephysics.config.ModConfig.get().halfLifeCompression);
        decayDiscovery();
        long now = System.currentTimeMillis();
        long interval = Math.max(5, com.particlephysics.config.ModConfig.get().autosaveSeconds)
                * 1000L;
        if (now - lastSaveMillis > interval) {
            lastSaveMillis = now;
            save(minecraftServer, false);
        }
    }

    public static void saveAll() {
        if (server != null) {
            save(server, true);
        }
    }

    private static void save(MinecraftServer minecraftServer, boolean force) {
        if (force || SITES.isDirty()) {
            SITES.save(minecraftServer);
        }
        if (force || RESEARCH.isDirty()) {
            RESEARCH.save(minecraftServer);
        }
        if (force || RADIATION.isDirty()) {
            RADIATION.save(minecraftServer);
        }
    }

    /**
     * Radioactive material that decays into a new element teaches the player that element: this is
     * how nuclides far from the line of stability are discovered.
     */
    private static void decayDiscovery() {
        for (RadiationState.Source source : RADIATION.sources()) {
            if (source.type == null || source.elapsed < 1.0) {
                continue;
            }
            com.particlephysics.elements.Isotope isotope =
                    com.particlephysics.elements.Isotopes.find(source.z, source.a);
            if (isotope == null || isotope.daughter() == null) {
                continue;
            }
            com.particlephysics.elements.Isotope daughter = isotope.daughter();
            if (daughter.element() == null) {
                continue;
            }
            for (ResearchState.PlayerResearch research : RESEARCH.allPlayers()
                    .values()) {
                if (research.hasDiscovered(daughter.element().atomicNumber())) {
                    continue;
                }
                research.discoverIsotope(daughter, ResearchState.DiscoverySource.DECAY);
                research.unlock("decay_chain");
            }
        }
    }

    /** Mining a material teaches the player which elements it contains. */
    public static void onBlockBroken(World world, PlayerEntity player, BlockPos pos,
                                     BlockState state, BlockEntity entity) {
        if (world.isClient || player == null) {
            return;
        }
        ResearchState.PlayerResearch research = research(player);
        List<Integer> composition = ItemElements.compositionOf(state.getBlock());
        for (int z : composition) {
            Element element = Elements.byZ(z);
            if (element != null) {
                research.discover(element, ResearchState.DiscoverySource.MINED);
            }
        }
        RADIATION.removeSourcesNear(pos, 0.9);
        // breaking a machine invalidates the network that owned it
        if (AcceleratorController.isMachineAt(world, pos)) {
            AcceleratorController.onStructureChanged(world, pos);
        }
        if (player instanceof ServerPlayerEntity) {
            RESEARCH.markDirty();
        }
    }
}
