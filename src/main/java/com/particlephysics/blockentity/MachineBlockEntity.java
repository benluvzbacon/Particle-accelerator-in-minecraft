package com.particlephysics.blockentity;

import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.item.GasCellItem;
import com.particlephysics.registry.ModBlockEntities;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The block entity behind every accelerator component.
 *
 * <p>It owns what belongs to a single block: the items a machine consumes (source gas, targets,
 * coolant), the local setpoints (cavity voltage and phase, magnet trim), and the buffered readouts
 * the gauges show. The physics of the machine as a whole lives in
 * {@link com.particlephysics.accelerator.AcceleratorNetwork}, which reads and writes these values.
 */
public class MachineBlockEntity extends AbstractMachineBlockEntity {
    /** Items inserted into a cooling unit per coolant unit. */
    public static final double COOLANT_PER_ICE = 4.0;
    /** Items inserted into a cryogenic unit per unit of liquid helium. */
    public static final double HELIUM_PER_BLUE_ICE = 6.0;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MACHINE, pos, state, kindOf(state));
    }

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                              MachineKind kind) {
        super(type, pos, state, kind, inventorySize(kind));
        if (kind != null) {
            // sensible default setpoints so a freshly placed cavity or magnet is usable at once
            switch (kind) {
                case RF_CAVITY -> setpointA = kind.capacity();
                case STEERING_MAGNET -> setpointA = 0.0;
                case DIPOLE_MAGNET, SUPERCONDUCTING_DIPOLE -> setpointA = 1.0;
                case QUADRUPOLE_MAGNET -> setpointA = 1.0;
                case SEXTUPOLE_MAGNET -> setpointA = 1.0;
                case CRYOGENIC_UNIT -> setpointA = 0.0;
                default -> setpointA = 0.0;
            }
        }
    }

    private static MachineKind kindOf(BlockState state) {
        if (state.getBlock() instanceof com.particlephysics.block.MachineBlock machine) {
            return machine.kind();
        }
        return null;
    }

    private static int inventorySize(MachineKind kind) {
        if (kind == null) {
            return 1;
        }
        return switch (kind) {
            case PARTICLE_SOURCE, INJECTOR -> 3;
            case TARGET_STATION, DECAY_CHAMBER -> 2;
            case COOLING_UNIT, CRYOGENIC_UNIT -> 2;
            default -> 1;
        };
    }

    /** True when this machine is a source that defines the injected species. */
    public boolean isSource() {
        return kind == MachineKind.PARTICLE_SOURCE || kind == MachineKind.INJECTOR;
    }

    public ItemStack sourceStack() {
        for (int i = 0; i < size(); i++) {
            ItemStack stack = getStack(i);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        if (kind == null || stack.isEmpty()) {
            return false;
        }
        return switch (kind) {
            case PARTICLE_SOURCE, INJECTOR -> stack.getItem() instanceof GasCellItem
                    || !com.particlephysics.elements.ItemElements.compositionOf(stack).isEmpty();
            case TARGET_STATION, DECAY_CHAMBER -> !com.particlephysics.elements.ItemElements
                    .compositionOf(stack).isEmpty();
            case COOLING_UNIT -> isCoolant(stack);
            case CRYOGENIC_UNIT -> isLiquidHelium(stack);
            case POWER_SUPPLY -> stack.isOf(Items.REDSTONE_BLOCK) || stack.isOf(Items.COPPER_INGOT);
            default -> false;
        };
    }

    private static boolean isCoolant(ItemStack stack) {
        return stack.isOf(Items.ICE) || stack.isOf(Items.PACKED_ICE) || stack.isOf(Items.SNOWBALL)
                || stack.isOf(Items.BLUE_ICE) || stack.isOf(Items.WATER_BUCKET);
    }

    private static boolean isLiquidHelium(ItemStack stack) {
        return stack.isOf(Items.BLUE_ICE) || stack.isOf(Items.PACKED_ICE) || stack.isOf(Items.ICE);
    }

    @Override
    public boolean insert(ItemStack stack) {
        if (kind == MachineKind.COOLING_UNIT && isCoolant(stack)) {
            level += stack.isOf(Items.ICE) || stack.isOf(Items.SNOWBALL) ? COOLANT_PER_ICE
                    : stack.isOf(Items.PACKED_ICE) ? COOLANT_PER_ICE * 2.0 : COOLANT_PER_ICE * 8.0;
            stack.decrement(1);
            markDirty();
            return true;
        }
        if (kind == MachineKind.CRYOGENIC_UNIT && isLiquidHelium(stack)) {
            level += stack.isOf(Items.BLUE_ICE) ? HELIUM_PER_BLUE_ICE : HELIUM_PER_ICE_HELIUM;
            stack.decrement(1);
            markDirty();
            return true;
        }
        return super.insert(stack);
    }

    /** Ice and packed ice are poor cryogens: they only top the unit up a little. */
    private static final double HELIUM_PER_ICE_HELIUM = 1.0;

    @Override
    protected void tickMachine(World world, BlockPos pos, BlockState state) {
        if (kind == null || world.isClient) {
            return;
        }
        // valves follow their block state, so a closed valve really closes the section
        if (kind == MachineKind.VACUUM_VALVE && state.contains(
                com.particlephysics.block.ValveBlock.OPEN)) {
            active = state.get(com.particlephysics.block.ValveBlock.OPEN);
        }
        if ((kind == MachineKind.COOLING_UNIT || kind == MachineKind.CRYOGENIC_UNIT)
                && level > 0.0 && active) {
            // consumption is proportional to the load, charged by the controller
            double drain = kind == MachineKind.COOLING_UNIT ? 0.002 : 0.0015;
            level = Math.max(0.0, level - drain);
            markDirty();
        }
        if (world.getTime() % 20 == 0) {
            temperatureC = Math.max(20.0, temperatureC - 0.5);
            markDirty();
        }
    }
}
