package com.particlephysics.physics;

import java.util.Locale;

/**
 * Physical constants and unit helpers.
 *
 * <p>All energies used by the simulation are expressed in MeV (mega electron volts) unless the
 * method name says otherwise, momenta in MeV/c, masses in MeV/c^2, times in seconds and lengths in
 * metres. One Minecraft block is treated as exactly one metre, which keeps the numbers
 * scientifically meaningful (a 16 block radius synchrotron really is a 100 m circumference ring).
 */
public final class Units {
    private Units() {
    }

    // --- Fundamental constants (SI) -------------------------------------------------------------
    public static final double C = 299_792_458.0;                 // speed of light [m/s]
    public static final double ELEMENTARY_CHARGE = 1.602176634e-19; // [C]
    public static final double EV_TO_JOULE = ELEMENTARY_CHARGE;
    public static final double AMU_TO_MEV = 931.49410242;         // atomic mass unit in MeV/c^2
    public static final double H_BAR = 1.054571817e-34;           // reduced Planck constant [J s]
    public static final double RYDBERG_EV = 13.605693;            // [eV]
    public static final double BOLTZMANN = 1.380649e-23;          // [J/K]

    /** Classical electron radius [m]. */
    public static final double ELECTRON_RADIUS = 2.8179403262e-15;
    /** Classical proton radius [m]. */
    public static final double PROTON_RADIUS = 1.534698e-18;
    /** Permittivity of free space. */
    public static final double EPSILON_0 = 8.8541878128e-12;

    // --- Particle masses [MeV/c^2] --------------------------------------------------------------
    public static final double ELECTRON_MASS = 0.51099895000;
    public static final double MUON_MASS = 105.6583755;
    public static final double PROTON_MASS = 938.27208816;
    public static final double NEUTRON_MASS = 939.56542052;
    public static final double PION_CHARGED_MASS = 139.57039;
    public static final double PION_NEUTRAL_MASS = 134.9768;
    public static final double KAON_CHARGED_MASS = 493.677;
    public static final double KAON_NEUTRAL_MASS = 497.611;
    public static final double DEUTERON_MASS = 1875.61294257;
    public static final double TRITON_MASS = 2808.92113298;
    public static final double HELIUM3_MASS = 2808.39160743;
    public static final double ALPHA_MASS = 3727.3794066;

    /** Fine structure constant. */
    public static final double ALPHA = 1.0 / 137.035999084;

    /** Avogadro constant [1/mol]. */
    public static final double AVOGADRO = 6.02214076e23;

    /** Conversion factor for magnetic rigidity: B[rho] = p / (0.299792458 * q) with p in GeV/c. */
    public static final double RIGIDITY_FACTOR = 0.299792458;

    // --- Display helpers ------------------------------------------------------------------------

    /** Formats an energy given in MeV using keV/MeV/GeV/TeV/PeV as appropriate. */
    public static String energy(double mev) {
        double abs = Math.abs(mev);
        if (abs >= 1.0e9) {
            return trim(mev / 1.0e9) + " PeV";
        }
        if (abs >= 1.0e6) {
            return trim(mev / 1.0e6) + " TeV";
        }
        if (abs >= 1.0e3) {
            return trim(mev / 1.0e3) + " GeV";
        }
        if (abs >= 1.0) {
            return trim(mev) + " MeV";
        }
        if (abs >= 1.0e-3) {
            return trim(mev * 1.0e3) + " keV";
        }
        return trim(mev * 1.0e6) + " eV";
    }

    /** Formats a momentum given in MeV/c. */
    public static String momentum(double mevPerC) {
        double abs = Math.abs(mevPerC);
        if (abs >= 1.0e9) {
            return trim(mevPerC / 1.0e9) + " PeV/c";
        }
        if (abs >= 1.0e6) {
            return trim(mevPerC / 1.0e6) + " TeV/c";
        }
        if (abs >= 1.0e3) {
            return trim(mevPerC / 1.0e3) + " GeV/c";
        }
        if (abs >= 1.0) {
            return trim(mevPerC) + " MeV/c";
        }
        return trim(mevPerC * 1.0e3) + " keV/c";
    }

    /** Formats a mass given in MeV/c^2 showing both the energy unit and the atomic mass unit. */
    public static String mass(double mevPerC2) {
        double u = mevPerC2 / AMU_TO_MEV;
        if (u >= 1.0) {
            return String.format(Locale.ROOT, "%.3f u (%s)", u, energy(mevPerC2) + "/c\u00b2");
        }
        return energy(mevPerC2) + "/c\u00b2";
    }

    /** Formats a pressure given in Pascal with Torr in brackets (1 Torr = 133.322 Pa). */
    public static String pressure(double pascal) {
        double torr = pascal / 133.322368;
        if (pascal >= 1.0) {
            return String.format(Locale.ROOT, "%.2f Pa (%.3f Torr)", pascal, torr);
        }
        if (pascal >= 1.0e-3) {
            return String.format(Locale.ROOT, "%.3e Pa (%.3e Torr)", pascal, torr);
        }
        return String.format(Locale.ROOT, "%.2e Pa (%.2e Torr)", pascal, torr);
    }

    /** Formats a velocity as a fraction of the speed of light plus the SI value. */
    public static String velocity(double beta) {
        return String.format(Locale.ROOT, "%.6f c (%.3e m/s)", beta, beta * C);
    }

    /** Formats a time span in seconds using us/ms/s/min/h/d. */
    public static String time(double seconds) {
        if (seconds < 1.0e-9) {
            return String.format(Locale.ROOT, "%.2f ns", seconds * 1.0e9);
        }
        if (seconds < 1.0e-6) {
            return String.format(Locale.ROOT, "%.2f us", seconds * 1.0e6);
        }
        if (seconds < 1.0e-3) {
            return String.format(Locale.ROOT, "%.2f ms", seconds * 1.0e3);
        }
        if (seconds < 1.0) {
            return String.format(Locale.ROOT, "%.3f s", seconds);
        }
        if (seconds < 60.0) {
            return String.format(Locale.ROOT, "%.1f s", seconds);
        }
        if (seconds < 3600.0) {
            return String.format(Locale.ROOT, "%.1f min", seconds / 60.0);
        }
        if (seconds < 86400.0) {
            return String.format(Locale.ROOT, "%.1f h", seconds / 3600.0);
        }
        return String.format(Locale.ROOT, "%.1f y", seconds / 3.15569e7);
    }

    /** Formats a power given in kilowatts. */
    public static String power(double kilowatts) {
        if (Math.abs(kilowatts) >= 1.0e3) {
            return String.format(Locale.ROOT, "%.3f MW", kilowatts / 1.0e3);
        }
        return String.format(Locale.ROOT, "%.2f kW", kilowatts);
    }

    /** Formats a magnetic field in tesla. */
    public static String field(double tesla) {
        return String.format(Locale.ROOT, "%.4f T", tesla);
    }

    /** Formats a radiation dose in sievert. */
    public static String dose(double sievert) {
        if (sievert >= 1.0) {
            return String.format(Locale.ROOT, "%.3f Sv", sievert);
        }
        if (sievert >= 1.0e-3) {
            return String.format(Locale.ROOT, "%.3f mSv", sievert * 1.0e3);
        }
        return String.format(Locale.ROOT, "%.3f uSv", sievert * 1.0e6);
    }

    /** Formats activity in becquerel with an appropriate prefix. */
    public static String activity(double becquerel) {
        if (becquerel >= 1.0e12) {
            return String.format(Locale.ROOT, "%.3f TBq", becquerel / 1.0e12);
        }
        if (becquerel >= 1.0e9) {
            return String.format(Locale.ROOT, "%.3f GBq", becquerel / 1.0e9);
        }
        if (becquerel >= 1.0e6) {
            return String.format(Locale.ROOT, "%.3f MBq", becquerel / 1.0e6);
        }
        if (becquerel >= 1.0e3) {
            return String.format(Locale.ROOT, "%.3f kBq", becquerel / 1.0e3);
        }
        return String.format(Locale.ROOT, "%.3f Bq", becquerel);
    }

    public static String percent(double fraction) {
        return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
    }

    public static String trim(double value) {
        double abs = Math.abs(value);
        if (abs == 0) {
            return "0";
        }
        if (abs >= 1000) {
            return String.format(Locale.ROOT, "%.4g", value);
        }
        if (abs >= 1) {
            return String.format(Locale.ROOT, "%.3f", value);
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /** Converts a kinetic energy in MeV into the equivalent temperature in kelvin. */
    public static double energyToKelvin(double mev) {
        return mev * 1.0e6 * EV_TO_JOULE / BOLTZMANN;
    }

    /** Converts kelvin to MeV. */
    public static double kelvinToEnergy(double kelvin) {
        return kelvin * BOLTZMANN / (EV_TO_JOULE * 1.0e6);
    }
}
