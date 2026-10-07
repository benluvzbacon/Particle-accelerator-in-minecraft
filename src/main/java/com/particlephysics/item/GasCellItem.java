package com.particlephysics.item;

import java.util.List;

import com.particlephysics.physics.ParticleSpecies;

import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * A source bottle for the ion source. The cell defines the species that is injected: protons from
 * hydrogen, deuterons, tritons, alpha particles from helium. The nuclear charge and mass number are
 * carried by the item, so the injected beam has the correct charge-to-mass ratio.
 */
public class GasCellItem extends Item {
    private final ParticleSpecies species;
    private final String description;

    public GasCellItem(Settings settings, ParticleSpecies species, String description) {
        super(settings);
        this.species = species;
        this.description = description;
    }

    public ParticleSpecies species() {
        return species;
    }

    public int elementZ() {
        return species.atomicNumber();
    }

    public int massNumber() {
        return species.nucleons();
    }

    public String description() {
        return description;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip,
                              TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        tooltip.add(Text.literal(description).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.particleaccelerator.gas_cell.species",
                species.symbol(), elementZ(), massNumber()).formatted(Formatting.DARK_GRAY));
    }
}
