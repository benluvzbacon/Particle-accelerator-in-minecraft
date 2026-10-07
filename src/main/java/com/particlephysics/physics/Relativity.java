package com.particlephysics.physics;

/**
 * Special relativity helpers.
 *
 * <p>Everything works with the total energy, kinetic energy and momentum measured in MeV, MeV/c
 * and MeV/c^2 respectively, which makes every value in the game directly displayable in eV based
 * units. The speed of light is implemented as a hard limit: {@link #beta(double, double)} only ever
 * returns values in [0, 1) and {@link #energyFromBeta(double, double)} returns the energy a particle
 * would need for a given beta, so an accelerated particle's energy grows without bound while its
 * velocity asymptotically approaches c.
 */
public final class Relativity {
    private Relativity() {
    }

    /** Lorentz factor for a velocity given as a fraction of c. */
    public static double gamma(double beta) {
        double b = clampBeta(beta);
        return 1.0 / Math.sqrt(1.0 - b * b);
    }

    /** Lorentz factor for a particle with the given kinetic and rest energy (both MeV). */
    public static double gammaFromKinetic(double kineticEnergy, double restEnergy) {
        if (restEnergy <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0 + Math.max(0.0, kineticEnergy) / restEnergy;
    }

    /**
     * Velocity as a fraction of the speed of light for a particle of rest energy {@code restEnergy}
     * (MeV) carrying kinetic energy {@code kineticEnergy} (MeV). Always strictly below 1.
     */
    public static double beta(double kineticEnergy, double restEnergy) {
        double gamma = gammaFromKinetic(kineticEnergy, restEnergy);
        return betaFromGamma(gamma);
    }

    /** beta from a Lorentz factor, safely clamped below c. */
    public static double betaFromGamma(double gamma) {
        if (gamma <= 1.0) {
            return 0.0;
        }
        double b = Math.sqrt(1.0 - 1.0 / (gamma * gamma));
        return clampBeta(b);
    }

    /** Clamps a velocity to the physically allowed open interval [0, 1 - 1e-12]. */
    public static double clampBeta(double beta) {
        if (Double.isNaN(beta) || beta <= 0) {
            return 0.0;
        }
        double max = 1.0 - 1.0e-12;
        return Math.min(beta, max);
    }

    /** Kinetic energy (MeV) required for a given velocity fraction. */
    public static double energyFromBeta(double beta, double restEnergy) {
        return restEnergy * (gamma(beta) - 1.0);
    }

    /** Total energy (MeV) = sqrt(p^2 c^2 + m^2 c^4), with p in MeV/c and m in MeV/c^2. */
    public static double totalEnergyFromMomentum(double momentum, double restEnergy) {
        return Math.sqrt(momentum * momentum + restEnergy * restEnergy);
    }

    /** Kinetic energy from momentum. */
    public static double kineticFromMomentum(double momentum, double restEnergy) {
        return totalEnergyFromMomentum(momentum, restEnergy) - restEnergy;
    }

    /** Momentum (MeV/c) for a particle with the given kinetic energy. */
    public static double momentumFromKinetic(double kineticEnergy, double restEnergy) {
        double total = kineticEnergy + restEnergy;
        double p2 = total * total - restEnergy * restEnergy;
        return p2 <= 0 ? 0 : Math.sqrt(p2);
    }

    /** Momentum (MeV/c) from velocity and rest energy. */
    public static double momentumFromBeta(double beta, double restEnergy) {
        double b = clampBeta(beta);
        if (b <= 0) {
            return 0.0;
        }
        return gamma(b) * b * restEnergy;
    }

    /**
     * Magnetic rigidity B*rho in tesla metres, the quantity that links momentum with the dipole
     * field needed to bend the beam: B*rho = p / (0.299792458 * |q/e|).
     *
     * @param momentumGeVPerC momentum in GeV/c
     * @param chargeState     charge in units of the elementary charge
     */
    public static double rigidity(double momentumGeVPerC, double chargeState) {
        if (chargeState == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.abs(momentumGeVPerC) / (Units.RIGIDITY_FACTOR * Math.abs(chargeState));
    }

    /**
     * Dipole field needed to keep a particle of the given momentum on a circle of the given
     * bending radius.
     *
     * @param momentumMeVPerC momentum in MeV/c
     * @param radius          bending radius in metres
     * @param chargeState     charge in units of e
     * @return the required field in tesla
     */
    public static double dipoleField(double momentumMeVPerC, double radius, double chargeState) {
        if (radius <= 0 || chargeState == 0) {
            return 0.0;
        }
        double rigidity = rigidity(momentumMeVPerC / 1000.0, chargeState);
        return rigidity / radius;
    }

    /**
     * Energy loss per revolution due to synchrotron radiation for a particle of rest energy
     * {@code restEnergy} and the given kinetic energy on a ring with bending radius rho.
     *
     * <p>U0 = (4*pi/3) * r_classical * m c^2 * beta^3 * gamma^4 / rho
     *
     * @return energy loss per turn in MeV
     */
    public static double synchrotronLossPerTurn(double kineticEnergyMeV, double restEnergyMeV,
                                               double rhoMetres, double classicalRadius) {
        if (rhoMetres <= 0 || restEnergyMeV <= 0) {
            return 0.0;
        }
        double gamma = gammaFromKinetic(kineticEnergyMeV, restEnergyMeV);
        double beta = betaFromGamma(gamma);
        double lossEv = (4.0 * Math.PI / 3.0) * classicalRadius * (restEnergyMeV * 1.0e6)
                * beta * beta * beta * Math.pow(gamma, 4) / rhoMetres;
        return lossEv / 1.0e6;
    }

    /** Relativistic Doppler / energy boost factors for a two body collision. */
    public static double centreOfMassEnergySquared(double e1, double p1, double m1,
                                                   double e2, double p2, double m2,
                                                   double cosAngle) {
        return m1 * m1 + m2 * m2 + 2.0 * (e1 * e2 - p1 * p2 * cosAngle);
    }

    /** sqrt(s) of a collision in MeV. */
    public static double centreOfMassEnergy(double e1, double p1, double m1,
                                            double e2, double p2, double m2,
                                            double cosAngle) {
        double s = centreOfMassEnergySquared(e1, p1, m1, e2, p2, m2, cosAngle);
        return s <= 0 ? 0 : Math.sqrt(s);
    }

    /**
     * Computes the lab-frame energy of a particle emitted with the given centre of mass energy and
     * the given longitudinal momentum fraction (used for hadronisation approximations).
     */
    public static double boostToLab(double eCm, double pCm, double betaBoost, double gammaBoost,
                                    double cosThetaCm) {
        return gammaBoost * (eCm + betaBoost * pCm * cosThetaCm);
    }

    /** Velocity of the centre of mass system, relative to the laboratory, as a fraction of c. */
    public static double centreOfMassBeta(double p1, double p2, double e1, double e2) {
        double totalE = e1 + e2;
        if (totalE <= 0) {
            return 0.0;
        }
        return clampBeta((p1 - p2) / totalE);
    }
}
