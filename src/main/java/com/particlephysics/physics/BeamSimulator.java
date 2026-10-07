package com.particlephysics.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The tracking engine: advances a beam through a lattice.
 *
 * <p>Two complementary descriptions are integrated together every step:
 * <ol>
 *   <li><b>Macro-particle tracking.</b> Each sample is pushed through the real optical elements
 *       (quadrupole focusing, sextupole non-linearity, dipole kicks from field errors, steering
 *       corrections) using symplectic drift-kick maps, and is lost when its amplitude reaches the
 *       vacuum chamber aperture. This is what makes beam loss, beam-pipe collisions and orbit
 *       distortion emerge from the actual magnet configuration.</li>
 *   <li><b>Bunch statistics.</b> The energy is advanced with the RF gain per turn minus
 *       synchrotron radiation, the RF phase follows the synchrotron oscillation equations, and the
 *       intensity decays with the beam-gas and instability lifetimes. A beam at 0.999 c passes
 *       through the ring thousands of times per tick; the physics of those passages (energy gain,
 *       radiation loss, gas scattering) is integrated in closed form over the full number of laps,
 *       while the non-linear transverse map is sampled with a bounded number of iterations.</li>
 * </ol>
 */
public final class BeamSimulator {
    private BeamSimulator() {
    }

    /** Simulation quality presets, exposed in the config file. */
    public enum Quality {
        LOW(16, 4, 8),
        MEDIUM(32, 12, 32),
        HIGH(64, 32, 128),
        EXTREME(128, 64, 256);

        public final int maxParticles;
        public final int trackingIterations;
        public final int maxMapIterations;

        Quality(int maxParticles, int trackingIterations, int maxMapIterations) {
            this.maxParticles = maxParticles;
            this.trackingIterations = trackingIterations;
            this.maxMapIterations = maxMapIterations;
        }
    }

    /** One localised beam loss. */
    public record Loss(double s, double energyMeV, double particles, String cause) {
    }

    /** Outcome of one simulation step. */
    public static final class StepResult {
        public final List<Loss> losses = new ArrayList<>();
        public double energyGainPerTurn;
        public double radiationLossPerTurn;
        public double gasLifetime = Double.POSITIVE_INFINITY;
        public double instabilityGrowth;
        public double revolutionFrequency;
        public double beamPowerWatts;
        public boolean beamLost;
        public String lossReason;
        public double scatteringAngleRms;
    }

    /**
     * Advances one beam by {@code dt} seconds of beam time.
     *
     * @param beam       the beam to advance
     * @param lattice    the accelerator lattice built from the placed blocks
     * @param machine    the machine state (fields, RF, vacuum, faults)
     * @param optics     the Twiss solution of the current lattice
     * @param dt         beam time step in seconds
     * @param quality    simulation quality
     * @param rng        random source
     */
    public static StepResult step(Beam beam, Lattice lattice, MachineState machine,
                                  Optics.Solution optics, double dt, Quality quality, Random rng) {
        StepResult result = new StepResult();
        if (beam.intensity <= 0.0 || beam.particles.isEmpty()) {
            beam.lost = true;
            result.beamLost = true;
            result.lossReason = "no beam";
            return result;
        }

        double charge = Math.abs(beam.species.charge()) > 1.0e-9 ? beam.species.charge()
                : beam.species.charge();
        double momentum = beam.momentum();
        double rigidity = Relativity.rigidity(momentum / 1000.0, charge == 0 ? 1.0 : charge);
        double rho = lattice.bendingRadius();
        double beta = beam.beta();
        double gamma = beam.gamma();
        double circumference = Math.max(1.0, lattice.circumference());
        double revolutionFrequency = beta * Units.C / circumference;
        result.revolutionFrequency = revolutionFrequency;
        double laps = dt * revolutionFrequency;

        // ---------------------------------------------------------------- energy and RF phase
        double lossPerTurn = synchrotronLossPerTurn(beam, lattice);
        result.radiationLossPerTurn = lossPerTurn;
        double voltage = machine.rfVoltageMV * 1.0e6;   // volts
        double harmonic = Math.max(1, lattice.harmonicNumber(beta));
        double eta = slipFactor(gamma, lattice);
        double energyGainPerTurn = charge * voltage * Math.sin(beam.phase) / 1.0e6;
        result.energyGainPerTurn = energyGainPerTurn;

        double dEnergy = laps * (energyGainPerTurn - lossPerTurn);
        beam.energy = Math.max(0.5, beam.energy + dEnergy);

        // RF phase: frequency error and momentum compaction drive the synchrotron motion
        double omegaRf = 2.0 * Math.PI * machine.rfFrequencyMHz * 1.0e6;
        double deltaPOverP = (momentum - lattice.matchedMomentum(machine.effectiveDipoleField(),
                charge)) / Math.max(1.0e-6, lattice.matchedMomentum(
                machine.effectiveDipoleField(), charge));
        if (!Double.isFinite(deltaPOverP)) {
            deltaPOverP = 0.0;
        }
        double phaseRate = omegaRf - harmonic * 2.0 * Math.PI * revolutionFrequency
                * (1.0 - eta * deltaPOverP);
        beam.phase += phaseRate * dt;
        // keep the phase in a sane range and fold it into the RF period
        beam.phase = wrap(beam.phase);

        // Radiation damping (electrons only, protons are effectively undamped)
        if (lossPerTurn > 1.0e-9 && beam.species.mass() < 200.0) {
            double dampingTime = 2.0 * beam.totalEnergy() * (circumference / (beta * Units.C))
                    / Math.max(1.0e-9, lossPerTurn);
            beam.emittanceX *= Math.exp(-dt / Math.max(1.0e-3, dampingTime));
            beam.emittanceY *= Math.exp(-dt / Math.max(1.0e-3, dampingTime));
            beam.energySpread *= Math.exp(-dt / Math.max(1.0e-3, dampingTime));
            beam.energySpread = Math.max(1.0e-6, beam.energySpread);
        }

        // ---------------------------------------------------------------- beam-gas scattering
        double gasCrossSection = Vacuum.gasCrossSection(beam.energy, beam.species.mass(), charge,
                6.0, 12.0);
        double gasLifetime = Vacuum.beamLifetime(machine.vacuumPressurePa, gasCrossSection, beta);
        result.gasLifetime = gasLifetime;
        double scatteringProbability = gasLifetime == Double.POSITIVE_INFINITY ? 0.0
                : Math.min(0.5, dt / gasLifetime);
        double scatteringAngle = Vacuum.multipleScatteringAngle(machine.vacuumPressurePa,
                Math.max(1.0e-6, laps * circumference), beam.energy, beam.species.mass());
        result.scatteringAngleRms = scatteringAngle;

        // ---------------------------------------------------------------- instability analysis
        double instability = instabilityGrowth(beam, lattice, machine, optics, eta);
        result.instabilityGrowth = instability;

        // ---------------------------------------------------------------- orbit distortion
        double dispersion = Double.isFinite(optics.dispersion) ? optics.dispersion : 0.0;
        double orbitOffset = dispersion * deltaPOverP
                + machine.orbitDistortionMm * 1.0e-3
                - machine.steeringTrim * 1.0e-3;

        // ---------------------------------------------------------------- tracking
        double aperture = lattice.minAperture();
        int iterations = Math.min(quality.maxMapIterations,
                Math.max(quality.trackingIterations, (int) Math.min(quality.maxMapIterations, laps)));
        double extraLaps = Math.max(0.0, laps - iterations);
        double growthPerTurn = instability > 0 ? Math.exp(Math.min(0.2, instability)) : 1.0;

        double lostWeight = 0.0;
        double lostEnergy = 0.0;
        List<Loss> losses = new ArrayList<>();
        int survivors = 0;
        for (BeamParticle particle : beam.particles) {
            if (particle.lost) {
                continue;
            }
            particle.chargeState = charge;
            for (int turn = 0; turn < iterations; turn++) {
                applyOneTurn(particle, lattice, machine, rigidity, charge, rng,
                        optics, orbitOffset);
                particle.turns++;
                particle.s = wrapS(particle.s, circumference);
                if (particleAmplitude(particle) > aperture) {
                    particle.markLost("beam pipe collision at " + particle.lossReason, lattice);
                    break;
                }
            }
            if (particle.lost) {
                lostWeight += particle.weight;
                lostEnergy += particle.energy;
                losses.add(new Loss(particle.s, particle.energy, particle.weight,
                        "vacuum chamber aperture"));
                continue;
            }
            // extrapolate the residual laps: linear growth of the betatron amplitude
            if (extraLaps > 0 && growthPerTurn > 1.0) {
                double factor = Math.pow(growthPerTurn, extraLaps);
                particle.x *= Math.min(1.0e6, factor);
                particle.y *= Math.min(1.0e6, factor);
                particle.xPrime *= Math.min(1.0e6, factor);
                particle.yPrime *= Math.min(1.0e6, factor);
            }
            // multiple scattering kick
            if (scatteringProbability > 0) {
                double kick = scatteringAngle * (rng.nextBoolean() ? 1.0 : -1.0);
                particle.xPrime += kick;
                particle.yPrime += kick * 0.5;
                if (rng.nextDouble() < scatteringProbability) {
                    // a large angle scattering event blows the particle out of the beam
                    double bigKick = kick * 8.0;
                    particle.xPrime += bigKick;
                    particle.yPrime += bigKick * 0.5;
                    if (particleAmplitude(particle)
                            > aperture * (1.0 + rng.nextDouble() * 0.5)) {
                        particle.markLost("beam-gas scattering", lattice);
                        lostWeight += particle.weight;
                        lostEnergy += particle.energy;
                        losses.add(new Loss(particle.s, particle.energy, particle.weight,
                                "beam-gas scattering"));
                        continue;
                    }
                }
            }
            if (particleAmplitude(particle) > aperture) {
                particle.markLost("orbit excursion", lattice);
                lostWeight += particle.weight;
                lostEnergy += particle.energy;
                losses.add(new Loss(particle.s, particle.energy, particle.weight,
                        "orbit excursion"));
                continue;
            }
            particle.world = lattice.toWorld(particle.s, particle.x + orbitOffset, particle.y);
            survivors++;
        }
        result.losses.addAll(losses);

        // displaced particles are re-injected at the injection point so the sample set stays useful
        if (survivors < Math.min(4, quality.maxParticles)) {
            reinjectSamples(beam, lattice, machine, quality, rng);
        }

        // ---------------------------------------------------------------- intensity decay
        double lostFraction = beam.intensity > 0 ? lostWeight / Math.max(1.0e-9, totalWeight(beam))
                : 0.0;
        beam.lossFraction = lostFraction;
        double lifetime = combineLifetimes(gasLifetime, instability, revolutionFrequency);
        beam.lifetime = lifetime;
        double decay = lifetime == Double.POSITIVE_INFINITY ? 1.0 : Math.exp(-dt / lifetime);
        double instabilityDecay = instability > 0 ? Math.exp(-Math.min(0.5, instability) * laps) : 1.0;
        beam.intensity *= decay * instabilityDecay * (1.0 - lostFraction * 0.5);
        beam.energySpread = Math.max(beam.energySpread,
                Math.min(0.05, 0.02 * Math.abs(deltaPOverP) + scatteringAngle * 0.01));
        beam.emittanceX = Math.max(1.0e-9, beam.emittanceX
                * Math.exp(Math.max(0.0, scatteringAngle) * 0.5)
                * (instability > 0 ? Math.exp(Math.min(0.4, instability)) : 1.0));

        // ---------------------------------------------------------------- radiation from losses
        double lostParticles = beam.intensity * lostFraction + (1.0 - decay) * beam.intensity
                + (1.0 - instabilityDecay) * beam.intensity;
        result.beamPowerWatts = lostParticles * beam.energy * 1.0e6 * Units.EV_TO_JOULE / dt;

        if (beam.intensity < 1.0e3) {
            beam.lost = true;
            result.beamLost = true;
            result.lossReason = describeLossCause(lostFraction, instability, machine, beam);
        }
        return result;
    }

    private static String describeLossCause(double lostFraction, double instability,
                                            MachineState machine, Beam beam) {
        if (machine.magnetQuench) {
            return "magnet quench";
        }
        if (machine.vacuumFault) {
            return "vacuum failure";
        }
        if (instability > 0.02) {
            return "beam instability";
        }
        if (lostFraction > 0.5) {
            return "beam pipe loss";
        }
        if (beam.intensity < 1.0e3) {
            return "intensity decayed to zero";
        }
        return "beam lost";
    }

    /**
     * Push one macro-particle through a single turn of the lattice.
     */
    public static void applyOneTurn(BeamParticle particle, Lattice lattice, MachineState machine,
                                    double rigidity, double charge, Random rng,
                                    Optics.Solution optics, double orbitOffset) {
        double rho = lattice.bendingRadius();
        for (LatticeElement element : lattice.elements()) {
            double length = element.length();
            // half drift
            particle.x += particle.xPrime * length * 0.5;
            particle.y += particle.yPrime * length * 0.5;
            switch (element.kind()) {
                case DIPOLE -> {
                    // A dipole bends by theta = L * B / (B*rho). The design orbit already bends by
                    // L / rho, so only the field mismatch produces a kick relative to that orbit.
                    if (rigidity > 1.0e-9 && Double.isFinite(rho) && rho > 0.05) {
                        double required = rigidity / rho;
                        double actual = Math.min(machine.effectiveDipoleField(), element.capacity());
                        double excessBend = length * (actual - required) / rigidity;
                        particle.xPrime += excessBend * element.bendSign();
                    }
                }
                case QUADRUPOLE -> {
                    if (rigidity > 1.0e-9) {
                        double gradient = element.field() * machine.effectiveQuadrupoleScale();
                        double k = gradient / rigidity;
                        // A quadrupole focuses in one plane and defocuses in the other; rotating it
                        // by 90 degrees swaps the planes - exactly the mistake the construction
                        // validator warns about.
                        boolean rotated = element.rotation() % 2 == 1;
                        double kx = rotated ? -k : k;
                        particle.xPrime += -kx * length * particle.x;
                        particle.yPrime += kx * length * particle.y;
                    }
                }
                case SEXTUPOLE -> {
                    if (rigidity > 1.0e-9) {
                        double ks = element.field() * machine.sextupoleScale / rigidity;
                        particle.xPrime += -0.5 * ks * length * particle.x * particle.x;
                        particle.yPrime += 0.5 * ks * length * particle.y * particle.y;
                    }
                }
                case STEERING -> {
                    double angle = (element.steeringAngle() * Math.PI / 180.0
                            + Math.toRadians(machine.steeringTrim)) * element.bendSign();
                    particle.xPrime += angle;
                }
                case COLLIMATOR -> {
                    double limit = element.aperture() * 0.6;
                    if (particleAmplitude(particle) > limit) {
                        particle.markLost("collimator", lattice);
                        return;
                    }
                }
                case COLLISION_POINT -> {
                    // the interaction point focuses the beam with the detector solenoid
                    if (rigidity > 1.0e-9 && optics != null) {
                        double kIp = 0.02 / Math.max(1.0e-3, optics.betaX);
                        particle.xPrime += -kIp * length * particle.x;
                        particle.yPrime += kIp * length * particle.y;
                    }
                }
                default -> {
                }
            }
            // half drift
            particle.x += particle.xPrime * length * 0.5;
            particle.y += particle.yPrime * length * 0.5;
            particle.s += length;
            if (particleAmplitude(particle) > element.aperture()) {
                particle.markLost(element.label(), lattice);
                return;
            }
        }
    }

    /** Residual betatron amplitude in metres. */
    public static double particleAmplitude(BeamParticle particle) {
        return Math.sqrt(particle.x * particle.x + particle.y * particle.y);
    }

    /** Longitudinal slippage factor eta = 1/gamma_tr^2 - 1/gamma^2. */
    public static double slipFactor(double gamma, Lattice lattice) {
        double gammaTr = transitionGamma(lattice);
        return 1.0 / (gammaTr * gammaTr) - 1.0 / (gamma * gamma);
    }

    /** Transition gamma approximated from the lattice tune (Qx for a FODO ring). */
    public static double transitionGamma(Lattice lattice) {
        double quadLength = lattice.totalMagnetLength(LatticeElement.Kind.QUADRUPOLE);
        if (quadLength <= 0.01) {
            return 1.0e6;
        }
        double cellLength = Math.max(1.0, lattice.circumference() / 8.0);
        double tune = Math.max(1.0, Math.sqrt(quadLength / cellLength) * 6.0);
        return tune;
    }

    /** Synchrotron radiation energy loss per turn for the whole beam. */
    public static double synchrotronLossPerTurn(Beam beam, Lattice lattice) {
        double rho = lattice.bendingRadius();
        if (!Double.isFinite(rho)) {
            return 0.0;
        }
        double classicalRadius = beam.species == ParticleSpecies.ELECTRON
                || beam.species == ParticleSpecies.POSITRON
                ? Units.ELECTRON_RADIUS
                : beam.species == ParticleSpecies.MUON || beam.species == ParticleSpecies.ANTIMUON
                ? Units.ELECTRON_RADIUS * Math.pow(Units.ELECTRON_MASS / Units.MUON_MASS, 2)
                : Units.PROTON_RADIUS;
        return Relativity.synchrotronLossPerTurn(beam.energy, beam.species.mass(), rho,
                classicalRadius);
    }

    /**
     * Growth rate per turn of the transverse instability. Positive values mean the beam is
     * unstable; the value is driven by the quadrupole configuration, the uncorrected
     * chromaticity, the residual gas pressure and the space charge at low energy.
     * */
    public static double instabilityGrowth(Beam beam, Lattice lattice, MachineState machine,
                                           Optics.Solution optics, double eta) {
        double growth = 0.0;
        if (!optics.stableX || !optics.stableY) {
            growth += 0.08;
        }
        double chromaticity = Math.abs(optics.chromaticityX);
        double sextupoleCorrection = lattice.totalMagnetLength(LatticeElement.Kind.SEXTUPOLE)
                * machine.sextupoleScale * 2.0e-3;
        double residualChroma = Math.max(0.0, chromaticity - sextupoleCorrection);
        growth += residualChroma * 0.02;

        // space charge: matters at low energy and high intensity
        double gamma = beam.gamma();
        double beta = beam.beta();
        double size = beam.typicalSize(optics);
        double spaceCharge = beam.intensity * (chargeFactor(beam)) / (gamma * gamma * beta * beta
                * Math.max(1.0e-5, size * size) * 1.0e18);
        growth += Math.min(0.05, spaceCharge * 0.01);

        // poor vacuum increases the growth rate (ion and residual gas instabilities)
        if (machine.vacuumPressurePa > 1.0e-4) {
            growth += Math.min(0.05, Math.log10(machine.vacuumPressurePa / 1.0e-4) * 0.01);
        }
        // RF desynchronisation feeds longitudinal instability
        double rfMismatch = Math.abs(Math.sin(beam.phase));
        if (eta < 0 && rfMismatch < 0.2) {
            growth += 0.02;
        }
        return Math.max(0.0, growth);
    }

    private static double chargeFactor(Beam beam) {
        return Math.abs(beam.species.charge()) > 0.1 ? Math.abs(beam.species.charge()) : 1.0;
    }

    private static double totalWeight(Beam beam) {
        double sum = 0;
        for (BeamParticle p : beam.particles) {
            sum += p.weight;
        }
        return sum;
    }

    /** Re-populates the tracked samples at the injection point. */
    public static void reinjectSamples(Beam beam, Lattice lattice, MachineState machine,
                                       Quality quality, Random rng) {
        double injectionS = lattice.injectionPointS();
        double weight = beam.intensity / Math.max(1, quality.maxParticles);
        for (BeamParticle particle : beam.particles) {
            if (particle.lost) {
                particle.lost = false;
                particle.lossReason = "";
                particle.s = injectionS;
                particle.x = (rng.nextDouble() - 0.5) * 0.002;
                particle.y = (rng.nextDouble() - 0.5) * 0.002;
                particle.xPrime = (rng.nextDouble() - 0.5) * 1.0e-4;
                particle.yPrime = (rng.nextDouble() - 0.5) * 1.0e-4;
                particle.energy = beam.energy;
                particle.age = 0;
                particle.weight = weight;
            }
        }
    }

    /** Creates a fresh set of tracked samples for a newly injected beam. */
    public static void populate(Beam beam, Lattice lattice, Quality quality, Random rng,
                               double injectedIntensity) {
        beam.particles.clear();
        double injectionS = lattice.injectionPointS();
        double weight = injectedIntensity / Math.max(1, quality.maxParticles);
        for (int i = 0; i < quality.maxParticles; i++) {
            BeamParticle particle = new BeamParticle(beam.species, beam.energy);
            particle.s = injectionS;
            particle.x = (rng.nextDouble() - 0.5) * 0.004;
            particle.y = (rng.nextDouble() - 0.5) * 0.004;
            particle.xPrime = (rng.nextDouble() - 0.5) * 2.0e-4;
            particle.yPrime = (rng.nextDouble() - 0.5) * 2.0e-4;
            particle.weight = weight;
            particle.phase = beam.phase;
            beam.particles.add(particle);
        }
    }

    private static double combineLifetimes(double gasLifetime, double instability,
                                           double revolutionFrequency) {
        double rate = 0.0;
        if (gasLifetime != Double.POSITIVE_INFINITY && gasLifetime > 0) {
            rate += 1.0 / gasLifetime;
        }
        if (instability > 0) {
            rate += Math.min(1.0e3, instability * revolutionFrequency);
        }
        return rate <= 0 ? Double.POSITIVE_INFINITY : 1.0 / rate;
    }

    private static double wrap(double phase) {
        double twoPi = Math.PI * 2.0;
        double p = phase % twoPi;
        if (p > Math.PI) {
            p -= twoPi;
        } else if (p < -Math.PI) {
            p += twoPi;
        }
        return p;
    }

    private static double wrapS(double s, double circumference) {
        double r = s % circumference;
        return r < 0 ? r + circumference : r;
    }
}
