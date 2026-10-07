package com.particlephysics.quest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.particlephysics.accelerator.AcceleratorController;
import com.particlephysics.accelerator.AcceleratorNetwork;
import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.elements.Element;
import com.particlephysics.elements.Elements;
import com.particlephysics.net.ModNetworking;
import com.particlephysics.world.ModState;
import com.particlephysics.world.ResearchState;
import com.particlephysics.world.SiteState;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Server side of the research journal: it evaluates every objective against the player's research
 * record and the machine in front of them, and reports what has just been completed.
 */
public final class QuestActions {
    private static final Map<UUID, Integer> CHAPTER = new HashMap<>();

    private QuestActions() {
    }

    public static void handle(ServerPlayerEntity player, NbtCompound data) {
        String act = data.getString("act");
        if ("chapter".equals(act)) {
            CHAPTER.put(player.getUuid(), data.getInt("value"));
        }
        sendQuestScreen(player);
    }

    public static void sendQuestScreen(ServerPlayerEntity player) {
        ResearchState.PlayerResearch research = ModState.research(player);
        AcceleratorNetwork network = nearestNetwork(player);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", "journal");
        NbtList chapters = new NbtList();
        int completedObjectives = 0;
        int totalObjectives = 0;
        int completedChapters = 0;
        for (QuestCatalog.Chapter chapter : QuestCatalog.chapters()) {
            NbtList objectives = new NbtList();
            int done = 0;
            for (QuestCatalog.Objective objective : chapter.objectives()) {
                double value = value(research, network, objective);
                boolean complete = value >= objective.target();
                if (complete) {
                    done++;
                }
                NbtCompound entry = new NbtCompound();
                entry.putString("text", objective.text());
                entry.putBoolean("done", complete);
                entry.putDouble("value", value);
                entry.putDouble("target", objective.target());
                entry.putString("unit", objective.unit());
                objectives.add(entry);
            }
            totalObjectives += chapter.objectives().size();
            completedObjectives += done;
            if (done == chapter.objectives().size()) {
                completedChapters++;
            }
            NbtCompound chapterNbt = new NbtCompound();
            chapterNbt.putString("id", chapter.id());
            chapterNbt.putString("title", chapter.title());
            chapterNbt.putString("description", chapter.description());
            chapterNbt.putDouble("progress", chapter.objectives().isEmpty() ? 0.0
                    : (double) done / chapter.objectives().size());
            chapterNbt.put("objectives", objectives);
            chapters.add(chapterNbt);
        }
        nbt.put("chapters", chapters);
        nbt.putInt("chapterCount", chapters.size());
        nbt.putInt("completedObjectives", completedObjectives);
        nbt.putInt("totalObjectives", totalObjectives);
        nbt.putInt("completedChapters", completedChapters);
        nbt.putInt("chapter", CHAPTER.getOrDefault(player.getUuid(), 0));

        // --- element collection ---------------------------------------------------------------
        NbtList elements = new NbtList();
        for (Element element : Elements.all()) {
            NbtCompound entry = new NbtCompound();
            entry.putInt("z", element.atomicNumber());
            entry.putString("symbol", element.symbol());
            entry.putString("name", element.name());
            entry.putString("category", element.category().displayName());
            entry.putBoolean("discovered", research.hasDiscovered(element.atomicNumber()));
            entry.putString("source", research.sourceOf(element.atomicNumber()).label());
            elements.add(entry);
        }
        nbt.put("elements", elements);
        nbt.putInt("discoveredElements", research.discoveredElementCount());
        nbt.putInt("discoveredIsotopes", research.discoveredIsotopes.size());
        nbt.putInt("collisions", (int) Math.min(Integer.MAX_VALUE, research.collisionCount));
        nbt.putDouble("dose", research.accumulatedDose);
        nbt.putString("lastEvent", research.experiments.isEmpty() ? "No experiments recorded yet"
                : "Last experiment: " + research.experiments.get(0).type + "  sqrt(s)="
                        + String.format(java.util.Locale.ROOT, "%.2f GeV",
                                research.experiments.get(0).sqrtSGeV));
        ModNetworking.send(player, nbt);
    }

    /** Evaluates one objective; returns the current value in the objective's unit. */
    public static double value(ResearchState.PlayerResearch research, AcceleratorNetwork network,
                               QuestCatalog.Objective objective) {
        return switch (objective.kind()) {
            case ELEMENT_COUNT -> research.discoveredElementCount();
            case ISOTOPE_COUNT -> research.discoveredIsotopes.size();
            case COLLISIONS -> research.collisionCount;
            case MILESTONE -> research.milestone(objective.key());
            case DOSE -> research.accumulatedDose;
            case UNLOCK -> unlockValue(research, objective.key());
            case MACHINE -> machineValue(research, network, objective.key());
        };
    }

    private static double unlockValue(ResearchState.PlayerResearch research, String key) {
        if (research.isUnlocked(key)) {
            return 1.0;
        }
        if ("rare_observed".equals(key)) {
            for (String unlocked : research.unlocked) {
                if (unlocked.startsWith("rare_process_")) {
                    return 1.0;
                }
            }
        }
        if ("rare_high_energy".equals(key)) {
            return research.isUnlocked("rare_high_energy") ? 1.0 : 0.0;
        }
        return 0.0;
    }

    private static double machineValue(ResearchState.PlayerResearch research,
                                       AcceleratorNetwork network, String key) {
        if (network == null) {
            return 0.0;
        }
        switch (key) {
            case "computer" -> {
                return count(network, MachineKind.CONTROL_COMPUTER);
            }
            case "magnets" -> {
                return count(network, MachineKind.DIPOLE_MAGNET)
                        + count(network, MachineKind.SUPERCONDUCTING_DIPOLE)
                        + count(network, MachineKind.QUADRUPOLE_MAGNET)
                        + count(network, MachineKind.SEXTUPOLE_MAGNET)
                        + count(network, MachineKind.STEERING_MAGNET);
            }
            case "cavities" -> {
                return count(network, MachineKind.RF_CAVITY);
            }
            case "shielding" -> {
                int shielding = 0;
                for (String key : new String[]{"concrete_shielding", "lead_shielding",
                        "lead_block", "water_shielding", "borated_polyethylene"}) {
                    shielding += network.placedMaterials.getOrDefault(key, 0);
                }
                return shielding;
            }
            case "complete" -> {
                if (network.components.isEmpty()) {
                    return 0.0;
                }
                for (AcceleratorNetwork.Component component : network.components) {
                    if (!component.present || !component.correctKind
                            || !component.correctOrientation) {
                        return 0.0;
                    }
                }
                return 1.0;
            }
            case "deploy" -> {
                return 1.0;
            }
            case "power" -> {
                return network.powerSupplyKW;
            }
            case "vacuum" -> {
                return network.vacuumPressurePa <= 1.0e-9 ? 0.0 : network.vacuumPressurePa;
            }
            case "beam" -> {
                return network.beam.intensity > 1.0 ? 1.0 : 0.0;
            }
            case "beam_time" -> {
                return network.beamAliveSeconds;
            }
            case "fault_free" -> {
                return network.faultFreeSeconds;
            }
            case "orbit" -> {
                return network.machine.orbitDistortionMm;
            }
            case "energy" -> {
                return network.peakEnergyMeV;
            }
            case "luminosity" -> {
                return network.luminosity;
            }
            default -> {
                return 0.0;
            }
        }
    }

    private static int count(AcceleratorNetwork network, MachineKind kind) {
        int count = 0;
        for (AcceleratorNetwork.Component component : network.components) {
            if (component.present && component.kind == kind) {
                count++;
            }
        }
        return count;
    }

    /** The machine closest to the player, used to evaluate the construction objectives. */
    private static AcceleratorNetwork nearestNetwork(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return null;
        }
        SiteState.Site site = ModState.sites().at(player.getBlockPos(),
                world.getRegistryKey().getValue().toString());
        if (site != null) {
            return AcceleratorController.networkOf(site);
        }
        SiteState.Site best = null;
        double bestDistance = Double.MAX_VALUE;
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

    /** Announces quest completions when a player opens the journal. */
    public static void announce(ServerPlayerEntity player, String text) {
        player.sendMessage(Text.literal(text).formatted(Formatting.AQUA), false);
    }

    public static List<QuestCatalog.Chapter> chapters() {
        return QuestCatalog.chapters();
    }
}
