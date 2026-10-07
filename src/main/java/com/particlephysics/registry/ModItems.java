package com.particlephysics.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.particlephysics.ParticleAcceleratorMod;
import com.particlephysics.item.BlueprintItem;
import com.particlephysics.item.GasCellItem;
import com.particlephysics.item.GeigerCounterItem;
import com.particlephysics.item.QuestBookItem;
import com.particlephysics.physics.ParticleSpecies;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Every item of the mod: one block item per machine, the tools of the trade (blueprint, journal,
 * radiation meter) and the materials that the accelerator consumes.
 */
public final class ModItems {
    public static final List<Item> ALL = new ArrayList<>();
    public static final Map<Block, Item> BLOCK_ITEMS = new LinkedHashMap<>();

    // ---------------------------------------------------------------------------------------
    // Tools
    // ---------------------------------------------------------------------------------------

    public static final Item BLUEPRINT = register("blueprint",
            new BlueprintItem(new Item.Settings().maxCount(1)));
    public static final Item QUEST_BOOK = register("quest_book",
            new QuestBookItem(new Item.Settings().maxCount(1)));
    public static final Item GEIGER_COUNTER = register("geiger_counter",
            new GeigerCounterItem(new Item.Settings().maxCount(1)));

    // ---------------------------------------------------------------------------------------
    // Materials
    // ---------------------------------------------------------------------------------------

    public static final Item LEAD_INGOT = register("lead_ingot", new Item(new Item.Settings()));
    public static final Item BORON_POWDER = register("boron_powder", new Item(new Item.Settings()));
    public static final Item COIL_WIRE = register("coil_wire", new Item(new Item.Settings()));
    public static final Item SUPERCONDUCTING_COIL = register("superconducting_coil",
            new Item(new Item.Settings()));
    public static final Item CIRCUIT_BOARD = register("circuit_board", new Item(new Item.Settings()));
    public static final Item PHOTODETECTOR = register("photodetector", new Item(new Item.Settings()));
    public static final Item TARGET_FOIL = register("target_foil", new Item(new Item.Settings()));
    public static final Item VACUUM_SEAL = register("vacuum_seal", new Item(new Item.Settings()));

    // ---------------------------------------------------------------------------------------
    // Beam sources
    // ---------------------------------------------------------------------------------------

    public static final Item HYDROGEN_CELL = register("hydrogen_cell",
            new GasCellItem(new Item.Settings(), ParticleSpecies.PROTON,
                    "Hydrogen gas: protons (H+)"));
    public static final Item DEUTERIUM_CELL = register("deuterium_cell",
            new GasCellItem(new Item.Settings(), ParticleSpecies.DEUTERON,
                    "Heavy hydrogen: deuterons (2H+)"));
    public static final Item TRITIUM_CELL = register("tritium_cell",
            new GasCellItem(new Item.Settings(), ParticleSpecies.TRITON,
                    "Tritium: tritons (3H+)"));
    public static final Item HELIUM_CELL = register("helium_cell",
            new GasCellItem(new Item.Settings(), ParticleSpecies.ALPHA,
                    "Helium-4: alpha particles (4He2+)"));

    private ModItems() {
    }

    /** Creates the block items and the creative tab. Called from the mod initialiser. */
    public static void register() {
        for (Block block : ModBlocks.ALL) {
            registerBlockItem(block);
        }
        Registry.register(Registries.ITEM_GROUP,
                Identifier.of(ParticleAcceleratorMod.MOD_ID, "main"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.particleaccelerator.main"))
                        .icon(() -> new ItemStack(BLUEPRINT))
                        .entries((context, entries) -> {
                            entries.add(BLUEPRINT);
                            entries.add(QUEST_BOOK);
                            entries.add(GEIGER_COUNTER);
                            for (Block block : ModBlocks.ALL) {
                                Item item = BLOCK_ITEMS.get(block);
                                if (item != null) {
                                    entries.add(item);
                                }
                            }
                            entries.add(LEAD_INGOT);
                            entries.add(BORON_POWDER);
                            entries.add(COIL_WIRE);
                            entries.add(SUPERCONDUCTING_COIL);
                            entries.add(CIRCUIT_BOARD);
                            entries.add(PHOTODETECTOR);
                            entries.add(TARGET_FOIL);
                            entries.add(VACUUM_SEAL);
                            entries.add(HYDROGEN_CELL);
                            entries.add(DEUTERIUM_CELL);
                            entries.add(TRITIUM_CELL);
                            entries.add(HELIUM_CELL);
                        })
                        .build());
    }

    private static void registerBlockItem(Block block) {
        BlockItem item = new BlockItem(block, new Item.Settings());
        register(Registries.BLOCK.getId(block).getPath(), item);
        BLOCK_ITEMS.put(block, item);
    }

    private static Item register(String id, Item item) {
        Item registered = Registry.register(Registries.ITEM,
                Identifier.of(ParticleAcceleratorMod.MOD_ID, id), item);
        ALL.add(registered);
        return registered;
    }

    public static Item blockItem(Block block) {
        return BLOCK_ITEMS.get(block);
    }
}
