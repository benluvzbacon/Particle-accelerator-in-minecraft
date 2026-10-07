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
 * The accelerator blueprint. Right clicking opens the construction guide: overview, layer by layer
 * cycling, material checklist, live validation and the systems view. The guide is filled from the
 * machine that is actually built in the world, so it always shows the real requirements and the real
 * errors.
 */
public class BlueprintItem extends Item {
    public BlueprintItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) {
            ModNetworking.requestScreen(player, "blueprint");
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void onCraftByPlayer(ItemStack stack, World world, PlayerEntity player) {
        super.onCraftByPlayer(stack, world, player);
        if (!world.isClient) {
            com.particlephysics.world.ModState.research(player).unlock("crafted_blueprint");
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip,
                              TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.translatable("item.particleaccelerator.blueprint.tooltip")
                .formatted(Formatting.GRAY));
    }
}
