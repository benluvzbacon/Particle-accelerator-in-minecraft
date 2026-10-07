package com.particlephysics.item;

import java.util.List;

import com.particlephysics.net.ModNetworking;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Hand held radiation meter. Right clicking asks the server for the dose rate at the player's
 * position, which is computed from the real radiation field (sources, shielding, distance) and
 * reported in the action bar.
 */
public class GeigerCounterItem extends Item {
    public GeigerCounterItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            ModNetworking.requestScreen(player, "geiger");
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip,
                              TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.particleaccelerator.geiger_counter.tooltip")
                .formatted(Formatting.GRAY));
    }
}
