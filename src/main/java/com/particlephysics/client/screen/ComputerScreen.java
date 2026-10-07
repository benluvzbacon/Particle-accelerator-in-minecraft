package com.particlephysics.client.screen;

import com.particlephysics.client.ClientState;
import com.particlephysics.client.UnitFormat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * The control computer.
 *
 * <p>Every readout is a value the simulation computed (energy, current, lifetime, vacuum, magnetic
 * field, RF frequency and voltage, orbit distortion, temperature, cryogenics, power, collision rate,
 * luminosity, dose rate). Every control writes a setpoint that the machine ramps towards, so the
 * operator sees the transient as well as the steady state.
 */
public class ComputerScreen extends ModScreen {

    public ComputerScreen() {
        super(Text.literal("Control Computer"));
    }

    @Override
    protected void init() {
        int left = 10;
        int bottom = height - 28;
        button("Start / Stop", left, bottom, 100, 20, () -> action("run", 0));
        button("Inject", left + 104, bottom, 70, 20, () -> action("inject", 0));
        button("E-STOP", left + 178, bottom, 70, 20, () -> action("estop", 0));
        button("Valve", left + 252, bottom, 60, 20, () -> action("valve", 0));
        button("Detectors", left + 316, bottom, 80, 20, () -> action("detectors", 0));

        int column = left + 420;
        int row = 30;
        // energy ramp: the server recomputes field and RF voltage for the new energy
        String[] energyLabels = {"1 MeV", "100 MeV", "10 GeV", "1 TeV", "100 TeV"};
        double[] energyValues = {1.0, 100.0, 1.0e4, 1.0e6, 1.0e8};
        for (int i = 0; i < energyLabels.length; i++) {
            final double value = energyValues[i];
            button("Set " + energyLabels[i], column + i * 84, row, 80, 18,
                    () -> action("energy", value));
        }
        row += 22;
        jog(column, row, "dipole field", "field", 0.1, 2.0);
        jog(column, row + 20, "quadrupoles", "quad", 0.02, 0.1);
        jog(column, row + 40, "sextupoles", "sext", 0.02, 0.1);
        jog(column, row + 60, "steering", "steer", 0.05, 0.2);
        jog(column, row + 80, "RF voltage", "rfv", 0.05, 0.5);
        jog(column, row + 100, "RF frequency", "rff", 0.0005, 0.01);
        jog(column, row + 120, "RF phase", "rfp", 5.0, 30.0);
    }

    /**
     * One control channel: a button that matches the value the physics requires, and four jog
     * buttons. Pressing "match" is what a real operator does before a ramp: set the magnet to the
     * field that corresponds to the beam momentum, the cavity to the synchronous frequency, and so
     * on. Nothing here is decorative.
     */
    private void jog(int x, int y, String label, String key, double fine, double coarse) {
        button("Match " + label, x, y, 96, 18, () -> action(key, required(key)));
        button("-", x + 98, y, 20, 18, () -> step(key, -coarse));
        button("+", x + 120, y, 20, 18, () -> step(key, coarse));
        button("--", x + 142, y, 22, 18, () -> step(key, -fine));
        button("++", x + 166, y, 22, 18, () -> step(key, fine));
    }

    /** The value the current beam requires, as reported by the server. */
    private double required(String key) {
        NbtCompound data = ClientState.computer;
        if (data == null) {
            return 0.0;
        }
        return switch (key) {
            case "field" -> data.getDouble("fieldRequired");
            case "quad" -> 1.0;
            case "sext" -> 1.0;
            case "steer" -> 0.0;
            case "rfv" -> data.getDouble("rfvRequired");
            case "rff" -> data.getDouble("rfRequired");
            case "rfp" -> 0.0;
            default -> 0.0;
        };
    }

    private void step(String key, double delta) {
        NbtCompound data = ClientState.computer;
        double current = data == null ? 0.0 : data.getDouble(key);
        action(key, current + delta);
    }

    private void action(String act, double value) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", "computer");
        nbt.putString("act", act);
        nbt.putDouble("value", value);
        NbtCompound contextNbt = context();
        if (contextNbt != null) {
            for (String k : contextNbt.getKeys()) {
                nbt.put(k, contextNbt.get(k));
            }
        }
        com.particlephysics.net.ModNetworking.sendAction(nbt);
    }

    @Override
    protected NbtCompound context() {
        NbtCompound data = ClientState.computer;
        if (data == null) {
            return null;
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("x", data.getInt("x"));
        nbt.putInt("y", data.getInt("y"));
        nbt.putInt("z", data.getInt("z"));
        return nbt;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        NbtCompound data = ClientState.computer;
        if (data == null) {
            text(context, "Waiting for the control computer...", 10, 40, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        int leftWidth = 400;
        panel(context, 8, 22, leftWidth, height - 56);
        panel(context, 414, 22, width - 422, height - 56);

        String title = data.getString("site") + (data.getBoolean("running") ? "   [RUNNING]"
                : "   [STANDBY]");
        text(context, title, 14, 26, data.getBoolean("running") ? GOOD : WARN);

        int y = 40;
        y = section(context, "Beam", y);
        y = readout(context, "Particle", data.getString("speciesSymbol") + "   "
                + data.getString("species"), y);
        row(context, "Energy", UnitFormat.energy(data.getDouble("energy")), 14, y, leftWidth - 12,
                TEXT);
        y += 11;
        row(context, "Target energy", UnitFormat.energy(data.getDouble("targetEnergy")), 14, y,
                leftWidth - 12, DIM);
        y += 11;
        row(context, "Momentum", String.format("%.4g MeV/c", data.getDouble("momentum")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Current", UnitFormat.current(data.getDouble("current")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Particles", String.format("%.3g", data.getDouble("intensity")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "beta / gamma", String.format("%.6f / %.3f", data.getDouble("beta"),
                data.getDouble("gamma")), 14, y, leftWidth - 12, TEXT);
        y += 11;
        row(context, "Beam lifetime", UnitFormat.time(data.getDouble("lifetime")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Loss per step", String.format("%.3e", data.getDouble("lossFraction")), 14,
                y, leftWidth - 12, data.getDouble("lossFraction") > 1.0e-3 ? BAD : TEXT);
        y += 11;
        row(context, "Peak energy reached", UnitFormat.energy(data.getDouble("peakEnergy")), 14,
                y, leftWidth - 12, GOOD);
        y += 13;

        y = section(context, "Magnets", y);
        row(context, "Dipole field", String.format("%.4f T", data.getDouble("field")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Field required", String.format("%.4f T", data.getDouble("fieldRequired")),
                14, y, leftWidth - 12,
                Math.abs(data.getDouble("field") - data.getDouble("fieldRequired")) > 1.0e-3
                        ? WARN : GOOD);
        y += 11;
        row(context, "Orbit distortion", String.format("%.2f mm", data.getDouble("orbit")), 14, y,
                leftWidth - 12, Math.abs(data.getDouble("orbit")) > 20.0 ? BAD : TEXT);
        y += 11;
        row(context, "Quad / sext scale", String.format("%.3f / %.3f", data.getDouble("quad"),
                data.getDouble("sext")), 14, y, leftWidth - 12, TEXT);
        y += 11;
        row(context, "Steering trim", String.format("%.3f deg", data.getDouble("steering")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Magnets", data.getBoolean("superconducting") ? "superconducting"
                : "resistive", 14, y, leftWidth - 12, TEXT);
        y += 11;
        if (data.getBoolean("quench")) {
            text(context, "QUENCH: magnets lost superconductivity", 14, y, BAD);
            y += 11;
        }
        y += 2;

        y = section(context, "Radio frequency", y);
        row(context, "Cavity voltage", String.format("%.4f MV", data.getDouble("rfv")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Frequency", String.format("%.6f MHz", data.getDouble("rff")), 14, y,
                leftWidth - 12, data.getBoolean("rfFault") ? BAD : TEXT);
        y += 11;
        row(context, "Synchronous freq.", String.format("%.6f MHz", data.getDouble("rfRequired")),
                14, y, leftWidth - 12, DIM);
        y += 11;
        row(context, "Phase", String.format("%.1f deg", data.getDouble("rfPhase")), 14, y,
                leftWidth - 12, TEXT);
        y += 13;

        y = section(context, "Vacuum, power and cryogenics", y);
        row(context, "Pressure", UnitFormat.pressure(data.getDouble("vacuum")), 14, y,
                leftWidth - 12, data.getBoolean("vacuumFault") ? WARN : GOOD);
        y += 11;
        row(context, "Pumping speed", String.format("%.2f m3/s", data.getDouble("pumpSpeed")), 14,
                y, leftWidth - 12, TEXT);
        y += 11;
        row(context, "Gas lifetime", UnitFormat.time(data.getDouble("gasLifetime")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Power", UnitFormat.power(data.getDouble("powerSupply")) + " / "
                + UnitFormat.power(data.getDouble("powerDemand")), 14, y, leftWidth - 12,
                data.getDouble("powerSupply") < data.getDouble("powerDemand") ? BAD : GOOD);
        y += 11;
        row(context, "Magnet temperature", String.format("%.1f C", data.getDouble("temperature")),
                14, y, leftWidth - 12, data.getDouble("temperature") > 45.0 ? BAD : TEXT);
        y += 11;
        row(context, "Cooling capacity", UnitFormat.power(data.getDouble("coolant")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Cryostat", String.format("%.1f K", data.getDouble("cryoTemperature")) + "  ("
                + String.format("%.2f", data.getDouble("helium")) + " L He)", 14, y, leftWidth - 12,
                data.getBoolean("cryoReady") ? GOOD : WARN);
        y += 13;

        y = section(context, "Collisions and radiation", y);
        row(context, "Collision rate", UnitFormat.rate(data.getDouble("collisionRate")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Total collisions", Long.toString(data.getLong("collisions")), 14, y,
                leftWidth - 12, TEXT);
        y += 11;
        row(context, "Luminosity", String.format("%.3e cm-2 s-1", data.getDouble("luminosity")),
                14, y, leftWidth - 12, TEXT);
        y += 11;
        row(context, "Dose rate", UnitFormat.dose(data.getDouble("dose")), 14, y, leftWidth - 12,
                data.getDouble("dose") > 10.0 ? BAD : TEXT);
        y += 11;
        row(context, "Construction", data.getInt("builtOk") + " ok / " + data.getInt("errors")
                + " errors", 14, y, leftWidth - 12,
                data.getInt("errors") > 0 ? BAD : GOOD);
        y += 11;
        if (!data.getString("failure").isEmpty()) {
            text(context, "Fault: " + data.getString("failure"), 14, y, BAD);
        }

        // --- right column: history and log ----------------------------------------------------
        int x = 420;
        int chartY = 40;
        text(context, "Beam energy (last 3 minutes)", x, chartY - 10, WARN);
        chart(context, ClientState.doubles(data, "historyEnergy"), x, chartY, width - 436, 40,
                0x80D3FF);
        chartY += 62;
        text(context, "Collision rate", x, chartY - 10, WARN);
        chart(context, ClientState.doubles(data, "historyRate"), x, chartY, width - 436, 40,
                0xFFD166);
        chartY += 62;
        text(context, "Vacuum pressure", x, chartY - 10, WARN);
        chart(context, ClientState.doubles(data, "historyVacuum"), x, chartY, width - 436, 40,
                0x9BE8AE, true);
        chartY += 54;
        text(context, "Log", x, chartY, WARN);
        chartY += 12;
        for (NbtCompound entry : ClientState.compounds(data, "log")) {
            text(context, entry.getString("text"), x, chartY, DIM);
            chartY += 10;
            if (chartY > height - 40) {
                break;
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private int section(DrawContext context, String name, int y) {
        text(context, name, 14, y, 0x7FD8FF);
        return y + 11;
    }

    private int readout(DrawContext context, String label, String value, int y) {
        row(context, label, value, 14, y, 388, TEXT);
        return y + 11;
    }

    private void chart(DrawContext context, double[] values, int x, int y, int width, int height,
                       int colour) {
        chart(context, values, x, y, width, height, colour, false);
    }

    private void chart(DrawContext context, double[] values, int x, int y, int width, int height,
                       int colour, boolean logarithmic) {
        context.fill(x, y, x + width, y + height, 0x60000000);
        if (values.length < 2) {
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (double value : values) {
            double v = logarithmic ? Math.log10(Math.max(1.0e-12, value)) : value;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (max - min < 1.0e-9) {
            max = min + 1.0;
        }
        for (int i = 0; i < values.length - 1; i++) {
            double v0 = logarithmic ? Math.log10(Math.max(1.0e-12, values[i])) : values[i];
            double v1 = logarithmic ? Math.log10(Math.max(1.0e-12, values[i + 1])) : values[i + 1];
            int x0 = x + i * width / Math.max(1, values.length - 1);
            int x1 = x + (i + 1) * width / Math.max(1, values.length - 1);
            int y0 = y + height - (int) ((v0 - min) / (max - min) * height);
            int y1 = y + height - (int) ((v1 - min) / (max - min) * height);
            context.fill(x0, Math.min(y0, y1), x1 + 1, Math.max(y0, y1) + 1, 0xFF000000 | colour);
        }
    }
}
