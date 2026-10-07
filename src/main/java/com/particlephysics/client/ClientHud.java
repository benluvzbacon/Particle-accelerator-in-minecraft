package com.particlephysics.client;

import java.util.List;

import com.particlephysics.accelerator.AcceleratorDesign;
import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.config.ModConfig;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The head up display: machine status while the player is working on an accelerator, the radiation
 * meter, and the blueprint hint that tells the player where the next block goes.
 *
 * <p>Nothing here is decorative: every number comes from the server's simulation, and the hint
 * points at the exact block the validator is waiting for.
 */
public class ClientHud implements HudRenderCallback {
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.options.hudHidden) {
            return;
        }
        ModConfig config = ModConfig.get();
        int y = 4;
        int x = 4;
        boolean holdingBlueprint = isBlueprint(player.getMainHandStack())
                || isBlueprint(player.getOffHandStack());

        // --- blueprint hint -------------------------------------------------------------------
        NbtCompound blueprint = ClientState.blueprint;
        if (holdingBlueprint && blueprint != null && config.renderGhosts) {
            String title = blueprint.getBoolean("deployed") ? "Blueprint: " + blueprint.getString("kind")
                    : "Blueprint " + blueprint.getString("kind") + " (not deployed)";
            context.drawTextWithShadow(client.textRenderer, Text.literal(title)
                    .formatted(Formatting.AQUA), x, y, 0xFFFFFF);
            y += 11;
            int ok = blueprint.getInt("builtOk");
            int errors = blueprint.getInt("errors");
            context.drawTextWithShadow(client.textRenderer, Text.literal(
                    String.format("Built %d/%d   errors %d   cycle %d", ok, ok + errors, errors,
                            blueprint.getInt("cycle"))), x, y, errors > 0 ? 0xFF8080 : 0x80FF80);
            y += 11;
            if (errors > 0 && !ClientState.compounds(blueprint, "issues").isEmpty()) {
                NbtCompound issue = ClientState.compounds(blueprint, "issues").get(0);
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal(issue.getString("text")).formatted(Formatting.YELLOW), x, y,
                        0xFFFFFF);
                y += 11;
            }
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Right click the blueprint for the construction guide"),
                    x, y, 0xA0A0A0);
            y += 11;
        }

        // --- machine readout ------------------------------------------------------------------
        NbtCompound machine = ClientState.machine != null ? ClientState.machine
                : ClientState.computer;
        if (machine != null && machine.getBoolean("inMachine")) {
            y = drawMachineReadout(context, client, machine, x, y);
        }

        // --- radiation meter ------------------------------------------------------------------
        NbtCompound meter = machine != null ? machine : blueprint;
        if (meter != null) {
            double microSv = meter.getDouble("dose");
            if (microSv > 0.01) {
                String text = "Dose rate: " + UnitFormat.dose(microSv);
                int colour = microSv < 1.0 ? 0x80FF80 : microSv < 10.0 ? 0xFFFF80 : 0xFF6060;
                context.drawTextWithShadow(client.textRenderer, Text.literal(text), x, y, colour);
                y += 11;
            }
            String failure = meter.getString("failure");
            if (!failure.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal("! " + failure).formatted(Formatting.RED), x, y, 0xFF5555);
                y += 11;
            }
        }

        // --- messages -------------------------------------------------------------------------
        List<String> messages = ClientState.messages;
        int messageY = client.getWindow().getScaledHeight() - 40;
        for (int i = messages.size() - 1; i >= 0 && i > messages.size() - 4; i--) {
            context.drawTextWithShadow(client.textRenderer, Text.literal(messages.get(i)),
                    4, messageY, 0xFFFF80);
            messageY -= 10;
        }

        // --- held item context ----------------------------------------------------------------
        if (!holdingBlueprint) {
            ItemStack held = player.getMainHandStack();
            if (held.isOf(com.particlephysics.registry.ModItems.GEIGER_COUNTER)) {
                context.drawTextWithShadow(client.textRenderer,
                        Text.literal("Right click to measure the dose rate here"), 4, y, 0xA0A0A0);
            }
        }
    }

    private int drawMachineReadout(DrawContext context, MinecraftClient client, NbtCompound machine,
                                   int x, int y) {
        String site = machine.getString("site");
        boolean running = machine.getBoolean("running");
        context.drawTextWithShadow(client.textRenderer, Text.literal(
                (site.isEmpty() ? "Accelerator" : site)
                        + (running ? "  [RUNNING]" : "  [STANDBY]")), x, y,
                running ? 0x80FF80 : 0xA0A0A0);
        y += 11;
        context.drawTextWithShadow(client.textRenderer, Text.literal(
                "Beam " + UnitFormat.energy(machine.getDouble("energy")) + "   "
                        + UnitFormat.current(machine.getDouble("current"))), x, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(client.textRenderer, Text.literal(
                "Vacuum " + UnitFormat.pressure(machine.getDouble("vacuum"))), x, y, 0xFFFFFF);
        y += 11;
        context.drawTextWithShadow(client.textRenderer, Text.literal(
                "RF " + String.format("%.3f MV", machine.getDouble("rfv")) + " @ "
                        + String.format("%.4f MHz", machine.getDouble("rff"))
                        + "   B " + String.format("%.3f T", machine.getDouble("field"))),
                x, y, 0xFFFFFF);
        y += 11;
        double temperature = machine.getDouble("temperature");
        if (temperature > 45.0) {
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal(String.format("Magnets at %.0f C - cooling needed", temperature)),
                    x, y, 0xFFAA55);
            y += 11;
        }
        String kind = machine.getString("kind");
        MachineKind machineKind = MachineKind.byId(kind);
        if (machineKind != null && machineKind.system() == MachineKind.System.VACUUM) {
            double measured = machine.getDouble("measured");
            context.drawTextWithShadow(client.textRenderer,
                    Text.literal("Gauge: " + UnitFormat.pressure(measured)), x, y, 0xFFFFFF);
            y += 11;
        }
        return y;
    }

    private static boolean isBlueprint(ItemStack stack) {
        return !stack.isEmpty() && stack.isOf(com.particlephysics.registry.ModItems.BLUEPRINT);
    }
}
