package com.particlephysics.registry;

import com.particlephysics.ParticleAcceleratorMod;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Lead ore generation. Lead is the core shielding material and the cheapest source of heavy nuclei,
 * so it has to be minable in survival like any other metal. The ore is added to every overworld
 * biome, with the deepslate variant in the lower half of the world.
 */
public final class ModOreGeneration {
    public static final RegistryKey<PlacedFeature> LEAD_ORE_PLACED = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE, Identifier.of(ParticleAcceleratorMod.MOD_ID, "lead_ore"));
    public static final RegistryKey<PlacedFeature> DEEPSLATE_LEAD_ORE_PLACED = RegistryKey.of(
            RegistryKeys.PLACED_FEATURE,
            Identifier.of(ParticleAcceleratorMod.MOD_ID, "deepslate_lead_ore"));

    private ModOreGeneration() {
    }

    public static void register() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES, LEAD_ORE_PLACED);
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Feature.UNDERGROUND_ORES, DEEPSLATE_LEAD_ORE_PLACED);
    }
}
