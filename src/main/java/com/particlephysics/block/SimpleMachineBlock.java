package com.particlephysics.block;

import com.mojang.serialization.MapCodec;
import com.particlephysics.accelerator.MachineKind;

import net.minecraft.block.Block;

/** A machine block without orientation properties. */
public class SimpleMachineBlock extends MachineBlock {
    public SimpleMachineBlock(Settings settings) {
        super(settings);
    }

    public SimpleMachineBlock(MachineKind kind, Settings settings) {
        this(settings);
        setKind(kind);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(SimpleMachineBlock::new);
    }
}
