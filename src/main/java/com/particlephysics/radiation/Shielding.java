package com.particlephysics.radiation;

import com.particlephysics.registry.ModBlocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

/**
 * Shielding physics: the attenuation of the radiation field by the blocks between a source and the
 * player, computed with the exponential attenuation law and per material, per radiation type
 * attenuation coefficients (in 1/metre).
 */
public final class Shielding {
    private Shielding() {
    }

    /** Macroscopic attenuation coefficient in 1/m for one block of the given material. */
    public static double coefficient(BlockState state, RadiationType type) {
        Block block = state.getBlock();
        boolean isLead = block == ModBlocks.LEAD_BLOCK || block == ModBlocks.LEAD_SHIELDING;
        boolean isConcrete = block == ModBlocks.CONCRETE_SHIELDING;
        boolean isWater = block == ModBlocks.WATER_SHIELDING;
        boolean isPoly = block == ModBlocks.BORATED_POLYETHYLENE;
        boolean isIron = block == Blocks.IRON_BLOCK || block == Blocks.NETHERITE_BLOCK;
        boolean empty = state.isAir();

        if (empty) {
            return switch (type) {
                case ALPHA -> 25.0;
                case BETA -> 0.35;
                case GAMMA -> 0.008;
                case NEUTRON -> 0.002;
                case PROTON -> 20.0;
                case MUON -> 0.0002;
            };
        }
        switch (type) {
            case ALPHA, PROTON:
                // a few millimetres of anything stops them
                return 400.0;
            case BETA:
                // ~1 cm of dense material; light materials are worse due to bremsstrahlung
                return isLead ? 90.0 : 60.0;
            case GAMMA:
                if (isLead) {
                    return 55.0;
                }
                if (isIron) {
                    return 28.0;
                }
                if (isWater) {
                    return 9.0;
                }
                if (isConcrete) {
                    return 12.0;
                }
                if (isPoly) {
                    return 5.0;
                }
                return 7.0;
            case NEUTRON:
                // hydrogen and boron rich materials are the good ones, lead is nearly transparent
                if (isPoly) {
                    return 42.0;
                }
                if (isWater) {
                    return 16.0;
                }
                if (isConcrete) {
                    return 6.0;
                }
                if (isLead) {
                    return 2.0;
                }
                if (isIron) {
                    return 4.0;
                }
                return 3.0;
            case MUON:
                return isIron || isLead ? 0.6 : 0.25;
            default:
                return 5.0;
        }
    }

    /**
     * Attenuation factor (0..1) of the radiation field along the straight line between two points.
     *
     * <p>The line is sampled with a step of about one block, which is accurate enough while keeping
     * the cost of hundreds of player-source pairs per tick negligible.
     */
    public static double transmission(BlockView world, Vec3d from, Vec3d to, RadiationType type) {
        Vec3d delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 0.5) {
            return 1.0;
        }
        int steps = (int) Math.min(128, Math.max(2, distance));
        double stepLength = distance / steps;
        Vec3d direction = delta.multiply(1.0 / distance);
        double opticalDepth = 0.0;
        double x = from.x;
        double y = from.y;
        double z = from.z;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockState lastState = null;
        double lastCoefficient = 0.0;
        for (int i = 0; i < steps; i++) {
            x += direction.x * stepLength;
            y += direction.y * stepLength;
            z += direction.z * stepLength;
            pos.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            BlockState state = world.getBlockState(pos);
            if (state != lastState) {
                lastCoefficient = coefficient(state, type);
                lastState = state;
            }
            opticalDepth += lastCoefficient * stepLength;
            if (opticalDepth > 60.0) {
                break;
            }
        }
        return Math.exp(-opticalDepth);
    }

    /** Suggested material name for the diagnostics screen. */
    public static String materialName(BlockState state) {
        Block block = state.getBlock();
        if (block == ModBlocks.LEAD_BLOCK || block == ModBlocks.LEAD_SHIELDING) {
            return "lead";
        }
        if (block == ModBlocks.CONCRETE_SHIELDING) {
            return "concrete";
        }
        if (block == ModBlocks.WATER_SHIELDING) {
            return "water";
        }
        if (block == ModBlocks.BORATED_POLYETHYLENE) {
            return "borated polyethylene";
        }
        return block.getName().getString();
    }
}
