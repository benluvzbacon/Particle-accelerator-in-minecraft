package com.particlephysics.elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/**
 * What a Minecraft material is made of, in terms of the periodic table.
 *
 * <p>Everything the player can put into a machine (a target, a source, a shielding block) is mapped
 * onto a chemical element, so irradiating iron really produces cobalt and manganese, and smashing
 * lead really produces spallation products. Where Minecraft has no direct equivalent the closest
 * real material is used (bone meal is calcium phosphate, gunpowder stands in for the light elements
 * of a nitrate, amethyst for a silicon dioxide crystal).
 */
public final class ItemElements {
    private static final Map<Item, int[]> ITEM_COMPOSITION = new HashMap<>();
    private static final Map<Block, int[]> BLOCK_COMPOSITION = new HashMap<>();

    private ItemElements() {
    }

    private static void item(Item item, int... z) {
        ITEM_COMPOSITION.put(item, z);
    }

    private static void block(Block block, int... z) {
        BLOCK_COMPOSITION.put(block, z);
    }

    static {
        // metals
        item(Items.IRON_INGOT, 26);
        item(Items.RAW_IRON, 26);
        item(Items.COPPER_INGOT, 29);
        item(Items.RAW_COPPER, 29);
        item(Items.GOLD_INGOT, 79);
        item(Items.RAW_GOLD, 79);
        item(Items.NETHERITE_INGOT, 26, 6, 79);
        item(Items.NETHERITE_SCRAP, 26, 6);
        item(Items.IRON_NUGGET, 26);
        item(Items.GOLD_NUGGET, 79);
        // light elements
        item(Items.COAL, 6);
        item(Items.CHARCOAL, 6);
        item(Items.DIAMOND, 6);
        item(Items.EMERALD, 4, 13);          // beryl: Be/Al silicate
        item(Items.QUARTZ, 14, 8);
        item(Items.AMETHYST_SHARD, 14, 8);
        item(Items.GLASS, 14, 8);
        item(Items.SAND, 14, 8);
        item(Items.REDSTONE, 29, 8);         // copper oxide
        item(Items.LAPIS_LAZULI, 16, 14, 11); // lazurite: S/Si/Na with aluminium
        item(Items.BONE_MEAL, 20, 15);       // calcium phosphate
        item(Items.BONE, 20, 15);
        item(Items.BLAZE_POWDER, 16, 8);
        item(Items.GUNPOWDER, 19, 7, 16);    // nitrate/sulfur/charcoal
        item(Items.GLOWSTONE_DUST, 16, 8);
        item(Items.SLIME_BALL, 1, 6, 8);
        item(Items.ICE, 1, 8);
        item(Items.PACKED_ICE, 1, 8);
        item(Items.BLUE_ICE, 1, 8);
        item(Items.SNOWBALL, 1, 8);
        item(Items.WATER_BUCKET, 1, 8);
        item(Items.CLAY_BALL, 13, 14, 8);
        item(Items.BRICK, 13, 14, 8);
        item(Items.LEATHER, 6, 1, 8, 7);
        item(Items.COPPER_INGOT, 29);
        item(Items.ECHO_SHARD, 14, 8);

        // blocks
        block(Blocks.IRON_BLOCK, 26);
        block(Blocks.COPPER_BLOCK, 29);
        block(Blocks.GOLD_BLOCK, 79);
        block(Blocks.NETHERITE_BLOCK, 26, 6, 79);
        block(Blocks.COAL_BLOCK, 6);
        block(Blocks.DIAMOND_BLOCK, 6);
        block(Blocks.EMERALD_BLOCK, 4, 13);
        block(Blocks.QUARTZ_BLOCK, 14, 8);
        block(Blocks.GLASS, 14, 8);
        block(Blocks.SAND, 14, 8);
        block(Blocks.GRAVEL, 14, 8, 26);
        block(Blocks.STONE, 14, 8, 20, 13);
        block(Blocks.DEEPSLATE, 14, 8, 20, 13, 12);
        block(Blocks.CLAY, 13, 14, 8);
        block(Blocks.BONE_BLOCK, 20, 15);
        block(Blocks.WATER, 1, 8);
        block(Blocks.LAVA, 14, 8, 26, 12);
        block(Blocks.OBSIDIAN, 14, 8, 26, 12);
        block(Blocks.NETHERRACK, 14, 8, 26);
        block(Blocks.ANCIENT_DEBRIS, 26, 6);
        block(Blocks.WHITE_CONCRETE, 20, 14, 8);
        block(Blocks.WHITE_WOOL, 6, 1, 7, 16);

        // this mod's materials
        block(com.particlephysics.registry.ModBlocks.LEAD_ORE, 82, 16);
        block(com.particlephysics.registry.ModBlocks.DEEPSLATE_LEAD_ORE, 82, 16, 14);
        block(com.particlephysics.registry.ModBlocks.LEAD_BLOCK, 82);
        block(com.particlephysics.registry.ModBlocks.LEAD_SHIELDING, 82);
        block(com.particlephysics.registry.ModBlocks.CONCRETE_SHIELDING, 20, 14, 8, 82);
        block(com.particlephysics.registry.ModBlocks.WATER_SHIELDING, 1, 8);
        block(com.particlephysics.registry.ModBlocks.BORATED_POLYETHYLENE, 1, 6, 5);
        block(com.particlephysics.registry.ModBlocks.MACHINE_CASING, 26, 82);
        block(com.particlephysics.registry.ModBlocks.CRYOSTAT_WALL, 26, 13, 29);
        item(com.particlephysics.registry.ModItems.LEAD_INGOT, 82);
        item(com.particlephysics.registry.ModItems.BORON_POWDER, 5, 8);
        item(com.particlephysics.registry.ModItems.COIL_WIRE, 29, 26);
        item(com.particlephysics.registry.ModItems.SUPERCONDUCTING_COIL, 41, 22, 29, 79);
        item(com.particlephysics.registry.ModItems.CIRCUIT_BOARD, 29, 79, 14);
        item(com.particlephysics.registry.ModItems.PHOTODETECTOR, 14, 8, 16);
        item(com.particlephysics.registry.ModItems.TARGET_FOIL, 26, 82);
        item(com.particlephysics.registry.ModItems.VACUUM_SEAL, 6, 1, 8, 16);
        item(com.particlephysics.registry.ModItems.HYDROGEN_CELL, 1);
        item(com.particlephysics.registry.ModItems.DEUTERIUM_CELL, 1);
        item(com.particlephysics.registry.ModItems.TRITIUM_CELL, 1);
        item(com.particlephysics.registry.ModItems.HELIUM_CELL, 2);
    }

    /** Atomic numbers the given stack is made of (heaviest first). */
    public static List<Integer> compositionOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        int[] direct = ITEM_COMPOSITION.get(stack.getItem());
        if (direct == null && stack.getItem() instanceof net.minecraft.item.BlockItem blockItem) {
            direct = BLOCK_COMPOSITION.get(blockItem.getBlock());
        }
        if (direct == null) {
            return List.of();
        }
        List<Integer> list = new ArrayList<>();
        for (int z : direct) {
            if (z >= 1 && z <= 118 && !list.contains(z)) {
                list.add(z);
            }
        }
        list.sort((a, b) -> Integer.compare(b, a));
        return list;
    }

    /** The dominant (heaviest) element of a stack, or 0 when unknown. */
    public static int dominantElement(ItemStack stack) {
        List<Integer> composition = compositionOf(stack);
        return composition.isEmpty() ? 0 : composition.get(0);
    }

    public static Element dominantElementOf(ItemStack stack) {
        int z = dominantElement(stack);
        return z == 0 ? null : Elements.byZ(z);
    }

    /** Composition of a block state, used for mining discoveries and shielding accounting. */
    public static List<Integer> compositionOf(Block block) {
        int[] direct = BLOCK_COMPOSITION.get(block);
        if (direct == null) {
            Item item = block.asItem();
            if (item != Items.AIR) {
                return compositionOf(new ItemStack(item));
            }
            return List.of();
        }
        List<Integer> list = new ArrayList<>();
        for (int z : direct) {
            if (z >= 1 && z <= 118 && !list.contains(z)) {
                list.add(z);
            }
        }
        return list;
    }
}
