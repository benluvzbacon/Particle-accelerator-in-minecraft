package com.particlephysics.accelerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.particlephysics.ParticleAcceleratorMod;
import com.particlephysics.block.MachineBlock;
import com.particlephysics.blockentity.AbstractMachineBlockEntity;
import com.particlephysics.config.ModConfig;
import com.particlephysics.net.ModNetworking;
import com.particlephysics.physics.Beam;
import com.particlephysics.physics.BeamSimulator;
import com.particlephysics.physics.Lattice;
import com.particlephysics.physics.ParticleSpecies;
import com.particlephysics.physics.Relativity;
import com.particlephysics.physics.Vacuum;
import com.particlephysics.world.ModState;
import com.particlephysics.world.SiteState;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The control room.
 *
 * <p>The controller owns one {@link AcceleratorNetwork} per accelerator site, steps them, answers
 * every request from the screens, sends the meter readings back to the players and keeps the world
 * data in sync. All of the interesting numbers (beam energy, current, vacuum, fields, RF, dose
 * rate) are produced here and nowhere else, so what the control computer shows is exactly what the
 * simulation computed.
 */
public final class AcceleratorController {
    private static final Map<UUID, AcceleratorNetwork> NETWORKS = new HashMap<>();
    private static final Map<UUID, List<double[]>> HISTORIES = new HashMap<>();
    private static boolean loaded;
    private static double accumulator;

    private AcceleratorController() {
    }

    // ------------------------------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------------------------------

    public static void onServerTick(MinecraftServer server) {
        if (!loaded) {
            loaded = true;
            loadNetworks(server);
        }
        ModState.tick(server, 1.0 / 20.0);

        double dt = 1.0 / Math.max(1, ModConfig.get().simulationTicksPerSecond);
        accumulator += 1.0 / 20.0;
        if (accumulator < dt) {
            return;
        }
        accumulator = 0.0;

        // --- radiation exposure of the players ------------------------------------------------
        int second = server.getTicks() % 20;
        if (second == 0) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                com.particlephysics.radiation.RadiationEffects.tickPlayer(player);
            }
        }

        for (Map.Entry<UUID, AcceleratorNetwork> entry : new ArrayList<>(NETWORKS.entrySet())) {
            AcceleratorNetwork network = entry.getValue();
            ServerWorld world = worldOf(server, network.site.world);
            if (world == null) {
                continue;
            }
            try {
                List<ServerPlayerEntity> players = playersNear(world, network.site.origin(), 128.0);
                network.tick(world, dt, players);
                recordHistory(network);
            } catch (Exception e) {
                ParticleAcceleratorMod.LOGGER.error("Accelerator {} failed to tick: {}",
                        network.site.name, e.toString());
                network.lastFailure = "Simulation error, see the log";
            }
        }
    }

    private static void loadNetworks(MinecraftServer server) {
        NETWORKS.clear();
        for (SiteState.Site site : ModState.sites().sites()) {
            NETWORKS.put(site.id, new AcceleratorNetwork(site));
        }
        for (Map.Entry<UUID, AcceleratorNetwork> entry : new ArrayList<>(NETWORKS.entrySet())) {
            ServerWorld world = worldOf(server, entry.getValue().site.world);
            if (world != null) {
                entry.getValue().refresh(world);
            }
        }
    }

    private static ServerWorld worldOf(MinecraftServer server, String worldId) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(worldId)) {
                return world;
            }
        }
        return server.getOverworld();
    }

    private static List<ServerPlayerEntity> playersNear(ServerWorld world, BlockPos pos,
                                                        double radius) {
        List<ServerPlayerEntity> list = new ArrayList<>();
        double squared = radius * radius;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    <= squared) {
                list.add(player);
            }
        }
        return list;
    }

    private static void recordHistory(AcceleratorNetwork network) {
        List<double[]> history = HISTORIES.computeIfAbsent(network.site.id,
                id -> new ArrayList<>());
        history.add(new double[]{network.beam.energy, network.collisionRateHz,
                network.vacuumPressurePa});
        while (history.size() > 180) {
            history.remove(0);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Network lookup
    // ------------------------------------------------------------------------------------------

    public static AcceleratorNetwork networkOf(SiteState.Site site) {
        return NETWORKS.computeIfAbsent(site.id, id -> new AcceleratorNetwork(site));
    }

    public static AcceleratorNetwork networkAt(ServerWorld world, BlockPos pos) {
        SiteState.Site site = ModState.sites().at(pos, world.getRegistryKey().getValue().toString());
        return site == null ? null : networkOf(site);
    }

    public static Map<UUID, AcceleratorNetwork> networks() {
        return NETWORKS;
    }

    public static boolean isMachineAt(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof MachineBlock;
    }

    /** Called when a machine block is broken: the network is re-validated immediately. */
    public static void onStructureChanged(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        AcceleratorNetwork network = networkAt(serverWorld, pos);
        if (network != null) {
            network.refresh(serverWorld);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Building sites
    // ------------------------------------------------------------------------------------------

    public static SiteState.Site createSite(ServerWorld world, AcceleratorDesign.Kind kind,
                                            BlockPos origin, int size, int cells,
                                            net.minecraft.util.math.Direction rotation) {
        SiteState.Site site = ModState.sites().create(kind, origin, size, cells, rotation,
                world.getTime(), world.getRegistryKey().getValue().toString());
        AcceleratorNetwork network = new AcceleratorNetwork(site);
        NETWORKS.put(site.id, network);
        network.refresh(world);
        network.log("Blueprint deployed: " + kind.displayName() + ", radius " + size
                + ", " + cells + " cells");
        return site;
    }

    public static boolean removeSite(SiteState.Site site) {
        NETWORKS.remove(site.id);
        HISTORIES.remove(site.id);
        return ModState.sites().remove(site);
    }

    // ------------------------------------------------------------------------------------------
    // Screen payloads
    // ------------------------------------------------------------------------------------------

    /** State of a single machine block, used by its own panel. */
    public static NbtCompound machineStateNbt(BlockEntity be) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", "machine");
        nbt.putInt("x", be.getPos().getX());
        nbt.putInt("y", be.getPos().getY());
        nbt.putInt("z", be.getPos().getZ());
        if (!(be instanceof AbstractMachineBlockEntity machine)) {
            return nbt;
        }
        nbt.putString("kind", machine.kind() == null ? "" : machine.kind().id());
        nbt.putString("title", machine.kind() == null ? "Machine" : machine.kind().displayName());
        nbt.putBoolean("active", machine.isActive());
        nbt.putDouble("level", machine.level());
        nbt.putDouble("measured", machine.measuredValue());
        nbt.putDouble("setpointA", machine.setpointA());
        nbt.putDouble("setpointB", machine.setpointB());
        nbt.putDouble("temperature", machine.temperatureC());
        if (be.getWorld() instanceof ServerWorld world) {
            AcceleratorNetwork network = networkAt(world, be.getPos());
            if (network != null) {
                nbt.putBoolean("inMachine", true);
                nbt.putBoolean("running", network.machine.running);
                nbt.putString("site", network.site.name);
                nbt.putDouble("vacuum", network.vacuumPressurePa);
                nbt.putDouble("field", network.machine.dipoleField);
                nbt.putDouble("rfv", network.machine.rfVoltageMV);
                nbt.putDouble("rff", network.machine.rfFrequencyMHz);
                nbt.putDouble("rfRequired", network.lattice.revolutionFrequency(network.beam.beta())
                        * Math.max(1, network.lattice.harmonicNumber(network.beam.beta())) / 1.0e6);
                nbt.putDouble("energy", network.beam.energy);
                nbt.putDouble("current", network.beam.current);
                nbt.putDouble("dose", network.radiationPromptSvH);
                nbt.putString("failure", network.lastFailure);
                nbt.putString("species", network.beam.species.name());
                NbtList items = new NbtList();
                for (int i = 0; i < machine.size(); i++) {
                    ItemStack stack = machine.getStack(i);
                    if (!stack.isEmpty()) {
                        NbtCompound entry = new NbtCompound();
                        entry.putString("name", stack.getName().getString());
                        entry.putInt("count", stack.getCount());
                        items.add(entry);
                    }
                }
                nbt.put("items", items);
            }
        }
        return nbt;
    }

    /** Full control room readout. */
    public static NbtCompound computerStateNbt(AcceleratorNetwork network, String action) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", "computer");
        nbt.putString("site", network.site.name);
        nbt.putString("action", action == null ? "" : action);
        nbt.putInt("x", network.site.originX);
        nbt.putInt("y", network.site.originY);
        nbt.putInt("z", network.site.originZ);
        nbt.putBoolean("ring", network.site.kind == AcceleratorDesign.Kind.RING);
        nbt.putInt("size", network.site.size);
        nbt.putInt("cells", network.site.cells);

        // beam
        nbt.putString("species", network.beam.species.name());
        nbt.putString("speciesSymbol", network.beam.species.symbol());
        nbt.putDouble("energy", network.beam.energy);
        nbt.putDouble("targetEnergy", network.site.targetEnergyMeV);
        nbt.putDouble("current", network.beam.current);
        nbt.putDouble("intensity", network.beam.intensity);
        nbt.putDouble("beta", network.beam.beta());
        nbt.putDouble("gamma", network.beam.gamma());
        nbt.putDouble("momentum", network.beam.momentum());
        nbt.putDouble("lifetime", Double.isInfinite(network.beam.lifetime) ? -1.0
                : network.beam.lifetime);
        nbt.putDouble("lossFraction", network.beam.lossFraction);
        nbt.putBoolean("beamLost", network.beam.lost);
        nbt.putString("lossReason", network.beam.lossReason);
        nbt.putDouble("peakEnergy", network.peakEnergyMeV);

        // magnets
        nbt.putDouble("field", network.machine.dipoleField);
        nbt.putDouble("fieldSetpoint", network.site.dipoleField);
        nbt.putDouble("fieldRequired", network.lattice.bendingRadius() > 0.05
                ? network.beam.momentum() / 1000.0 / 0.299792458
                        / network.lattice.bendingRadius() : 0.0);
        nbt.putDouble("quad", network.site.quadrupoleScale);
        nbt.putDouble("sext", network.site.sextupoleScale);
        nbt.putDouble("steering", network.site.steeringTrim);
        nbt.putDouble("orbit", network.machine.orbitDistortionMm);
        nbt.putBoolean("quench", network.machine.magnetQuench);
        nbt.putBoolean("superconducting", network.machine.superconducting);
        nbt.putDouble("dipoleCount", network.lattice.countElements(
                com.particlephysics.physics.LatticeElement.Kind.DIPOLE));
        nbt.putDouble("quadrupoleCount", network.lattice.countElements(
                com.particlephysics.physics.LatticeElement.Kind.QUADRUPOLE));

        // RF
        nbt.putDouble("rfv", network.machine.rfVoltageMV);
        nbt.putDouble("rff", network.machine.rfFrequencyMHz);
        nbt.putDouble("rfPhase", network.site.rfPhaseDegrees);
        nbt.putDouble("rfRequired", network.lattice.revolutionFrequency(network.beam.beta())
                * Math.max(1, network.lattice.harmonicNumber(network.beam.beta())) / 1.0e6);
        nbt.putBoolean("rfFault", network.machine.rfFault);
        // voltage the cavities must deliver to make up the radiation loss per turn
        double storedEnergy = network.beam.energy;
        double lossPerTurn = BeamSimulator.synchrotronLossPerTurn(network.beam, network.lattice);
        nbt.putDouble("rfvRequired", Math.max(0.05, lossPerTurn * 1.25));
        network.beam.energy = storedEnergy;

        // vacuum
        nbt.putDouble("vacuum", network.vacuumPressurePa);
        nbt.putDouble("pumpSpeed", network.pumpSpeedM3S);
        nbt.putBoolean("valveOpen", network.site.valveOpen);
        nbt.putBoolean("vacuumFault", network.machine.vacuumFault);
        double gasSigma = Vacuum.gasCrossSection(network.beam.energy,
                network.beam.species.mass(), Math.abs(network.beam.species.charge()),
                7.0, 14.0);
        nbt.putDouble("gasLifetime", Vacuum.beamLifetime(network.vacuumPressurePa, gasSigma,
                network.beam.beta()));

        // power, cooling, cryogenics
        nbt.putDouble("powerSupply", network.powerSupplyKW);
        nbt.putDouble("powerDemand", network.powerDemandKW);
        nbt.putDouble("temperature", network.coilTemperatureC);
        nbt.putDouble("coolant", network.coolantAvailableKW);
        nbt.putDouble("cryoTemperature", network.cryoTemperatureK);
        nbt.putDouble("helium", network.heliumCharge);
        nbt.putBoolean("coolingFault", network.machine.coolingFault);
        nbt.putBoolean("cryoReady", network.machine.cryoReady);
        nbt.putDouble("cryoLoad", network.cryoLoadKW);

        // collisions and radiation
        nbt.putDouble("collisionRate", network.collisionRateHz);
        nbt.putDouble("luminosity", network.luminosity);
        nbt.putLong("collisions", network.totalCollisions);
        nbt.putDouble("dose", network.radiationPromptSvH);
        nbt.putString("failure", network.lastFailure);
        nbt.putBoolean("running", network.machine.running);
        nbt.putBoolean("interlock", network.machine.interlockTripped);

        // components and construction
        nbt.putInt("components", network.components.size());
        nbt.putInt("detectors", network.detectors.size());
        int errors = 0;
        int ok = 0;
        for (AcceleratorNetwork.Component component : network.components) {
            if (!component.present || !component.correctKind || !component.correctOrientation) {
                errors++;
            } else {
                ok++;
            }
        }
        nbt.putInt("builtOk", ok);
        nbt.putInt("errors", errors);
        NbtList issues = new NbtList();
        for (AcceleratorNetwork.Issue issue : network.issues) {
            NbtCompound entry = new NbtCompound();
            entry.putString("text", issue.message());
            entry.putInt("x", issue.pos().getX());
            entry.putInt("y", issue.pos().getY());
            entry.putInt("z", issue.pos().getZ());
            entry.putBoolean("error", issue.error());
            issues.add(entry);
            if (issues.size() >= 64) {
                break;
            }
        }
        nbt.put("issues", issues);

        NbtList log = new NbtList();
        for (String line : network.log) {
            NbtCompound entry = new NbtCompound();
            entry.putString("text", line);
            log.add(entry);
        }
        nbt.put("log", log);

        // history for the strip charts
        List<double[]> history = HISTORIES.get(network.site.id);
        if (history != null && !history.isEmpty()) {
            double[] energy = new double[history.size()];
            double[] rate = new double[history.size()];
            double[] vacuum = new double[history.size()];
            for (int i = 0; i < history.size(); i++) {
                double[] sample = history.get(i);
                energy[i] = sample[0];
                rate[i] = sample[1];
                vacuum[i] = sample[2];
            }
            nbt.putLongArray("historyEnergy", bits(energy));
            nbt.putLongArray("historyRate", bits(rate));
            nbt.putLongArray("historyVacuum", bits(vacuum));
        }
        return nbt;
    }

    /** Detector panel: everything a particle physicist wants to see. */
    public static NbtCompound detectorStateNbt(AcceleratorNetwork network,
                                               AcceleratorNetwork.DetectorReading reading) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", "detector");
        nbt.putString("title", reading.label());
        nbt.putString("site", network.site.name);
        nbt.putString("particle", network.beam.species.displayName());
        nbt.putString("symbol", network.beam.species.symbol());
        nbt.putDouble("energy", network.beam.energy);
        nbt.putDouble("momentum", network.beam.momentum());
        nbt.putDouble("mass", network.beam.species.mass());
        nbt.putDouble("charge", network.beam.species.charge());
        nbt.putDouble("velocity", network.beam.beta());
        nbt.putDouble("lifetime", Double.isInfinite(network.beam.species.lifetime()) ? -1.0
                : network.beam.species.lifetime() * network.beam.gamma());
        nbt.putDouble("rate", reading.rateHz);
        nbt.putDouble("lastEnergy", reading.lastEnergyGeV);
        nbt.putDouble("integrated", reading.integratedEnergyGeV);
        nbt.putDouble("multiplicity", reading.multiplicity);
        nbt.putLong("events", reading.events);
        nbt.putLong("charged", reading.chargedTracks);
        nbt.putLong("muons", reading.muons);
        nbt.putString("lastEvent", reading.lastEvent);
        nbt.putInt("x", reading.pos.getX());
        nbt.putInt("y", reading.pos.getY());
        nbt.putInt("z", reading.pos.getZ());
        return nbt;
    }

    // ------------------------------------------------------------------------------------------
    // Requests from the client
    // ------------------------------------------------------------------------------------------

    public static void sendMachineScreen(ServerPlayerEntity player, BlockPos pos) {
        BlockEntity be = player.getWorld().getBlockEntity(pos);
        if (be == null) {
            return;
        }
        ModNetworking.send(player, machineStateNbt(be));
    }

    public static void sendGeigerReading(ServerPlayerEntity player) {
        double microSv = ModState.radiation().doseRateMicroSvPerHour(player.getWorld(),
                player.getPos()) * ModConfig.get().radiationScale;
        String text = String.format(Locale.ROOT, "Dose rate: %.3f uSv/h  (%s)", microSv,
                microSv < 0.5 ? "background" : microSv < 5.0 ? "elevated"
                        : microSv < 50.0 ? "dangerous - lead shielding advised"
                                : "lethal - leave the area");
        ModNetworking.sendMessage(player, text, true);
        if (microSv > 50.0 && ModConfig.get().radiationDamageEnabled) {
            com.particlephysics.radiation.RadiationEffects.applyAcute(player,
                    Math.min(30.0, microSv / 20.0));
        }
    }

    public static void handleMachineAction(ServerPlayerEntity player, BlockPos pos, NbtCompound d) {
        BlockEntity be = player.getWorld().getBlockEntity(pos);
        if (!(be instanceof AbstractMachineBlockEntity machine)
                || !(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        AcceleratorNetwork network = networkAt(world, pos);
        String action = d.getString("act");
        switch (action) {
            case "rfv" -> {
                machine.setSetpointA(d.getDouble("value"));
                if (network != null) {
                    network.site.rfVoltageMV = d.getDouble("value");
                }
            }
            case "rfp" -> {
                machine.setSetpointB(d.getDouble("value"));
                if (network != null) {
                    network.site.rfPhaseDegrees = d.getDouble("value");
                }
            }
            case "steer" -> {
                machine.setSetpointA(d.getDouble("value"));
                if (network != null) {
                    network.site.steeringTrim = d.getDouble("value");
                }
            }
            case "valve" -> {
                boolean open = !d.contains("value") || d.getBoolean("value");
                net.minecraft.block.BlockState state = world.getBlockState(pos);
                if (state.contains(com.particlephysics.block.ValveBlock.OPEN)) {
                    world.setBlockState(pos, state.with(com.particlephysics.block.ValveBlock.OPEN,
                            open), 3);
                }
                if (network != null) {
                    network.site.valveOpen = open;
                    network.log(open ? "Valve opened" : "Valve closed");
                }
            }
            case "mode" -> machine.setSetpointB(d.getDouble("value"));
            default -> {
                // unknown action, ignored
            }
        }
        ModState.sites().markDirty();
        ModNetworking.send(player, machineStateNbt(be));
    }

    public static void handleComputerAction(ServerPlayerEntity player, BlockPos pos, NbtCompound d) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        AcceleratorNetwork network = networkAt(world, pos);
        if (network == null) {
            ModNetworking.sendMessage(player, "No accelerator at this console", true);
            return;
        }
        String action = d.getString("act");
        SiteState.Site site = network.site;
        switch (action) {
            case "run" -> {
                if (!network.machine.running) {
                    if (network.issues.stream().anyMatch(AcceleratorNetwork.Issue::error)) {
                        ModNetworking.sendMessage(player,
                                "Construction incomplete: fix the reported errors first", true);
                        break;
                    }
                    if (network.machine.interlockTripped) {
                        ModNetworking.sendMessage(player,
                                "Interlock active: " + network.lastFailure, true);
                        break;
                    }
                    network.machine.running = true;
                    site.running = true;
                    network.log("Start: magnets ramping to "
                            + String.format(Locale.ROOT, "%.2f T", site.dipoleField));
                } else {
                    network.machine.running = false;
                    site.running = false;
                    network.log("Stop: magnets ramping down");
                }
            }
            case "inject" -> {
                ParticleSpecies species = ParticleSpecies.byName(site.sourceSpecies);
                if (species == null) {
                    species = ParticleSpecies.PROTON;
                }
                network.beam.species = species;
                network.beam.clear();
                network.beam.energy = Math.max(0.1, network.beam.energy);
                BeamSimulator.populate(network.beam, network.lattice,
                        ModConfig.get().simulationQuality(), new java.util.Random(),
                        site.injectionIntensity);
                network.beam.intensity = site.injectionIntensity;
                network.log("Injection: " + species.displayName() + " at "
                        + String.format(Locale.ROOT, "%.2f MeV", network.beam.energy));
            }
            case "estop" -> {
                site.running = false;
                site.dipoleField = 0.0;
                network.machine.running = false;
                network.log("EMERGENCY STOP");
            }
            case "field" -> {
                site.dipoleField = clamp(d.getDouble("value"), 0.0, 12.0);
                network.machine.dipoleFieldSetpoint = site.dipoleField;
            }
            case "quad" -> site.quadrupoleScale = clamp(d.getDouble("value"), 0.0, 2.0);
            case "sext" -> site.sextupoleScale = clamp(d.getDouble("value"), 0.0, 2.0);
            case "steer" -> site.steeringTrim = clamp(d.getDouble("value"), -5.0, 5.0);
            case "rfv" -> site.rfVoltageMV = clamp(d.getDouble("value"), 0.0, 200.0);
            case "rff" -> site.rfFrequencyMHz = clamp(d.getDouble("value"), 0.01, 1000.0);
            case "rfp" -> site.rfPhaseDegrees = clamp(d.getDouble("value"), -360.0, 360.0);
            case "energy" -> applyTargetEnergy(network, clamp(d.getDouble("value"), 0.1,
                    1.0e8));
            case "valve" -> site.valveOpen = !d.contains("value") || d.getBoolean("value");
            case "super" -> {
                site.superconducting = d.getBoolean("value");
                network.log(site.superconducting ? "Superconducting magnets requested"
                        : "Resistive magnet mode");
            }
            case "species" -> {
                int index = (int) clamp(d.getDouble("value"), 0.0, 64.0);
                List<ParticleSpecies> injectable = ParticleSpecies.injectable();
                if (index >= 0 && index < injectable.size()) {
                    site.sourceSpecies = injectable.get(index).name();
                    network.log("Source set to " + injectable.get(index).displayName());
                }
            }
            case "bunches" -> site.bunches = (int) clamp(d.getDouble("value"), 1.0, 16.0);
            case "intensity" -> site.injectionIntensity =
                    10.0 * Math.pow(10.0, clamp(d.getDouble("value"), 4.0, 12.0) - 1.0);
            case "detectors" -> {
                if (network.detectors.isEmpty()) {
                    ModNetworking.sendMessage(player,
                            "No detector is built into this machine yet", true);
                } else {
                    AcceleratorNetwork.DetectorReading reading = network.detectors.get(0);
                    ModNetworking.send(player, detectorStateNbt(network, reading));
                    ModState.sites().markDirty();
                    return;
                }
            }
            default -> {
                // unknown action, ignored
            }
        }
        ModState.sites().markDirty();
        ModNetworking.send(player, computerStateNbt(network, action));
    }

    /** Sets the dipole field and RF voltage that correspond to a beam energy. */
    public static void applyTargetEnergy(AcceleratorNetwork network, double energyMeV) {
        network.site.targetEnergyMeV = energyMeV;
        ParticleSpecies species = network.beam.species;
        double momentum = Relativity.momentumFromKinetic(energyMeV, species.mass());
        double rho = network.lattice.bendingRadius();
        network.machine.setTargetFromMomentum(momentum, species.charge(), rho);
        network.site.dipoleField = network.machine.dipoleFieldSetpoint;

        // the cavities must make up the synchrotron radiation loss per turn, plus a margin for
        // the energy ramp itself
        double previousEnergy = network.beam.energy;
        network.beam.energy = energyMeV;
        double lossPerTurnMeV = BeamSimulator.synchrotronLossPerTurn(network.beam, network.lattice);
        network.beam.energy = previousEnergy;
        double requiredVoltage = lossPerTurnMeV * 1.25 + network.beam.energy * 1.0e-4;
        network.site.rfVoltageMV = Math.max(0.05, requiredVoltage);
        network.log(String.format(Locale.ROOT,
                "Target: %.3g MeV  (B = %.3f T, RF = %.3f MV at %.4f MHz)", energyMeV,
                network.site.dipoleField, network.site.rfVoltageMV,
                network.lattice.revolutionFrequency(network.beam.beta())
                        * Math.max(1, network.lattice.harmonicNumber(network.beam.beta()))
                        / 1.0e6));
    }

    public static void handleDetectorAction(ServerPlayerEntity player, BlockPos pos, NbtCompound d) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        AcceleratorNetwork network = networkAt(world, pos);
        if (network == null) {
            return;
        }
        AcceleratorNetwork.DetectorReading reading = null;
        for (AcceleratorNetwork.DetectorReading candidate : network.detectors) {
            if (candidate.pos.equals(pos)) {
                reading = candidate;
                break;
            }
        }
        if (reading == null) {
            return;
        }
        String action = d.getString("act");
        if ("reset".equals(action)) {
            reading.events = 0;
            reading.chargedTracks = 0;
            reading.muons = 0;
            reading.integratedEnergyGeV = 0.0;
            reading.lastEvent = "";
            network.log(reading.label() + " counters reset");
        }
        ModNetworking.send(player, detectorStateNbt(network, reading));
    }

    /** Adds a player to the research store and tells them about their new element. */
    public static void announceDiscovery(ServerPlayerEntity player, String message) {
        player.sendMessage(Text.literal(message), false);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static long[] bits(double[] values) {
        long[] out = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Double.doubleToRawLongBits(values[i]);
        }
        return out;
    }

    static double[] values(long[] bits) {
        double[] out = new double[bits.length];
        for (int i = 0; i < bits.length; i++) {
            out[i] = Double.longBitsToDouble(bits[i]);
        }
        return out;
    }

    /** All sites, for the command and the screens. */
    public static Map<String, String> summary() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Map.Entry<UUID, AcceleratorNetwork> entry : NETWORKS.entrySet()) {
            AcceleratorNetwork network = entry.getValue();
            map.put(network.site.name, String.format(Locale.ROOT,
                    "%s, R=%d, %d components, beam %.3g MeV, I=%.3g A, p=%.2e Pa, %.1f C",
                    network.site.kind.displayName(), network.site.size,
                    network.components.size(), network.beam.energy, network.beam.current,
                    network.vacuumPressurePa, network.coilTemperatureC));
        }
        return map;
    }
}
