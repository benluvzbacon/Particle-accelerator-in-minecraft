package com.particlephysics.physics;

/**
 * Vacuum physics: gas density, pumping, outgassing and beam-gas scattering.
 *
 * <p>The pressure of the beam pipe is not a number the player can simply set: it follows from the
 * volume of the vacuum system, the pumping speed of the installed pumps and the outgassing of the
 * chamber walls. Beam-gas scattering then limits the beam lifetime and slowly blows the emittance
 * up, exactly as in real accelerators.
 */
public final class Vacuum {
    private Vacuum() {
    }

    /** Room temperature used for the ideal gas law [K]. */
    public static final double TEMPERATURE = 293.15;

    /** Boltzmann constant in Pa*m^3/K. */
    private static final double K_B = 1.380649e-23;

    /** Gas molecules per cubic metre at the given pressure. */
    public static double numberDensity(double pressurePa) {
        return pressurePa / (K_B * TEMPERATURE);
    }

    /** Pressure of a gas with the given number density. */
    public static double pressureFromDensity(double moleculesPerM3) {
        return moleculesPerM3 * K_B * TEMPERATURE;
    }

    /** Speed of a thermal gas molecule (light species) in m/s. */
    public static double thermalSpeed(double molarMassKg) {
        return Math.sqrt(8.0 * K_B * TEMPERATURE / (Math.PI * molarMassKg));
    }

    /**
     * Total beam-gas cross section per gas molecule for a beam of the given kinetic energy.
     *
     * <p>Three contributions are modelled, all with their correct energy scaling:
     * <ul>
     *   <li>nuclear elastic scattering, roughly geometric and energy independent, sigma ~ A^(2/3)
     *       (a few barns, i.e. 1e-28 m^2, per nucleus),</li>
     *   <li>Coulomb scattering, which dominates at low energy and falls off as 1/gamma,</li>
     *   <li>bremsstrahlung off the residual gas, which grows logarithmically with energy and is
     *       the reason why high energy machines need ultra high vacuum.</li>
     * </ul>
     *
     * @param kineticEnergyMeV beam kinetic energy
     * @param restEnergyMeV    beam particle rest energy
     * @param chargeState      projectile charge in units of e
     * @param gasZ             atomic number of the residual gas (mostly hydrogen and carbon)
     * @param gasA             mass number of the residual gas
     * @return cross section in m^2
     */
    public static double gasCrossSection(double kineticEnergyMeV, double restEnergyMeV,
                                         double chargeState, double gasZ, double gasA) {
        double gamma = Relativity.gammaFromKinetic(kineticEnergyMeV, restEnergyMeV);
        double beta = Relativity.betaFromGamma(gamma);

        // Nuclear elastic: geometric cross section of the gas nucleus plus the strong interaction
        // range for a light projectile.
        double nuclear = Math.PI * Math.pow(1.2e-15 * Math.cbrt(gasA), 2) + 3.0e-30;

        // Coulomb scattering: sigma ~ Z_p^2 Z_g^2 (1 - beta^2) / beta^4, normalised so that the
        // value for a 1 MeV proton on hydrogen is of the order of 1e-21 m^2.
        double coulomb = 1.0e-22 * Math.pow(chargeState * chargeState * gasZ * gasZ, 1.0)
                * (1.0 / (gamma * gamma)) / Math.pow(Math.max(beta, 1.0e-3), 4.0);
        coulomb = Math.min(coulomb, 1.0e-19);

        // Bremsstrahlung: sigma ~ alpha^3 Z^2 r_e^2 ln(...), important for electrons and muons.
        double logFactor = Math.log(Math.max(2.0, gamma)) + 0.5;
        double brem = 4.0 * Units.ALPHA * Math.pow(Units.ELECTRON_RADIUS, 2)
                * gasZ * (gasZ + 1.0) * logFactor * Math.max(1.0, gamma) * 0.02;

        return nuclear + coulomb + brem;
    }

    /**
     * Beam lifetime against beam-gas scattering.
     *
     * @param pressurePa   residual gas pressure
     * @param crossSection beam-gas cross section in m^2
     * @param beta         beam velocity in units of c
     * @return lifetime in seconds (infinite when there is no gas)
     */
    public static double beamLifetime(double pressurePa, double crossSection, double beta) {
        double density = numberDensity(Math.max(1.0e-12, pressurePa));
        double rate = density * crossSection * Math.max(1.0e-4, beta) * Units.C;
        if (rate <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0 / rate;
    }

    /**
     * RMS multiple scattering angle (projected) from a path length of gas.
     *
     * <p>Uses the highland-like parameterisation  theta_rms = 13.6 MeV/(beta p) * sqrt(x/X0).
     *
     * @param pressurePa pressure of the gas
     * @param pathMetres path length through the gas
     * @param kineticMeV beam kinetic energy
     * @param restMeV    beam rest energy
     * @return projected scattering angle in radians
     */
    public static double multipleScatteringAngle(double pressurePa, double pathMetres,
                                                 double kineticMeV, double restMeV) {
        double beta = Relativity.beta(kineticMeV, restMeV);
        double p = Relativity.momentumFromKinetic(kineticMeV, restMeV);
        double thickness = numberDensity(pressurePa) * pathMetres * 1.0e-28;
        double xOverX0 = Math.max(1.0e-12, thickness);
        return 13.6e-3 / (Math.max(1.0e-3, beta * p)) * Math.sqrt(xOverX0)
                * Math.min(10.0, Math.log(xOverX0 * 1.0e8 + 2.0));
    }

    /**
     * Equilibrium pressure of a pumped vacuum system.
     *
     * @param volumeM3       volume of the chamber [m^3]
     * @param surfaceM2      internal surface area [m^2]
     * @param pumps          total pumping speed [m^3/s]
     * @param leakRatePaM3S  leaks and outgassing [Pa*m^3/s]
     * @param previousPa     pressure at the previous step
     * @param dt             time step [s]
     * @return the new pressure in Pa
     */
    public static double pumpStep(double volumeM3, double surfaceM2, double pumps,
                                  double leakRatePaM3S, double previousPa, double dt) {
        double volume = Math.max(0.05, volumeM3);
        double outgassing = leakRatePaM3S + 1.3e-6 * surfaceM2 / Math.max(0.2, previousPa * 1.0e4 + 1.0);
        double removal = pumps * previousPa;
        double dp = (outgassing - removal) / volume;
        double next = previousPa + dp * dt;
        return Math.max(1.0e-12, next);
    }

    /** Builds a human readable pressure label. */
    public static String describe(double pascal) {
        if (pascal > 1.0e5) {
            return "atmosphere";
        }
        if (pascal > 1.0e3) {
            return "rough vacuum";
        }
        if (pascal > 1.0) {
            return "coarse vacuum";
        }
        if (pascal > 1.0e-1) {
            return "medium vacuum";
        }
        if (pascal > 1.0e-4) {
            return "high vacuum";
        }
        if (pascal > 1.0e-7) {
            return "ultra high vacuum";
        }
        return "extreme high vacuum";
    }
}
