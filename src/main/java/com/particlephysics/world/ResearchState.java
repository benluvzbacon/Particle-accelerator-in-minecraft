package com.particlephysics.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.particlephysics.elements.Element;
import com.particlephysics.elements.Elements;
import com.particlephysics.elements.Isotope;

import net.minecraft.nbt.NbtCompound;

/**
 * Per player research data: which elements and isotopes have been discovered, how they were made,
 * progress through the quest book, milestone values (highest beam energy, best vacuum, ...) and the
 * laboratory notebook of experiments that were run.
 */
public class ResearchState extends PersistentStore {
    /** How an element was first produced. */
    public enum DiscoverySource {
        MINED,
        DECAY,
        FUSION,
        NEUTRON_CAPTURE,
        SPALLATION,
        FISSION,
        HEAVY_ION_FUSION,
        COLLISION,
        PHOTONUCLEAR,
        ACCELERATOR_TARGET,
        SYNTHESISED;

        public String label() {
            return switch (this) {
                case MINED -> "Extracted from world materials";
                case DECAY -> "Observed in a radioactive decay chain";
                case FUSION -> "Produced by nuclear fusion";
                case NEUTRON_CAPTURE -> "Produced by neutron capture";
                case SPALLATION -> "Produced by spallation";
                case FISSION -> "Found among fission fragments";
                case HEAVY_ION_FUSION -> "Synthesised by heavy ion fusion";
                case COLLISION -> "Observed in a particle collision";
                case PHOTONUCLEAR -> "Produced by photonuclear reaction";
                case ACCELERATOR_TARGET -> "Collected from an accelerator target station";
                case SYNTHESISED -> "Assembled in the laboratory";
            };
        }
    }

    /** One entry of the experiment log shown by the detector readout. */
    public static final class ExperimentRecord {
        public String type;
        public double sqrtSGeV;
        public int multiplicity;
        public double energyGeV;
        public double invariantMassGeV;
        public boolean rare;
        public long gameTime;

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("type", type);
            nbt.putDouble("sqrts", sqrtSGeV);
            nbt.putInt("multiplicity", multiplicity);
            nbt.putDouble("energy", energyGeV);
            nbt.putDouble("mass", invariantMassGeV);
            nbt.putBoolean("rare", rare);
            nbt.putLong("time", gameTime);
            return nbt;
        }

        public static ExperimentRecord fromNbt(NbtCompound nbt) {
            ExperimentRecord record = new ExperimentRecord();
            record.type = nbt.getString("type");
            record.sqrtSGeV = nbt.getDouble("sqrts");
            record.multiplicity = nbt.getInt("multiplicity");
            record.energyGeV = nbt.getDouble("energy");
            record.invariantMassGeV = nbt.getDouble("mass");
            record.rare = nbt.getBoolean("rare");
            record.gameTime = nbt.getLong("time");
            return record;
        }
    }

    /** Everything known about one player. */
    public static final class PlayerResearch {
        /** Bit set of the 118 elements (element Z-1 bit). */
        public int[] discoveredElements = new int[4];
        /** Discovered nuclides encoded as z * 1000 + a. */
        public final List<Integer> discoveredIsotopes = new ArrayList<>();
        /** source per element (index = Z-1). */
        public final Map<Integer, DiscoverySource> elementSources = new HashMap<>();
        /** quest id -> progress state, 0 = not started, 1 = objectives met, 2 = completed. */
        public final Map<String, Integer> questProgress = new HashMap<>();
        /** Milestone values, e.g. "max_energy_mev" -> 1.2e8. */
        public final Map<String, Double> milestones = new HashMap<>();
        /** The 40 most recent experiment records. */
        public final List<ExperimentRecord> experiments = new ArrayList<>();
        /** Accumulated effective dose in sievert. */
        public double accumulatedDose;
        /** Dose accumulated in the current exposure window, used for acute effects. */
        public double recentDose;
        /** Beam current the player has achieved, in amperes (journal value). */
        public double bestBeamCurrent;
        /** Total number of collisions observed. */
        public long collisionCount;
        /** Unlocked recipes/knowledge flags. */
        public final List<String> unlocked = new ArrayList<>();

        public boolean hasDiscovered(int z) {
            if (z < 1 || z > 118) {
                return false;
            }
            int index = z - 1;
            return (discoveredElements[index / 32] & (1 << (index % 32))) != 0;
        }

        public boolean hasDiscoveredIsotope(int z, int a) {
            return discoveredIsotopes.contains(z * 1000 + a);
        }

        public int discoveredElementCount() {
            int count = 0;
            for (int z = 1; z <= 118; z++) {
                if (hasDiscovered(z)) {
                    count++;
                }
            }
            return count;
        }

        public void discover(Element element, DiscoverySource source) {
            if (element == null) {
                return;
            }
            int index = element.atomicNumber() - 1;
            boolean isNew = (discoveredElements[index / 32] & (1 << (index % 32))) == 0;
            discoveredElements[index / 32] |= 1 << (index % 32);
            elementSources.putIfAbsent(element.atomicNumber(), source);
            if (isNew) {
                markDirty();
            }
        }

        public void discoverIsotope(Isotope isotope, DiscoverySource source) {
            if (isotope == null) {
                return;
            }
            discover(isotope.element(), source);
            int key = isotope.element().atomicNumber() * 1000 + isotope.massNumber();
            if (!discoveredIsotopes.contains(key)) {
                discoveredIsotopes.add(key);
                markDirty();
            }
        }

        public void recordExperiment(ExperimentRecord record) {
            experiments.add(0, record);
            while (experiments.size() > 40) {
                experiments.remove(experiments.size() - 1);
            }
            markDirty();
        }

        public void raiseMilestone(String key, double value) {
            Double current = milestones.get(key);
            if (current == null || value > current) {
                milestones.put(key, value);
                markDirty();
            }
        }

        public double milestone(String key) {
            return milestones.getOrDefault(key, 0.0);
        }

        public boolean isUnlocked(String key) {
            return unlocked.contains(key);
        }

        public void unlock(String key) {
            if (!unlocked.contains(key)) {
                unlocked.add(key);
                markDirty();
            }
        }

        public DiscoverySource sourceOf(int z) {
            return elementSources.getOrDefault(z, DiscoverySource.SYNTHESISED);
        }
    }

    private final Map<UUID, PlayerResearch> players = new HashMap<>();

    public ResearchState() {
        super("research.nbt");
    }

    public PlayerResearch forPlayer(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> {
            markDirty();
            return new PlayerResearch();
        });
    }

    public Map<UUID, PlayerResearch> allPlayers() {
        return players;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        for (Map.Entry<UUID, PlayerResearch> entry : players.entrySet()) {
            PlayerResearch research = entry.getValue();
            NbtCompound nbt = new NbtCompound();
            nbt.putIntArray("elements", research.discoveredElements);
            int[] isotopeArray = new int[research.discoveredIsotopes.size()];
            for (int i = 0; i < isotopeArray.length; i++) {
                isotopeArray[i] = research.discoveredIsotopes.get(i);
            }
            nbt.putIntArray("isotopes", isotopeArray);
            StringBuilder sources = new StringBuilder();
            for (Map.Entry<Integer, DiscoverySource> source : research.elementSources.entrySet()) {
                sources.append(source.getKey()).append(':').append(source.getValue().name())
                        .append(';');
            }
            nbt.putString("sources", sources.toString());
            StringBuilder quests = new StringBuilder();
            for (Map.Entry<String, Integer> quest : research.questProgress.entrySet()) {
                quests.append(quest.getKey()).append(':').append(quest.getValue()).append(';');
            }
            nbt.putString("quests", quests.toString());
            NbtCompound milestones = new NbtCompound();
            for (Map.Entry<String, Double> milestone : research.milestones.entrySet()) {
                milestones.putDouble(milestone.getKey(), milestone.getValue());
            }
            nbt.put("milestones", milestones);
            nbt.putDouble("dose", research.accumulatedDose);
            nbt.putDouble("recentDose", research.recentDose);
            nbt.putDouble("bestCurrent", research.bestBeamCurrent);
            nbt.putLong("collisions", research.collisionCount);
            StringBuilder unlocked = new StringBuilder();
            for (String key : research.unlocked) {
                unlocked.append(key).append(';');
            }
            nbt.putString("unlocked", unlocked.toString());
            StringBuilder experiments = new StringBuilder();
            for (ExperimentRecord record : research.experiments) {
                experiments.append(record.type).append('|')
                        .append(record.sqrtSGeV).append('|')
                        .append(record.multiplicity).append('|')
                        .append(record.energyGeV).append('|')
                        .append(record.invariantMassGeV).append('|')
                        .append(record.rare).append('|')
                        .append(record.gameTime).append('\n');
            }
            nbt.putString("experiments", experiments.toString());
            root.put(entry.getKey().toString(), nbt);
        }
        return root;
    }

    @Override
    public void fromNbt(NbtCompound root) {
        players.clear();
        for (String key : root.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtCompound nbt = root.getCompound(key);
                PlayerResearch research = new PlayerResearch();
                int[] elements = nbt.getIntArray("elements");
                if (elements.length == 4) {
                    research.discoveredElements = elements;
                }
                for (int value : nbt.getIntArray("isotopes")) {
                    research.discoveredIsotopes.add(value);
                }
                for (String part : nbt.getString("sources").split(";")) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    String[] split = part.split(":");
                    if (split.length == 2) {
                        try {
                            research.elementSources.put(Integer.parseInt(split[0]),
                                    DiscoverySource.valueOf(split[1]));
                        } catch (IllegalArgumentException ignored) {
                            // unknown source from an older save: skip
                        }
                    }
                }
                for (String part : nbt.getString("quests").split(";")) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    int split = part.lastIndexOf(':');
                    if (split > 0) {
                        try {
                            research.questProgress.put(part.substring(0, split),
                                    Integer.parseInt(part.substring(split + 1)));
                        } catch (NumberFormatException ignored) {
                            // ignore malformed entry
                        }
                    }
                }
                NbtCompound milestones = nbt.getCompound("milestones");
                for (String name : milestones.getKeys()) {
                    research.milestones.put(name, milestones.getDouble(name));
                }
                research.accumulatedDose = nbt.getDouble("dose");
                research.recentDose = nbt.getDouble("recentDose");
                research.bestBeamCurrent = nbt.getDouble("bestCurrent");
                research.collisionCount = nbt.getLong("collisions");
                for (String part : nbt.getString("unlocked").split(";")) {
                    if (!part.isEmpty()) {
                        research.unlocked.add(part);
                    }
                }
                for (String line : nbt.getString("experiments").split("\n")) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    String[] split = line.split("\\|");
                    if (split.length >= 7) {
                        ExperimentRecord record = new ExperimentRecord();
                        record.type = split[0];
                        record.sqrtSGeV = parseDouble(split[1]);
                        record.multiplicity = (int) parseDouble(split[2]);
                        record.energyGeV = parseDouble(split[3]);
                        record.invariantMassGeV = parseDouble(split[4]);
                        record.rare = Boolean.parseBoolean(split[5]);
                        record.gameTime = (long) parseDouble(split[6]);
                        research.experiments.add(record);
                    }
                }
                players.put(uuid, research);
            } catch (IllegalArgumentException ignored) {
                // not a uuid key
            }
        }
    }

    private static double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Convenience: total number of discovered elements across all players. */
    public int totalDiscoveredElements() {
        int count = 0;
        for (PlayerResearch research : players.values()) {
            count = Math.max(count, research.discoveredElementCount());
        }
        return count;
    }

    /** Helper used by the quest book to display the periodic table. */
    public static List<Element> periodicTable() {
        return Elements.all();
    }
}
