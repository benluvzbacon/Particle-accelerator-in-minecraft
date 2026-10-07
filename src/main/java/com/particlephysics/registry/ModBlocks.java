package com.particlephysics.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.particlephysics.ParticleAcceleratorMod;
import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.block.AxisMachineBlock;
import com.particlephysics.block.MachineBlock;
import com.particlephysics.block.OrientedMachineBlock;
import com.particlephysics.block.SimpleMachineBlock;
import com.particlephysics.block.WarningLightBlock;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Every block of the mod.
 *
 * <p>Machine blocks are created from {@link MachineKind}, so adding a component to the enum is
 * enough to get a block, an item, a lattice mapping and a blueprint entry for it. The physical
 * numbers of a component (magnet field, pump speed, RF voltage) live in {@link MachineKind} and are
 * read by the simulation from the block that was actually placed.
 */
public final class ModBlocks {
    private static final Map<MachineKind, Block> MACHINES = new LinkedHashMap<>();
    private static final Map<Block, MachineKind> BY_BLOCK = new LinkedHashMap<>();
    /** Every block of the mod, in registration order (used for item and creative tab creation). */
    public static final List<Block> ALL = new ArrayList<>();

    // ---------------------------------------------------------------------------------------
    // Shielding and construction materials
    // ---------------------------------------------------------------------------------------

    public static final Block LEAD_BLOCK = material("lead_block", 6.0f, 9.0f, MapColor.IRON_GRAY);
    public static final Block LEAD_SHIELDING = material("lead_shielding", 5.0f, 8.0f, MapColor.IRON_GRAY);
    public static final Block CONCRETE_SHIELDING = material("concrete_shielding", 4.0f, 7.0f,
            MapColor.LIGHT_GRAY);
    public static final Block WATER_SHIELDING = material("water_shielding", 2.5f, 4.0f,
            MapColor.LIGHT_BLUE);
    public static final Block BORATED_POLYETHYLENE = material("borated_polyethylene", 2.0f, 3.0f,
            MapColor.WHITE);
    public static final Block MACHINE_CASING = material("machine_casing", 5.0f, 8.0f,
            MapColor.IRON_GRAY);
    public static final Block CRYOSTAT_WALL = material("cryostat_wall", 5.0f, 10.0f,
            MapColor.LIGHT_GRAY);

    // ---------------------------------------------------------------------------------------
    // Ores
    // ---------------------------------------------------------------------------------------

    public static final Block LEAD_ORE = register("lead_ore",
            new Block(AbstractBlock.Settings.create().strength(3.0f, 3.0f).requiresTool()
                    .sounds(BlockSoundGroup.STONE).mapColor(MapColor.STONE_GRAY)));
    public static final Block DEEPSLATE_LEAD_ORE = register("deepslate_lead_ore",
            new Block(AbstractBlock.Settings.create().strength(4.5f, 3.0f).requiresTool()
                    .sounds(BlockSoundGroup.DEEPSLATE).mapColor(MapColor.DEEPSLATE_GRAY)));

    static {
        for (MachineKind kind : MachineKind.values()) {
            registerMachine(kind);
        }
    }

    private ModBlocks() {
    }

    /** Forces class initialisation (called from the mod initialiser). */
    public static void initialize() {
        // registering happens in the static initialiser
    }

    public static Block forKind(MachineKind kind) {
        return MACHINES.get(kind);
    }

    public static Map<MachineKind, Block> machines() {
        return MACHINES;
    }

    /** The kind of accelerator component a block is, or null when it is not a machine block. */
    public static MachineKind kindOf(BlockState state) {
        if (state == null) {
            return null;
        }
        return state.getBlock() instanceof MachineBlock machine ? machine.kind() : null;
    }

    public static MachineKind kindOf(Block block) {
        return BY_BLOCK.get(block);
    }

    // ---------------------------------------------------------------------------------------

    private static Block material(String id, float hardness, float resistance, MapColor color) {
        return register(id, new Block(AbstractBlock.Settings.create()
                .strength(hardness, resistance)
                .requiresTool()
                .sounds(BlockSoundGroup.METAL)
                .mapColor(color)));
    }

    private static void registerMachine(MachineKind kind) {
        AbstractBlock.Settings settings = switch (kind.system()) {
            case BEAMLINE, VACUUM -> AbstractBlock.Settings.create()
                    .strength(3.5f, 6.0f).requiresTool().nonOpaque().sounds(BlockSoundGroup.METAL);
            case MAGNET -> AbstractBlock.Settings.create()
                    .strength(4.5f, 9.0f).requiresTool().sounds(BlockSoundGroup.METAL);
            case RF -> AbstractBlock.Settings.create()
                    .strength(4.0f, 7.0f).requiresTool().nonOpaque().sounds(BlockSoundGroup.METAL);
            case DETECTOR -> AbstractBlock.Settings.create()
                    .strength(3.0f, 5.0f).requiresTool().nonOpaque().sounds(BlockSoundGroup.GLASS);
            case CONTROL -> kind == MachineKind.WARNING_LIGHT
                    ? AbstractBlock.Settings.create().strength(1.5f, 2.0f).nonOpaque()
                            .sounds(BlockSoundGroup.GLASS)
                            .luminance(state -> state.get(WarningLightBlock.LIT) ? 14 : 3)
                    : AbstractBlock.Settings.create()
                            .strength(4.0f, 8.0f).requiresTool().sounds(BlockSoundGroup.METAL);
            case POWER -> AbstractBlock.Settings.create()
                    .strength(3.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.METAL);
            case COOLING, CRYO -> AbstractBlock.Settings.create()
                    .strength(4.0f, 7.0f).requiresTool().sounds(BlockSoundGroup.METAL);
            case SHIELDING -> AbstractBlock.Settings.create()
                    .strength(5.0f, 8.0f).requiresTool().sounds(BlockSoundGroup.STONE);
        };

        Block block = switch (kind.orientation()) {
            case NONE -> kind == MachineKind.WARNING_LIGHT
                    ? new WarningLightBlock(kind, settings)
                    : new SimpleMachineBlock(kind, settings);
            case FACING -> new OrientedMachineBlock(kind, settings);
            case AXIS -> new AxisMachineBlock(kind, settings);
        };
        register(kind.id(), block);
        MACHINES.put(kind, block);
        BY_BLOCK.put(block, kind);
    }

    private static Block register(String id, Block block) {
        Block registered = Registry.register(Registries.BLOCK,
                Identifier.of(ParticleAcceleratorMod.MOD_ID, id), block);
        ALL.add(registered);
        return registered;
    }
}
