package com.particlephysics.client;

import java.util.Locale;

/** Physical units, the way a control room displays them. */
public final class UnitFormat {
    private UnitFormat() {
    }

    private static final String[] ENERGY = {"eV", "keV", "MeV", "GeV", "TeV", "PeV"};

    /** Formats an energy given in MeV with the right SI prefix. */
    public static String energy(double meV) {
        double value = Math.abs(meV);
        int index = indexOf(value, 1.0e-6, 6);
        double scaled = meV / Math.pow(1.0e3, index - 2 - 3 + 3);
        // energies arrive in MeV: index 2 is MeV
        scaled = meV / Math.pow(1000.0, index - 2);
        String unit = ENERGY[Math.max(0, Math.min(ENERGY.length - 1, index))];
        return String.format(Locale.ROOT, "%.3f %s", scaled, unit);
    }

    private static int indexOf(double value, double floor, int max) {
        int index = 0;
        double v = Math.max(1.0e-12, value) * 1.0e0;
        // MeV is index 2, so start from there and step by 1000
        index = 2;
        while (index < max && v >= 1000.0) {
            v /= 1000.0;
            index++;
        }
        while (index > 0 && v < 1.0) {
            v *= 1000.0;
            index--;
        }
        return index;
    }

    /** Power in kW or MW. */
    public static String power(double kW) {
        if (Math.abs(kW) >= 1000.0) {
            return String.format(Locale.ROOT, "%.2f MW", kW / 1000.0);
        }
        return String.format(Locale.ROOT, "%.1f kW", kW);
    }

    /** Pressure in Pa, mbar and Torr, as a vacuum technician would read it. */
    public static String pressure(double pascal) {
        double torr = pascal * 7.50061683e-3;
        if (pascal >= 1.0) {
            return String.format(Locale.ROOT, "%.3g Pa (%.3g Torr)", pascal, torr);
        }
        return String.format(Locale.ROOT, "%.2e Pa (%.2e mbar, %.2e Torr)", pascal, pascal / 100.0,
                torr);
    }

    /** Current in A, mA, uA, nA. */
    public static String current(double amperes) {
        double value = Math.abs(amperes);
        if (value >= 1.0) {
            return String.format(Locale.ROOT, "%.3f A", amperes);
        }
        if (value >= 1.0e-3) {
            return String.format(Locale.ROOT, "%.3f mA", amperes * 1.0e3);
        }
        if (value >= 1.0e-6) {
            return String.format(Locale.ROOT, "%.3f uA", amperes * 1.0e6);
        }
        return String.format(Locale.ROOT, "%.3f nA", amperes * 1.0e9);
    }

    public static String dose(double microSvPerHour) {
        if (microSvPerHour >= 1.0e6) {
            return String.format(Locale.ROOT, "%.2f Sv/h", microSvPerHour / 1.0e6);
        }
        if (microSvPerHour >= 1.0e3) {
            return String.format(Locale.ROOT, "%.2f mSv/h", microSvPerHour / 1.0e3);
        }
        return String.format(Locale.ROOT, "%.2f uSv/h", microSvPerHour);
    }

    /** Rate in Hz / kHz / MHz. */
    public static String rate(double hertz) {
        if (hertz >= 1.0e6) {
            return String.format(Locale.ROOT, "%.2f MHz", hertz / 1.0e6);
        }
        if (hertz >= 1.0e3) {
            return String.format(Locale.ROOT, "%.2f kHz", hertz / 1.0e3);
        }
        return String.format(Locale.ROOT, "%.2f Hz", hertz);
    }

    /** Time in s / min / h / d / a. */
    public static String time(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) {
            return "stable";
        }
        if (seconds < 1.0) {
            return String.format(Locale.ROOT, "%.0f ms", seconds * 1000.0);
        }
        if (seconds < 120.0) {
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        if (seconds < 7200.0) {
            return String.format(Locale.ROOT, "%.1f min", seconds / 60.0);
        }
        if (seconds < 172800.0) {
            return String.format(Locale.ROOT, "%.1f h", seconds / 3600.0);
        }
        return String.format(Locale.ROOT, "%.1f d", seconds / 86400.0);
    }
}
