package com.particlephysics.block;

import com.mojang.serialization.MapCodec;
import com.particlephysics.accelerator.MachineKind;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

/**
 * A beamline component with an axis: the beam passes through it along its axis.
 *
 * <p>The axis is not decoration. A quadrupole whose axis is rotated by 90 degrees focuses in the
 * wrong plane, a dipole that is not tangential to the design orbit bends the beam into the vacuum
 * chamber wall, and an RF cavity that is not aligned with the beam does not accelerate at all. The
 * construction validator compares the placed axis with the axis required by the design and the beam
 * simulation uses the axis that was actually built.
 */
public class AxisMachineBlock extends MachineBlock {
    public static final EnumProperty<Direction.Axis> AXIS = Properties.AXIS;

    public AxisMachineBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(AXIS, Direction.Axis.Z));
    }

    public AxisMachineBlock(MachineKind kind, Settings settings) {
        this(settings);
        setKind(kind);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(AxisMachineBlock::new);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction look = ctx.getPlayerLookDirection();
        Direction.Axis axis = look.getAxis().isHorizontal() ? look.getAxis() : Direction.Axis.Z;
        return getDefaultState().with(AXIS, axis);
    }

    /** Axis of a placed machine block, or null when the block has no axis property. */
    public static Direction.Axis axisOf(BlockState state) {
        return state.contains(AXIS) ? state.get(AXIS) : null;
    }
}
