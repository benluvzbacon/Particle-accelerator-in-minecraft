package com.particlephysics.client.screen;

import com.particlephysics.client.ClientState;
import com.particlephysics.client.UnitFormat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * Detector readout: particle identification (energy, momentum, charge, mass, velocity, lifetime),
 * event rate, multiplicity, integrated energy and the last event the detector saw.
 */
public class DetectorScreen extends ModScreen {
    public DetectorScreen() {
        super(Text.literal("Detector"));
    }

    @Override
    protected void init() {
        button("Reset counters", 10, height - 28, 110, 20, () -> {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("action", "detector");
            nbt.putString("act", "reset");
            NbtCompound context = context();
            if (context != null) {
                for (String key : context.getKeys()) {
                    nbt.put(key, context.get(key));
                }
            }
            com.particlephysics.net.ModNetworking.sendAction(nbt);
        });
        button("Close", width - 70, height - 28, 60, 20, this::close);
    }

    @Override
    protected NbtCompound context() {
        NbtCompound data = ClientState.detector;
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
        drawBackgroundOnce(context, mouseX, mouseY, delta);
        NbtCompound data = ClientState.detector;
        if (data == null) {
            text(context, "No detector connected", 10, 40, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        panel(context, 8, 22, width - 16, height - 56);
        text(context, data.getString("title") + "   (" + data.getString("site") + ")", 14, 26,
                WARN);

        int left = 14;
        int y = 42;
        text(context, "Particle identification", left, y, 0x7FD8FF);
        y += 13;
        row(context, "Particle", data.getString("particle") + " " + data.getString("symbol"),
                left, y, 220, TEXT);
        y += 11;
        row(context, "Kinetic energy", UnitFormat.energy(data.getDouble("energy")), left, y, 220,
                TEXT);
        y += 11;
        row(context, "Momentum", String.format("%.4g MeV/c", data.getDouble("momentum")), left, y,
                220, TEXT);
        y += 11;
        row(context, "Rest mass", UnitFormat.energy(data.getDouble("mass")), left, y, 220, TEXT);
        y += 11;
        row(context, "Charge", String.format("%+.0f e", data.getDouble("charge")), left, y, 220,
                TEXT);
        y += 11;
        row(context, "Velocity", String.format("%.8f c (beta)", data.getDouble("velocity")), left,
                y, 220, TEXT);
        y += 11;
        double lifetime = data.getDouble("lifetime");
        row(context, "Lifetime (lab)", UnitFormat.time(lifetime), left, y, 220, TEXT);
        y += 16;

        text(context, "Event data", left, y, 0x7FD8FF);
        y += 13;
        row(context, "Event rate", UnitFormat.rate(data.getDouble("rate")), left, y, 220, TEXT);
        y += 11;
        row(context, "Events recorded", Long.toString(data.getLong("events")), left, y, 220, TEXT);
        y += 11;
        row(context, "Charged tracks", Long.toString(data.getLong("charged")), left, y, 220, TEXT);
        y += 11;
        row(context, "Muon hits", Long.toString(data.getLong("muons")), left, y, 220, TEXT);
        y += 11;
        row(context, "Multiplicity (last)", String.format("%.0f", data.getDouble("multiplicity")),
                left, y, 220, TEXT);
        y += 11;
        row(context, "Last event energy", String.format("%.3f GeV", data.getDouble("lastEnergy")),
                left, y, 220, TEXT);
        y += 11;
        row(context, "Integrated energy", String.format("%.3f GeV", data.getDouble("integrated")),
                left, y, 220, TEXT);
        y += 16;

        text(context, "Last event", left, y, 0x7FD8FF);
        y += 13;
        text(context, data.getString("lastEvent"), left, y, GOOD);

        int right = 260;
        int ry = 42;
        text(context, "What this detector measures", right, ry, 0x7FD8FF);
        ry += 13;
        for (String line : java.util.List.of(
                "Tracking detector: charged particle tracks, momentum from curvature.",
                "Calorimeter: total energy of the shower, electromagnetic + hadronic.",
                "Scintillation detector: fast timing and charged particle counting.",
                "Cherenkov detector: velocity threshold, separates pions from kaons.",
                "Muon detector: penetrating charged particles behind the shielding.",
                "Radiation detector: ambient dose rate in the tunnel.",
                "Collision chamber: the interaction point itself, sqrt(s) and multiplicity.")) {
            text(context, line, right, ry, DIM);
            ry += 11;
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
