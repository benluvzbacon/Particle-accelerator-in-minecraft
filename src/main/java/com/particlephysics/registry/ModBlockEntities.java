package com.particlephysics.registry;

import com.particlephysics.ParticleAcceleratorMod;
import com.particlephysics.blockentity.MachineBlockEntity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * One block entity type covers every accelerator component: which component a block is, is stored
 * on the block (see {@link com.particlephysics.accelerator.MachineKind}), so a new component never
 * needs a new block entity type.
 */
public final class ModBlockEntities {
    public static BlockEntityType<MachineBlockEntity> MACHINE;

    private ModBlockEntities() {
    }

    public static void register() {
        Block[] blocks = ModBlocks.machines().values().toArray(new Block[0]);
        MACHINE = Registry.register(Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(ParticleAcceleratorMod.MOD_ID, "machine"),
                FabricBlockEntityTypeBuilder.create(MachineBlockEntity::new, blocks).build());
    }

    public static int count() {
        return 1;
    }
}
