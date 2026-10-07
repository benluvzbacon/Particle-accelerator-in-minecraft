package com.particlephysics.client.screen;

import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.client.ClientState;
import com.particlephysics.client.UnitFormat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * Panel of a single component: source, injector, target station, valve, pump, gauge, cavity,
 * detector, power supply, cooling or cryogenic unit. The controls are the ones that component
 * really has, and the readouts are the values the simulation wrote into it.
 */
public class MachineScreen extends ModScreen {
    public MachineScreen() {
        super(Text.literal("Machine"));
    }

    @Override
    protected void init() {
        NbtCompound data = ClientState.machine;
        if (data == null) {
            return;
        }
        MachineKind kind = MachineKind.byId(data.getString("kind"));
        int bottom = height - 28;
        if (kind == MachineKind.RF_CAVITY) {
            button("Voltage -", 10, bottom, 70, 20, () -> tune("rfv", -0.1));
            button("Voltage +", 84, bottom, 70, 20, () -> tune("rfv", 0.1));
            button("Phase -", 158, bottom, 60, 20, () -> tune("rfp", -10.0));
            button("Phase +", 222, bottom, 60, 20, () -> tune("rfp", 10.0));
        }
        if (kind == MachineKind.STEERING_MAGNET) {
            button("Trim -", 10, bottom, 60, 20, () -> tune("steer", -0.1));
            button("Trim +", 74, bottom, 60, 20, () -> tune("steer", 0.1));
        }
        if (kind == MachineKind.VACUUM_VALVE) {
            button("Open / close", 10, bottom, 100, 20, () -> {
                NbtCompound nbt = new NbtCompound();
                nbt.putString("action", "machine");
                nbt.putString("act", "valve");
                nbt.putBoolean("value", !(ClientState.machine != null
                        && ClientState.machine.getBoolean("active")));
                NbtCompound context = context();
                if (context != null) {
                    for (String key : context.getKeys()) {
                        nbt.put(key, context.get(key));
                    }
                }
                com.particlephysics.net.ModNetworking.sendAction(nbt);
            });
        }
        button("Close", width - 70, bottom, 60, 20, this::close);
    }

    private void tune(String act, double delta) {
        NbtCompound data = ClientState.machine;
        double current = data == null ? 0.0 : data.getDouble("setpointA");
        if ("rfp".equals(act)) {
            current = data == null ? 0.0 : data.getDouble("setpointB");
        }
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", "machine");
        nbt.putString("act", act);
        nbt.putDouble("value", current + delta);
        NbtCompound context = context();
        if (context != null) {
            for (String key : context.getKeys()) {
                nbt.put(key, context.get(key));
            }
        }
        com.particlephysics.net.ModNetworking.sendAction(nbt);
    }

    @Override
    protected NbtCompound context() {
        NbtCompound data = ClientState.machine;
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
        NbtCompound data = ClientState.machine;
        panel(context, 8, 22, width - 16, height - 56);
        if (data == null) {
            text(context, "No machine selected", 14, 30, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        MachineKind kind = MachineKind.byId(data.getString("kind"));
        text(context, data.getString("title"), 14, 26, 0x7FD8FF);
        int y = 42;
        if (kind != null) {
            text(context, "System: " + kind.system().displayName(), 14, y, DIM);
            y += 11;
            text(context, "Nominal capability: " + String.format("%.3g", kind.capacity()) + " "
                    + capabilityUnit(kind), 14, y, DIM);
            y += 13;
        }
        row(context, "State", data.getBoolean("active") ? "active" : "idle", 14, y, width - 28,
                data.getBoolean("active") ? GOOD : DIM);
        y += 11;
        row(context, "Stored level", String.format("%.3f", data.getDouble("level")), 14, y,
                width - 28, TEXT);
        y += 11;
        row(context, "Measured", String.format("%.4g", data.getDouble("measured")), 14, y,
                width - 28, TEXT);
        y += 11;
        row(context, "Setpoint", String.format("%.4g / %.4g", data.getDouble("setpointA"),
                data.getDouble("setpointB")), 14, y, width - 28, TEXT);
        y += 11;
        row(context, "Temperature", String.format("%.1f C", data.getDouble("temperature")), 14, y,
                width - 28, data.getDouble("temperature") > 45.0 ? BAD : TEXT);
        y += 13;

        if (data.getBoolean("inMachine")) {
            text(context, "Machine: " + data.getString("site") + (data.getBoolean("running")
                    ? "  [RUNNING]" : "  [STANDBY]"), 14, y, 0x7FD8FF);
            y += 13;
            row(context, "Beam", UnitFormat.energy(data.getDouble("energy")), 14, y, width - 28,
                    TEXT);
            y += 11;
            row(context, "Beam current", UnitFormat.current(data.getDouble("current")), 14, y,
                    width - 28, TEXT);
            y += 11;
            row(context, "Vacuum", UnitFormat.pressure(data.getDouble("vacuum")), 14, y,
                    width - 28, TEXT);
            y += 11;
            row(context, "RF", String.format("%.4f MV @ %.6f MHz", data.getDouble("rfv"),
                    data.getDouble("rff")), 14, y, width - 28, TEXT);
            y += 11;
            row(context, "Synchronous freq.", String.format("%.6f MHz", data.getDouble("rfRequired")),
                    14, y, width - 28, DIM);
            y += 11;
            row(context, "Dipole field", String.format("%.4f T", data.getDouble("field")), 14, y,
                    width - 28, TEXT);
            y += 11;
            row(context, "Dose rate here", UnitFormat.dose(data.getDouble("dose")), 14, y,
                    width - 28, data.getDouble("dose") > 10.0 ? BAD : TEXT);
            y += 11;
            if (!data.getString("failure").isEmpty()) {
                text(context, "Fault: " + data.getString("failure"), 14, y, BAD);
                y += 11;
            }
        }
        y += 6;
        text(context, "Contents", 14, y, 0x7FD8FF);
        y += 12;
        for (NbtCompound item : ClientState.compounds(data, "items")) {
            text(context, item.getString("name") + " x" + item.getInt("count"), 14, y, TEXT);
            y += 11;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private String capabilityUnit(MachineKind kind) {
        return switch (kind) {
            case DIPOLE_MAGNET, SUPERCONDUCTING_DIPOLE -> "T";
            case QUADRUPOLE_MAGNET -> "T/m";
            case SEXTUPOLE_MAGNET -> "T/m2";
            case STEERING_MAGNET -> "T*m";
            case RF_CAVITY -> "MV";
            case VACUUM_PUMP -> "m3/s";
            case POWER_SUPPLY, POWER_CABLE, COOLING_UNIT, CRYOGENIC_UNIT -> "kW";
            default -> "";
        };
    }

    private double progressValue(double value) {
        return value;
    }
}
