package com.particlephysics.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.particlephysics.physics.BeamSimulator;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Configuration of the whole mod, stored in {@code config/particle-accelerator.json}.
 *
 * <p>The defaults are the "realistic but playable" settings: real physics constants, compressed
 * nuclear half lives, radiation that hurts but can be shielded, and a simulation that keeps up with
 * a large ring.
 */
public final class ModConfig {
    /** How much of the full simulation is applied. */
    public enum Accuracy {
        /** Fast: ideal machine, no gas scattering, no instabilities beyond orbit errors. */
        SIMPLIFIED,
        /** Default: gas scattering, instabilities, synchrotron radiation, decay. */
        REALISTIC,
        /** Everything on, including multiple scattering of single particles every turn. */
        RESEARCH
    }

    public Accuracy accuracy = Accuracy.REALISTIC;
    /** Simulation quality: number of tracked macro particles and map iterations. */
    public String particles = "HIGH";
    /** Number of simulation steps per second (1..20). Higher is smoother and costlier. */
    public int simulationTicksPerSecond = 10;
    /** Upper bound of macro particles tracked per machine. */
    public int maxParticlesPerMachine = 768;
    /** Maximum radius of an accelerator ring in blocks. */
    public int maxAcceleratorRadius = 60;
    /** Multiplier on the energy that RF cavities can deliver (1.0 = real scale). */
    public double energyScale = 1.0;
    /** Multiplier on the electrical power that the machine draws. */
    public double powerScale = 1.0;
    /** Multiplier on radiation dose rates. */
    public double radiationScale = 1.0;
    /** Multiplier on nuclear reaction and activation rates. */
    public double elementProductionScale = 1.0;
    /** Compressed half life factor: a nuclide decays this much faster than in reality. */
    public double halfLifeCompression = 7.2e7;
    /** Apply gas scattering and vacuum lifetime limits. */
    public boolean vacuumEnabled = true;
    /** Apply betatron/synchrotron instability growth. */
    public boolean instabilitiesEnabled = true;
    /** Apply synchrotron radiation energy loss. */
    public boolean synchrotronRadiationEnabled = true;
    /** Apply beam particle decay (compressed). */
    public boolean particleDecayEnabled = true;
    /** Radiation can hurt the player. */
    public boolean radiationDamageEnabled = true;
    /** Show the beam in the world. */
    public boolean renderBeam = true;
    /** Show magnet fields in the world. */
    public boolean renderFields = true;
    /** Show blueprint ghost blocks. */
    public boolean renderGhosts = true;
    /** Show collision flashes and detector events. */
    public boolean renderEvents = true;
    /** Maximum distance (blocks) at which ghost blocks are rendered. */
    public int ghostRenderDistance = 48;
    /** Interval for writing the mod's world data to disk. */
    public int autosaveSeconds = 60;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ModConfig instance;

    public static ModConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public BeamSimulator.Quality simulationQuality() {
        try {
            return BeamSimulator.Quality.valueOf(particles.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BeamSimulator.Quality.HIGH;
        }
    }

    /** Simulations never run with fewer than this many macro particles. */
    public static final int MIN_PARTICLES = 8;

    /** Number of macro particles tracked for one machine. */
    public int particlesPerMachine() {
        int budget = Math.min(maxParticlesPerMachine, simulationQuality().maxParticles);
        return Math.max(MIN_PARTICLES, budget);
    }

    // ------------------------------------------------------------------------------------------

    private static ModConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir()
                .resolve("particle-accelerator.json");
        ModConfig config = new ModConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                com.particlephysics.ParticleAcceleratorMod.LOGGER.warn(
                        "Could not read the config, using defaults: {}", e.toString());
            }
        }
        config.save(path);
        return config;
    }

    public void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            com.particlephysics.ParticleAcceleratorMod.LOGGER.warn("Could not write the config: {}",
                    e.toString());
        }
    }

    public void save() {
        save(FabricLoader.getInstance().getConfigDir().resolve("particle-accelerator.json"));
    }

    /** All settings with their current values, for the control screen and the command. */
    public Map<String, String> values() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("accuracy", accuracy.name());
        map.put("particles", particles);
        map.put("simulationTicksPerSecond", Integer.toString(simulationTicksPerSecond));
        map.put("maxParticlesPerMachine", Integer.toString(maxParticlesPerMachine));
        map.put("maxAcceleratorRadius", Integer.toString(maxAcceleratorRadius));
        map.put("energyScale", Double.toString(energyScale));
        map.put("powerScale", Double.toString(powerScale));
        map.put("radiationScale", Double.toString(radiationScale));
        map.put("elementProductionScale", Double.toString(elementProductionScale));
        map.put("halfLifeCompression", Double.toString(halfLifeCompression));
        map.put("vacuumEnabled", Boolean.toString(vacuumEnabled));
        map.put("instabilitiesEnabled", Boolean.toString(instabilitiesEnabled));
        map.put("synchrotronRadiationEnabled", Boolean.toString(synchrotronRadiationEnabled));
        map.put("particleDecayEnabled", Boolean.toString(particleDecayEnabled));
        map.put("radiationDamageEnabled", Boolean.toString(radiationDamageEnabled));
        map.put("renderBeam", Boolean.toString(renderBeam));
        map.put("renderFields", Boolean.toString(renderFields));
        map.put("renderGhosts", Boolean.toString(renderGhosts));
        map.put("renderEvents", Boolean.toString(renderEvents));
        map.put("ghostRenderDistance", Integer.toString(ghostRenderDistance));
        map.put("autosaveSeconds", Integer.toString(autosaveSeconds));
        return map;
    }

    /** Sets one setting from a string; returns false when the key is unknown. */
    public boolean set(String key, String value) {
        try {
            switch (key) {
                case "accuracy" -> accuracy = Accuracy.valueOf(value.toUpperCase(java.util.Locale.ROOT));
                case "particles" -> particles = value.toUpperCase(java.util.Locale.ROOT);
                case "simulationTicksPerSecond" -> simulationTicksPerSecond =
                        clamp(Integer.parseInt(value), 1, 20);
                case "maxParticlesPerMachine" -> maxParticlesPerMachine =
                        clamp(Integer.parseInt(value), 8, 8192);
                case "maxAcceleratorRadius" -> maxAcceleratorRadius =
                        clamp(Integer.parseInt(value), 8, 128);
                case "energyScale" -> energyScale = clamp(Double.parseDouble(value), 0.01, 1000.0);
                case "powerScale" -> powerScale = clamp(Double.parseDouble(value), 0.01, 1000.0);
                case "radiationScale" -> radiationScale = clamp(Double.parseDouble(value), 0.0, 1000.0);
                case "elementProductionScale" -> elementProductionScale =
                        clamp(Double.parseDouble(value), 0.0, 1000.0);
                case "halfLifeCompression" -> halfLifeCompression =
                        clamp(Double.parseDouble(value), 1.0, 1.0e12);
                case "vacuumEnabled" -> vacuumEnabled = Boolean.parseBoolean(value);
                case "instabilitiesEnabled" -> instabilitiesEnabled = Boolean.parseBoolean(value);
                case "synchrotronRadiationEnabled" ->
                        synchrotronRadiationEnabled = Boolean.parseBoolean(value);
                case "particleDecayEnabled" -> particleDecayEnabled = Boolean.parseBoolean(value);
                case "radiationDamageEnabled" -> radiationDamageEnabled = Boolean.parseBoolean(value);
                case "renderBeam" -> renderBeam = Boolean.parseBoolean(value);
                case "renderFields" -> renderFields = Boolean.parseBoolean(value);
                case "renderGhosts" -> renderGhosts = Boolean.parseBoolean(value);
                case "renderEvents" -> renderEvents = Boolean.parseBoolean(value);
                case "ghostRenderDistance" -> ghostRenderDistance =
                        clamp(Integer.parseInt(value), 8, 256);
                case "autosaveSeconds" -> autosaveSeconds = clamp(Integer.parseInt(value), 5, 3600);
                default -> {
                    return false;
                }
            }
            save();
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
