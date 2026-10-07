package com.particlephysics.command;

import java.util.Locale;
import java.util.Map;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.particlephysics.accelerator.AcceleratorController;
import com.particlephysics.accelerator.AcceleratorDesign;
import com.particlephysics.accelerator.AcceleratorNetwork;
import com.particlephysics.config.ModConfig;
import com.particlephysics.elements.Element;
import com.particlephysics.elements.Elements;
import com.particlephysics.world.ModState;
import com.particlephysics.world.ResearchState;
import com.particlephysics.world.SiteState;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * The control room console: a command line for everything the GUI can do, which makes the mod
 * testable, scriptable and usable on a server.
 */
public final class AcceleratorCommand {
    private AcceleratorCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("accelerator")
                        .requires(source -> source.hasPermissionLevel(0))
                        .executes(context -> status(context.getSource()))
                        .then(CommandManager.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(CommandManager.literal("blueprint")
                                .executes(context -> giveBlueprint(context.getSource())))
                        .then(CommandManager.literal("deploy")
                                .then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(
                                                6.0, 120.0))
                                        .executes(context -> deploy(context.getSource(),
                                                (int) DoubleArgumentType.getDouble(context,
                                                        "radius")))))
                        .then(CommandManager.literal("start")
                                .executes(context -> startStop(context.getSource(), true)))
                        .then(CommandManager.literal("stop")
                                .executes(context -> startStop(context.getSource(), false)))
                        .then(CommandManager.literal("inject")
                                .executes(context -> inject(context.getSource())))
                        .then(CommandManager.literal("field")
                                .then(CommandManager.argument("tesla", DoubleArgumentType.doubleArg(
                                                0.0, 12.0))
                                        .executes(context -> set(context.getSource(), "field",
                                                DoubleArgumentType.getDouble(context, "tesla")))))
                        .then(CommandManager.literal("energy")
                                .then(CommandManager.argument("mev", DoubleArgumentType.doubleArg(
                                                0.01, 1.0e8))
                                        .executes(context -> setEnergy(context.getSource(),
                                                DoubleArgumentType.getDouble(context, "mev")))))
                        .then(CommandManager.literal("rf")
                                .then(CommandManager.argument("voltage", DoubleArgumentType.doubleArg(
                                                0.0, 200.0))
                                        .executes(context -> set(context.getSource(), "rfv",
                                                DoubleArgumentType.getDouble(context, "voltage")))))
                        .then(CommandManager.literal("species")
                                .then(CommandManager.argument("name",
                                                StringArgumentType.word())
                                        .executes(context -> species(context.getSource(),
                                                StringArgumentType.getString(context, "name")))))
                        .then(CommandManager.literal("elements")
                                .executes(context -> elements(context.getSource())))
                        .then(CommandManager.literal("config")
                                .executes(context -> config(context.getSource()))
                                .then(CommandManager.argument("key", StringArgumentType.word())
                                        .then(CommandManager.argument("value",
                                                        StringArgumentType.word())
                                                .executes(context -> setConfig(context.getSource(),
                                                        StringArgumentType.getString(context,
                                                                "key"),
                                                        StringArgumentType.getString(context,
                                                                "value"))))))
                        .then(CommandManager.literal("save")
                                .executes(context -> {
                                    ModState.saveAll();
                                    context.getSource().sendFeedback(
                                            () -> Text.literal("Particle Accelerator data saved"),
                                            true);
                                    return 1;
                                }))
                        .then(CommandManager.literal("radiation")
                                .executes(context -> radiation(context.getSource())))));
    }

    // ------------------------------------------------------------------------------------------

    private static ServerPlayerEntity player(ServerCommandSource source) {
        try {
            return source.getPlayerOrThrow();
        } catch (Exception e) {
            return null;
        }
    }

    private static AcceleratorNetwork network(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        SiteState.Site site = ModState.sites().at(player.getBlockPos(),
                world.getRegistryKey().getValue().toString());
        if (site != null) {
            return AcceleratorController.networkOf(site);
        }
        SiteState.Site best = null;
        double bestDistance = 10000.0 * 10000.0;
        for (SiteState.Site candidate : ModState.sites().sites()) {
            if (!candidate.world.equals(world.getRegistryKey().getValue().toString())) {
                continue;
            }
            double distance = player.getPos().squaredDistanceTo(candidate.originX + 0.5,
                    candidate.originY + 0.5, candidate.originZ + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best == null ? null : AcceleratorController.networkOf(best);
    }

    private static int status(ServerCommandSource source) {
        Map<String, String> summary = AcceleratorController.summary();
        if (summary.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No accelerator exists yet. Stand where the "
                    + "centre of the ring should be and run /accelerator deploy <radius>, or deploy "
                    + "a blueprint from your inventory."), false);
            return 0;
        }
        for (Map.Entry<String, String> entry : summary.entrySet()) {
            source.sendFeedback(() -> Text.literal(entry.getKey() + ": " + entry.getValue())
                    .formatted(Formatting.AQUA), false);
        }
        return 1;
    }

    private static int giveBlueprint(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrThrow();
        player.getInventory().insertStack(new net.minecraft.item.ItemStack(
                com.particlephysics.registry.ModItems.BLUEPRINT));
        source.sendFeedback(() -> Text.literal("Blueprint added to your inventory"), false);
        return 1;
    }

    private static int deploy(ServerCommandSource source, int radius) {
        ServerPlayerEntity player = player(source);
        if (player == null || !(player.getWorld() instanceof ServerWorld world)) {
            return 0;
        }
        BlockPos pos = player.getBlockPos();
        SiteState.Site site = AcceleratorController.createSite(world, AcceleratorDesign.Kind.RING,
                pos, radius, 16, player.getHorizontalFacing());
        source.sendFeedback(() -> Text.literal("Accelerator deployed: " + site.name + " radius "
                + radius + " at " + pos.toShortString()), false);
        return 1;
    }

    private static int startStop(ServerCommandSource source, boolean start) {
        AcceleratorNetwork network = network(source);
        if (network == null) {
            source.sendError(Text.literal("No accelerator nearby"));
            return 0;
        }
        network.site.running = start;
        network.machine.running = start;
        network.log(start ? "Start (command)" : "Stop (command)");
        source.sendFeedback(() -> Text.literal((start ? "Started " : "Stopped ")
                + network.site.name), false);
        return 1;
    }

    private static int inject(ServerCommandSource source) {
        AcceleratorNetwork network = network(source);
        if (network == null) {
            source.sendError(Text.literal("No accelerator nearby"));
            return 0;
        }
        network.beam.intensity = network.site.injectionIntensity;
        com.particlephysics.physics.BeamSimulator.populate(network.beam, network.lattice,
                ModConfig.get().simulationQuality(), new java.util.Random(),
                network.site.injectionIntensity);
        network.log("Injection (command)");
        source.sendFeedback(() -> Text.literal("Injected " + network.beam.species.displayName()),
                false);
        return 1;
    }

    private static int set(ServerCommandSource source, String key, double value) {
        AcceleratorNetwork network = network(source);
        if (network == null) {
            source.sendError(Text.literal("No accelerator nearby"));
            return 0;
        }
        switch (key) {
            case "field" -> network.site.dipoleField = value;
            case "rfv" -> network.site.rfVoltageMV = value;
            default -> {
                return 0;
            }
        }
        source.sendFeedback(() -> Text.literal(key + " set to " + value), false);
        return 1;
    }

    private static int setEnergy(ServerCommandSource source, double meV) {
        AcceleratorNetwork network = network(source);
        if (network == null) {
            source.sendError(Text.literal("No accelerator nearby"));
            return 0;
        }
        AcceleratorController.applyTargetEnergy(network, meV);
        source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "Target energy %.3g MeV: dipole %.3f T, RF %.3f MV", meV, network.site.dipoleField,
                network.site.rfVoltageMV)), false);
        return 1;
    }

    private static int species(ServerCommandSource source, String name) {
        AcceleratorNetwork network = network(source);
        if (network == null) {
            source.sendError(Text.literal("No accelerator nearby"));
            return 0;
        }
        com.particlephysics.physics.ParticleSpecies species =
                com.particlephysics.physics.ParticleSpecies.byName(name.toUpperCase(Locale.ROOT));
        if (species == null) {
            species = com.particlephysics.physics.ParticleSpecies.byName(name);
        }
        if (species == null || !species.isCharged()) {
            source.sendError(Text.literal("Unknown or uncharged species: " + name));
            return 0;
        }
        com.particlephysics.physics.ParticleSpecies chosen = species;
        network.site.sourceSpecies = chosen.name();
        network.beam.species = chosen;
        source.sendFeedback(() -> Text.literal("Source set to " + chosen.displayName()), false);
        return 1;
    }

    private static int elements(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        if (player == null) {
            return 0;
        }
        ResearchState.PlayerResearch research = ModState.research(player);
        source.sendFeedback(() -> Text.literal("Discovered elements: "
                + research.discoveredElementCount() + " / 118   nuclides: "
                + research.discoveredIsotopes.size()), false);
        StringBuilder builder = new StringBuilder();
        for (Element element : Elements.all()) {
            if (research.hasDiscovered(element.atomicNumber())) {
                builder.append(element.symbol()).append(' ');
            }
        }
        source.sendFeedback(() -> Text.literal(builder.toString().trim()).formatted(
                Formatting.GRAY), false);
        return 1;
    }

    private static int config(ServerCommandSource source) {
        for (Map.Entry<String, String> entry : ModConfig.get().values().entrySet()) {
            source.sendFeedback(() -> Text.literal(entry.getKey() + " = " + entry.getValue())
                    .formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    private static int setConfig(ServerCommandSource source, String key, String value) {
        boolean ok = ModConfig.get().set(key, value);
        if (!ok) {
            source.sendError(Text.literal("Unknown setting or value: " + key));
            return 0;
        }
        source.sendFeedback(() -> Text.literal(key + " = " + value + " (saved)"), false);
        return 1;
    }

    private static int radiation(ServerCommandSource source) {
        ServerPlayerEntity player = player(source);
        if (player == null) {
            return 0;
        }
        double microSv = ModState.radiation().doseRateMicroSvPerHour(player.getWorld(),
                player.getPos()) * ModConfig.get().radiationScale;
        source.sendFeedback(() -> Text.literal(String.format(Locale.ROOT,
                "Dose rate: %.4f uSv/h   sources in the world: %d", microSv,
                ModState.radiation().sources().size())), false);
        return 1;
    }
}
