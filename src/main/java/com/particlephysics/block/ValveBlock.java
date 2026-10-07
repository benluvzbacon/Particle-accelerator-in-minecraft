package com.particlephysics.block;

import com.mojang.serialization.MapCodec;
import com.particlephysics.accelerator.MachineKind;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * A vacuum valve: it separates two sections of the beam pipe. Closing a valve isolates the vacuum,
 * which is what the player uses to keep part of the machine under vacuum while venting another
 * section for maintenance.
 */
public class ValveBlock extends OrientedMachineBlock {
    public static final BooleanProperty OPEN = Properties.OPEN;

    public ValveBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(OPEN, true));
    }

    public ValveBlock(MachineKind kind, Settings settings) {
        this(settings);
        setKind(kind);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(ValveBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(OPEN);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing).with(OPEN, true);
    }
}
