package com.particlephysics.client.screen;

import java.util.List;

import com.particlephysics.client.ClientState;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The construction guide.
 *
 * <p>Six modes, all of them fed by the server's validation of the machine that is really in the
 * world: Overview, Construction (ghost cycling), Materials (checklist with progress), Layer,
 * Systems and Diagnostics. Everything the player reads here is a measurement.
 */
public class BlueprintScreen extends ModScreen {
    private static final String[] MODES = {"Overview", "Construction", "Materials", "Layer",
            "Systems", "Diagnostics"};

    private int page;
    private int mode;

    public BlueprintScreen() {
        super(Text.literal("Accelerator Blueprint"));
    }

    @Override
    protected void init() {
        NbtCompound data = ClientState.blueprint;
        if (data != null) {
            mode = data.getInt("mode");
        }
        int left = 10;
        int bottom = height - 30;
        for (int i = 0; i < MODES.length; i++) {
            final int index = i;
            button(MODES[i], left + i * 78, 22, 76, 18, () -> {
                mode = index;
                NbtCompound nbt = new NbtCompound();
                nbt.putString("route", "blueprint");
                nbt.putString("action", "mode");
                nbt.putInt("value", index);
                com.particlephysics.net.ModNetworking.sendAction(nbt);
            });
        }
        boolean deployed = data != null && data.getBoolean("deployed");
        button(deployed ? "Remove blueprint" : "Deploy blueprint", left, bottom, 130, 20, () -> {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("route", "blueprint");
            nbt.putString("action", deployed ? "forget" : "deploy");
            com.particlephysics.net.ModNetworking.sendAction(nbt);
        });
        button("Ring / Linac", left + 136, bottom, 90, 20, () -> {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("route", "blueprint");
            nbt.putString("action", "kind");
            nbt.putString("value", data != null && "LINAC".equals(data.getString("kind"))
                    ? "RING" : "LINAC");
            com.particlephysics.net.ModNetworking.sendAction(nbt);
        });
        button("- radius", left + 232, bottom, 70, 20, () -> adjust("size", -4));
        button("+ radius", left + 306, bottom, 70, 20, () -> adjust("size", 4));
        button("- cells", left + 380, bottom, 60, 20, () -> adjust("cells", -4));
        button("+ cells", left + 444, bottom, 60, 20, () -> adjust("cells", 4));
        if (mode == 1) {
            button("Prev section", left + 520, bottom, 90, 20, () -> adjust("cycle", -1));
            button("Next section", left + 614, bottom, 90, 20, () -> adjust("cycle", 1));
        }
        if (mode == 3) {
            button("Layer -", left + 520, bottom, 70, 20, () -> adjust("layer", -1));
            button("Layer +", left + 594, bottom, 70, 20, () -> adjust("layer", 1));
        }
    }

    private void adjust(String key, int delta) {
        NbtCompound data = ClientState.blueprint;
        int current = data == null ? 0 : data.getInt(key);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("route", "blueprint");
        nbt.putString("action", key);
        nbt.putInt("value", current + delta);
        com.particlephysics.net.ModNetworking.sendAction(nbt);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        NbtCompound data = ClientState.blueprint;
        if (data == null) {
            text(context, "No blueprint data yet - right click the blueprint again", 10, 50, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        panel(context, 8, 44, width - 16, height - 80);

        String kind = data.getString("kind");
        int size = data.getInt("size");
        int cells = data.getInt("cells");
        int ok = data.getInt("builtOk");
        int errors = data.getInt("errors");
        String header = String.format("%s  radius %d  cells %d  circumference %.1f m  %s",
                kind, size, cells, data.getDouble("circumference"),
                data.getBoolean("deployed") ? "deployed" : "not deployed");
        text(context, header, 14, 48, TEXT);
        row(context, "Components", ok + " / " + (ok + errors), 14, 62, width - 28,
                errors == 0 ? GOOD : BAD);
        progress(context, 14, 74, width - 28, 6, ok + errors == 0 ? 0.0
                : (double) ok / (ok + errors), GOOD);

        switch (mode) {
            case 0 -> renderOverview(context, data);
            case 1 -> renderConstruction(context, data);
            case 2 -> renderMaterials(context, data);
            case 3 -> renderLayer(context, data);
            case 4 -> renderSystems(context, data);
            default -> renderDiagnostics(context, data);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderOverview(DrawContext context, NbtCompound data) {
        int y = 90;
        text(context, "How to build it", 14, y, WARN);
        y += 12;
        for (String line : List.of(
                "1. Deploy the blueprint where the centre of the machine should be.",
                "2. Follow the ghost blocks: they are sent by the server for the real design.",
                "3. Place each block; the validator checks kind and orientation immediately.",
                "4. Shield the tunnel (lead, concrete, water, borated polyethylene) before running.",
                "5. Add power supplies, cooling units and cryogenics on the infrastructure ring.",
                "6. Place the control computer next to the ring and start the machine there.",
                "7. Pump the beam pipe down, inject, then ramp the magnets to the beam energy.",
                "8. Focus the quadrupoles: the beam only survives inside the stability region.")) {
            text(context, line, 14, y, TEXT);
            y += 11;
        }
        y += 6;
        text(context, "Sections: " + data.getInt("sections") + "   beam path blocks: "
                + data.getInt("components") + "   total blocks: " + data.getInt("slots"), 14, y,
                DIM);
        text(context, "Rotation: design aligned to " + data.getString("rotation"), 14, y + 12,
                DIM);
    }

    private void renderConstruction(DrawContext context, NbtCompound data) {
        List<NbtCompound> issues = ClientState.compounds(data, "issues");
        int y = 90;
        text(context, "Live validation - the server checks the blocks in the world", 14, y, WARN);
        y += 12;
        progress(context, 14, y, width - 28, 6, data.getInt("cycle") / 8.0, BORDER);
        y += 12;
        if (issues.isEmpty()) {
            text(context, "No errors: the machine matches the design.", 14, y, GOOD);
            return;
        }
        int shown = 0;
        for (int i = page * 14; i < issues.size() && shown < 14; i++, shown++) {
            NbtCompound issue = issues.get(i);
            int colour = issue.getBoolean("error") ? BAD : WARN;
            text(context, issue.getString("text") + "   [" + issue.getInt("x") + ", "
                    + issue.getInt("y") + ", " + issue.getInt("z") + "]", 14, y, colour);
            y += 11;
        }
        button("More", 14, y + 4, 60, 18, () -> page++);
        button("Less", 80, y + 4, 60, 18, () -> page = Math.max(0, page - 1));
    }

    private void renderMaterials(DrawContext context, NbtCompound data) {
        List<NbtCompound> materials = ClientState.compounds(data, "materials");
        int y = 92;
        text(context, "Material checklist", 14, y, WARN);
        y += 12;
        int requiredTotal = 0;
        int placedTotal = 0;
        for (NbtCompound material : materials) {
            requiredTotal += material.getInt("required");
            placedTotal += Math.min(material.getInt("placed"), material.getInt("required"));
        }
        row(context, "Overall", placedTotal + " / " + requiredTotal, 14, y, width - 28,
                placedTotal >= requiredTotal ? GOOD : WARN);
        y += 12;
        progress(context, 14, y, width - 28, 6, requiredTotal == 0 ? 0.0
                : (double) placedTotal / requiredTotal, GOOD);
        y += 14;
        int shown = 0;
        for (int i = page * 18; i < materials.size() && shown < 18; i++, shown++) {
            NbtCompound material = materials.get(i);
            int required = material.getInt("required");
            int placed = material.getInt("placed");
            row(context, material.getString("name"), placed + " / " + required, 14, y, width - 28,
                    placed >= required ? GOOD : WARN);
            y += 11;
        }
        button("More", 14, height - 58, 60, 18, () -> page++);
        button("Less", 80, height - 58, 60, 18, () -> page = Math.max(0, page - 1));
    }

    private void renderLayer(DrawContext context, NbtCompound data) {
        int layer = data.getInt("layer");
        int y = 92;
        text(context, "Layer view: choose a layer, then walk along the ghosts", 14, y, WARN);
        y += 14;
        row(context, "Layer (Y offset)", Integer.toString(layer), 14, y, width - 28, TEXT);
        y += 14;
        text(context, "The ghost blocks in the world show exactly this layer.", 14, y, DIM);
        y += 14;
        text(context, "Use the 'Layer -' and 'Layer +' buttons to move through the design.", 14, y,
                DIM);
    }

    private void renderSystems(DrawContext context, NbtCompound data) {
        int y = 92;
        text(context, "Systems view", 14, y, WARN);
        y += 14;
        String[] systems = {"Beam line", "Vacuum", "Magnets", "RF", "Detectors", "Control",
                "Power", "Cooling", "Cryogenics", "Shielding"};
        int[] colours = {0x8FD3FF, 0x9BE8AE, 0xFF8A65, 0xFFD166, 0xC792EA, 0xB0BEC5, 0xFFF176,
                0x81D4FA, 0xB39DDB, 0x9E9E9E};
        for (int i = 0; i < systems.length; i++) {
            int x = 14 + (i % 2) * (width - 28) / 2;
            int row = y + (i / 2) * 14;
            context.fill(x, row, x + 8, row + 8, 0xFF000000 | colours[i]);
            text(context, systems[i], x + 12, row, TEXT);
        }
        y += 14 * ((systems.length + 1) / 2) + 10;
        text(context, "Ghost colours use the same palette: place each component on its own line.",
                14, y, DIM);
        y += 12;
        text(context, "Magnet blocks: " + data.getInt("components") + " components total", 14, y,
                DIM);
    }

    private void renderDiagnostics(DrawContext context, NbtCompound data) {
        int y = 92;
        text(context, "Diagnostics", 14, y, WARN);
        y += 14;
        row(context, "Beam energy", String.format("%.3g MeV", data.getDouble("energy")), 14, y,
                width - 28, TEXT);
        y += 12;
        row(context, "Dipole field", String.format("%.3f T", data.getDouble("field")), 14, y,
                width - 28, TEXT);
        y += 12;
        row(context, "Vacuum", String.format("%.2e Pa", data.getDouble("vacuum")), 14, y,
                width - 28, TEXT);
        y += 12;
        row(context, "Dose rate",
                com.particlephysics.client.UnitFormat.dose(data.getDouble("dose")), 14, y,
                width - 28, TEXT);
        y += 12;
        row(context, "Machine", data.getBoolean("running") ? "RUNNING" : "STANDBY", 14, y,
                width - 28, data.getBoolean("running") ? GOOD : DIM);
        y += 14;
        String failure = data.getString("failure");
        if (!failure.isEmpty()) {
            text(context, "Fault: " + failure, 14, y, BAD);
            y += 12;
        }
        text(context, "Recent events", 14, y, WARN);
        y += 12;
        int shown = 0;
        for (int i = page * 10; i < ClientState.compounds(data, "log").size() && shown < 10;
                i++, shown++) {
            text(context, ClientState.compounds(data, "log").get(i).getString("text"), 14, y, DIM);
            y += 11;
        }
        button("More", 14, height - 58, 60, 18, () -> page++);
        button("Less", 80, height - 58, 60, 18, () -> page = Math.max(0, page - 1));
    }
}
