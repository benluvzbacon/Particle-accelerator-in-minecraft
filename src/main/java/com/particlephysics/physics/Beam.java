package com.particlephysics.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * A circulating beam: a statistical description of the whole bunch population plus a bounded set of
 * tracked macro-particles used for tracking, rendering and loss localisation.
 *
 * <p>The full beam may contain 10^10 or more particles; only {@code particles.size()} of them are
 * integrated numerically. The macro-particles carry a {@code weight} so that any loss is converted
 * back into a fraction of the total intensity.
 */
public final class Beam {
    public ParticleSpecies species;
    /** Total number of physical particles in the beam. */
    public double intensity;
    public final List<BeamParticle> particles = new ArrayList<>();
    /** Mean kinetic energy in MeV. */
    public double energy;
    /** Relative RMS energy spread. */
    public double energySpread = 1.0e-3;
    /** Normalised transverse emittance in m*rad. */
    public double emittanceX = 1.0e-6;
    public double emittanceY = 1.0e-6;
    /** Synchronous RF phase in radians. */
    public double phase;
    /** Beam current in amperes. */
    public double current;
    /** Number of bunches. */
    public int bunches = 1;
    /** Lifetime in seconds against all loss mechanisms. */
    public double lifetime = Double.POSITIVE_INFINITY;
    /** Fraction of the beam lost on the last step. */
    public double lossFraction;
    /** True when the beam was lost (intensity below the useful threshold). */
    public boolean lost;
    public String lossReason = "";
    /** Sum of the beta functions at the collision point, used for the beam size. */
    public double betaStarX = 1.0;
    public double betaStarY = 1.0;
    /** Momentum (MeV/c) of the matched orbit. */
    public double momentum() {
        return Relativity.momentumFromKinetic(energy, species.mass());
    }

    public double beta() {
        return Relativity.beta(energy, species.mass());
    }

    public double gamma() {
        return Relativity.gammaFromKinetic(energy, species.mass());
    }

    public double totalEnergy() {
        return energy + species.mass();
    }

    /** Momentum spread dp/p derived from the energy spread. */
    public double momentumSpread() {
        double beta = beta();
        return energySpread / Math.max(1.0e-6, beta * beta);
    }

    /** RMS horizontal beam size at the given beta function in metres. */
    public double beamSizeX(Lattice lattice, Optics.Solution optics, double betaAtIp) {
        double beta = betaAtIp > 0 ? betaAtIp : optics.betaX;
        double sigma = Math.sqrt(emittanceX * beta);
        double dispersive = Math.abs(optics.dispersion) * momentumSpread();
        return Math.sqrt(sigma * sigma + dispersive * dispersive);
    }

    public double beamSizeY(double betaAtIp) {
        return Math.sqrt(emittanceY * Math.max(1.0e-4, betaAtIp));
    }

    /** Round beam size used for luminosity when the aspect ratio is unknown. */
    public double typicalSize(Optics.Solution optics) {
        return Math.max(1.0e-6, Math.sqrt(emittanceX * optics.betaX));
    }

    /**
     * Luminosity for two colliding beams, in m^-2 s^-1:
     * L = f_rev * n_b * N1 * N2 / (4 pi sigma_x sigma_y)
     */
    public static double luminosity(double revFrequency, int bunches, double n1, double n2,
                                    double sigmaX, double sigmaY) {
        double sx = Math.max(1.0e-7, sigmaX);
        double sy = Math.max(1.0e-7, sigmaY);
        return revFrequency * Math.max(1, bunches) * n1 * n2 / (4.0 * Math.PI * sx * sy);
    }

    /** Current in amperes for the given revolution frequency. */
    public double computeCurrent(double revFrequency) {
        current = Math.abs(species.charge()) * intensity * revFrequency;
        return current;
    }

    /** Average of the tracked macro-particles' transverse amplitudes. */
    public double averageAmplitude() {
        if (particles.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (BeamParticle p : particles) {
            sum += Math.sqrt(p.x * p.x + p.y * p.y);
        }
        return sum / particles.size();
    }

    /** Mean electrical charge in coulomb carried by the beam. */
    public double chargeCoulomb() {
        return intensity * species.charge() * Units.ELEMENTARY_CHARGE;
    }

    /** Total kinetic energy stored in the beam, in joules. */
    public double storedEnergyJoules() {
        return intensity * energy * 1.0e6 * Units.EV_TO_JOULE;
    }

    /** Clears the beam. */
    public void clear() {
        particles.clear();
        intensity = 0;
        lost = false;
        lossReason = "";
    }
}
