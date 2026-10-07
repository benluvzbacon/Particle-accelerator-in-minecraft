package com.particlephysics.blockentity;

import java.util.ArrayList;
import java.util.List;

import com.particlephysics.ParticleAcceleratorMod;
import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.net.ModNetworking;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Common behaviour of every accelerator component: a small inventory, an NBT backed state, and the
 * hooks the controller uses to drive it.
 *
 * <p>The physics itself lives in {@link com.particlephysics.accelerator.AcceleratorNetwork}: block
 * entities only store what belongs to them (fuel, coolant, target material, measured values) and
 * are read/updated by the controller, which keeps a single authoritative simulation per machine.
 */
public abstract class AbstractMachineBlockEntity extends BlockEntity implements Inventory {
    public static final int OUTPUT_SLOT = 2;

    protected final DefaultedList<ItemStack> inventory;
    protected MachineKind kind;
    protected boolean active;
    /** Generic double state: coolant level, stored energy, gas amount, heat... */
    protected double level;
    protected double temperatureC = 20.0;
    /** Energy stored locally in kilojoules (power supplies, cables). */
    protected double energyKJ;
    /** Game time of the last tick, used for rate limiting. */
    protected long lastTick;
    /** Cached values written by the controller for the GUI. */
    protected String statusLine = "";
    protected double measuredValue;
    protected double measuredTarget;
    /** Component specific setpoint: RF voltage [MV], magnet trim, steering angle [deg]. */
    protected double setpointA;
    /** Component specific second setpoint: RF phase [deg], extra analogue channel. */
    protected double setpointB;

    protected AbstractMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                         MachineKind kind, int inventorySize) {
        super(type, pos, state);
        this.kind = kind;
        this.inventory = DefaultedList.ofSize(inventorySize, ItemStack.EMPTY);
    }

    public MachineKind kind() {
        return kind;
    }

    public void setKind(MachineKind kind) {
        this.kind = kind;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public double level() {
        return level;
    }

    public void setLevel(double level) {
        this.level = level;
    }

    public double temperatureC() {
        return temperatureC;
    }

    public double energyKJ() {
        return energyKJ;
    }

    public void setEnergyKJ(double energyKJ) {
        this.energyKJ = energyKJ;
    }

    public String statusLine() {
        return statusLine;
    }

    public double measuredValue() {
        return measuredValue;
    }

    public void setMeasuredValue(double value) {
        this.measuredValue = value;
    }

    public double measuredTarget() {
        return measuredTarget;
    }

    public double setpointA() {
        return setpointA;
    }

    public void setSetpointA(double value) {
        this.setpointA = value;
        markDirty();
    }

    public double setpointB() {
        return setpointB;
    }

    public void setSetpointB(double value) {
        this.setpointB = value;
        markDirty();
    }

    public void setMeasuredTarget(double target) {
        this.measuredTarget = target;
    }

    /** Called once per tick on the server. */
    public void serverTick(World world, BlockPos pos, BlockState state) {
        if (kind == null || world == null) {
            return;
        }
        lastTick = world.getTime();
        tickMachine(world, pos, state);
    }

    protected void tickMachine(World world, BlockPos pos, BlockState state) {
        // default: nothing to do locally, the controller drives the physics
    }

    public void onPlacedBy(PlayerEntity player) {
        // default: nothing
    }

    /** True when the given stack can be inserted into this machine. */
    public boolean accepts(ItemStack stack) {
        return false;
    }

    /** Inserts a stack, merging with existing stacks when possible. */
    public boolean insert(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack existing = inventory.get(i);
            if (!existing.isEmpty() && ItemStack.areItemsAndComponentsEqual(existing, stack)
                    && existing.getCount() < existing.getMaxCount()) {
                int move = Math.min(stack.getCount(), existing.getMaxCount() - existing.getCount());
                existing.increment(move);
                stack.decrement(move);
                markDirty();
                return true;
            }
        }
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.get(i).isEmpty()) {
                inventory.set(i, stack.split(Math.min(stack.getCount(), stack.getMaxCount())));
                markDirty();
                return true;
            }
        }
        return false;
    }

    /** Called when the player right clicks the block with an item in hand. */
    public boolean onPlayerUse(PlayerEntity player, ItemStack stack) {
        if (!stack.isEmpty() && accepts(stack)) {
            if (insert(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Drops everything the machine holds. */
    public void ejectContents(World world, BlockPos pos) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!stack.isEmpty()) {
                ItemEntity entity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.0,
                        pos.getZ() + 0.5, stack.copy());
                world.spawnEntity(entity);
                inventory.set(i, ItemStack.EMPTY);
            }
        }
        markDirty();
    }

    /** Sends this machine's state to the player and asks the client to open its screen. */
    public void openScreen(PlayerEntity player) {
        ModNetworking.sendMachineState(player, this);
    }

    /** Reads a stack from the given hand, consuming one item. */
    protected boolean consumeItem(int slot) {
        ItemStack stack = inventory.get(slot);
        if (stack.isEmpty()) {
            return false;
        }
        stack.decrement(1);
        if (stack.isEmpty()) {
            inventory.set(slot, ItemStack.EMPTY);
        }
        markDirty();
        return true;
    }

    // --- persistence --------------------------------------------------------------------------

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        inventory.clear();
        Inventories.readNbt(nbt, inventory, registries);
        active = nbt.getBoolean("Active");
        level = nbt.getDouble("Level");
        temperatureC = nbt.contains("Temp") ? nbt.getDouble("Temp") : 20.0;
        energyKJ = nbt.getDouble("Energy");
        statusLine = nbt.getString("Status");
        measuredValue = nbt.getDouble("Measured");
        measuredTarget = nbt.getDouble("Target");
        setpointA = nbt.getDouble("SetpointA");
        setpointB = nbt.getDouble("SetpointB");
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, inventory, registries);
        nbt.putBoolean("Active", active);
        nbt.putDouble("Level", level);
        nbt.putDouble("Temp", temperatureC);
        nbt.putDouble("Energy", energyKJ);
        nbt.putString("Status", statusLine == null ? "" : statusLine);
        nbt.putDouble("Measured", measuredValue);
        nbt.putDouble("Target", measuredTarget);
        nbt.putDouble("SetpointA", setpointA);
        nbt.putDouble("SetpointB", setpointB);
    }

    // --- inventory ----------------------------------------------------------------------------

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getStack(int slot) {
        return slot >= 0 && slot < inventory.size() ? inventory.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = net.minecraft.inventory.Inventories.splitStack(inventory, slot, amount);
        markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = net.minecraft.inventory.Inventories.removeStack(inventory, slot);
        markDirty();
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        if (slot >= 0 && slot < inventory.size()) {
            inventory.set(slot, stack);
            markDirty();
        }
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return getWorld() != null && getWorld().getBlockEntity(getPos()) == this
                && player.squaredDistanceTo(getPos().getX() + 0.5, getPos().getY() + 0.5,
                getPos().getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        inventory.clear();
    }

    public List<ItemStack> contents() {
        List<ItemStack> list = new ArrayList<>();
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                list.add(stack);
            }
        }
        return list;
    }

    public void log(String message) {
        ParticleAcceleratorMod.LOGGER.debug("[{}] {}", kind, message);
    }
}
