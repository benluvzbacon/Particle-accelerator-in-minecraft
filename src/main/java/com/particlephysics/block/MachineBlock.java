package com.particlephysics.block;

import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.blockentity.AbstractMachineBlockEntity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Base class of every accelerator component.
 *
 * <p>Machine blocks are ordinary blocks with a block entity: they can be oriented, they tell the
 * controller what kind of component they are, and they hand their contents back when broken.
 * Nothing here depends on creative mode: every component is built, placed and operated by the
 * player.
 */
public abstract class MachineBlock extends Block implements net.minecraft.block.BlockEntityProvider {
    protected MachineKind kind;

    protected MachineBlock(Settings settings) {
        super(settings);
    }

    public MachineKind kind() {
        return kind;
    }

    public void setKind(MachineKind kind) {
        this.kind = kind;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new com.particlephysics.blockentity.MachineBlockEntity(pos, state);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer,
                         ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof AbstractMachineBlockEntity machine
                && placer instanceof PlayerEntity player) {
            machine.onPlacedBy(player);
            machine.markDirty();
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof AbstractMachineBlockEntity machine && !world.isClient) {
            for (int i = 0; i < machine.size(); i++) {
                ItemStack stack = machine.getStack(i);
                if (!stack.isEmpty()) {
                    ItemEntity entity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5, stack.copy());
                    world.spawnEntity(entity);
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof AbstractMachineBlockEntity machine) {
            ItemStack held = player.getStackInHand(Hand.MAIN_HAND);
            if (player.isSneaking() && !held.isEmpty()
                    && machine.accepts(held)) {
                machine.insert(held);
                return ActionResult.CONSUME;
            }
            if (machine.onPlayerUse(player, held)) {
                return ActionResult.CONSUME;
            }
            machine.openScreen(player);
            return ActionResult.CONSUME;
        }
        return ActionResult.PASS;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return (tickWorld, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AbstractMachineBlockEntity machine
                    && !machine.isRemoved()) {
                machine.serverTick(tickWorld, pos, tickState);
            }
        };
    }

    public String kindName() {
        return kind == null ? "component" : kind.displayName();
    }
}
