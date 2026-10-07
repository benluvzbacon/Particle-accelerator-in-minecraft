package com.particlephysics.block;

import com.mojang.serialization.MapCodec;
import com.particlephysics.accelerator.MachineKind;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * A machine block that knows which way it faces.
 *
 * <p>Orientation is physically meaningful: a quadrupole magnet rotated by 90 degrees focuses in the
 * wrong plane, and a dipole magnet that points the wrong way bends the beam out of the ring. Both
 * are detected by the construction validator and both are visible in the beam simulation.
 */
public class OrientedMachineBlock extends MachineBlock {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;

    public OrientedMachineBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH));
    }

    public OrientedMachineBlock(MachineKind kind, Settings settings) {
        this(settings);
        setKind(kind);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(OrientedMachineBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        return getDefaultState().with(FACING, facing);
    }

    /** Number of 90 degree turns between an actual facing and the required one. */
    public static int quarterTurns(Direction actual, Direction required) {
        if (actual == null || required == null || actual.getAxis() == Direction.Axis.Y
                || required.getAxis() == Direction.Axis.Y) {
            return 0;
        }
        return (actual.getHorizontal() - required.getHorizontal() + 4) % 4;
    }
}
