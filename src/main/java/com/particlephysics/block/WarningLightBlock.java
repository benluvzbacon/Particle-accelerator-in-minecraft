package com.particlephysics.block;

import com.mojang.serialization.MapCodec;
import com.particlephysics.accelerator.MachineKind;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;

/**
 * The radiation warning light. It is switched on by the controller when the measured dose rate in
 * its section exceeds the configured alarm threshold, so the player sees a running machine from the
 * outside without opening a GUI.
 */
public class WarningLightBlock extends SimpleMachineBlock {
    public static final BooleanProperty LIT = Properties.LIT;

    public WarningLightBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(LIT, false));
    }

    public WarningLightBlock(MachineKind kind, Settings settings) {
        this(settings);
        setKind(kind);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(WarningLightBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
