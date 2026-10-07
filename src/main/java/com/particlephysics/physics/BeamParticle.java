package com.particlephysics.physics;

import java.util.concurrent.ThreadLocalRandom;

/**
 * A tracked macro-particle: one numerical sample that represents a large number of real beam
 * particles (the {@link #weight}). Every macro-particle carries the full state vector required by
 * the physics: position, velocity, mass, charge, energy, momentum, direction, age and species.
 *
 * <p>The transverse coordinates are stored relative to the accelerator's design orbit
 * (s, x, y and the angles x' = dx/ds, y' = dy/ds), which is how real tracking codes work. World
 * space coordinates are reconstructed through {@link Lattice#toWorld}.
 */
public final class BeamParticle {
    public ParticleSpecies species;
    /** Number of physical particles represented by this sample. */
    public double weight;

    /** Longitudinal position along the design orbit [m]. */
    public double s;
    /** Horizontal offset from the design orbit [m]. */
    public double x;
    /** Vertical offset from the design orbit [m]. */
    public double y;
    /** Horizontal angle dx/ds. */
    public double xPrime;
    /** Vertical angle dy/ds. */
    public double yPrime;
    /** Longitudinal (bunch) offset [m]. */
    public double z;

    /** Kinetic energy [MeV]. */
    public double energy;
    /** RF phase [rad]. */
    public double phase;
    /** Time since injection [s]. */
    public double age;
    /** Charge state in units of e (ions can be partially stripped). */
    public double chargeState;
    /** Revolutions completed. */
    public int turns;
    /** Set when the particle hit the vacuum chamber wall. */
    public boolean lost;
    /** Reason the particle was lost, for the diagnostics log. */
    public String lossReason = "";

    /** Position of the particle during the previous simulation step (for rendering). */
    public Vec3 previousWorld = Vec3.ZERO;
    public Vec3 world = Vec3.ZERO;

    public BeamParticle(ParticleSpecies species, double energy) {
        this.species = species;
        this.energy = energy;
        this.chargeState = species.charge();
        this.weight = 1.0;
    }

    public double gamma() {
        return Relativity.gammaFromKinetic(energy, species.mass());
    }

    public double beta() {
        return Relativity.beta(energy, species.mass());
    }

    /** Momentum in MeV/c. */
    public double momentum() {
        return Relativity.momentumFromKinetic(energy, species.mass());
    }

    public double mass() {
        return species.mass();
    }

    public double charge() {
        return chargeState;
    }

    /** Total energy in MeV. */
    public double totalEnergy() {
        return energy + species.mass();
    }

    /** Velocity in m/s. */
    public double speed() {
        return beta() * Units.C;
    }

    /** Lifetime of the species in the particle's own frame, in seconds. */
    public double properLifetime() {
        return species.lifetime();
    }

    /** Lorentz dilated lifetime as seen in the laboratory. */
    public double laboratoryLifetime() {
        if (species.isStable()) {
            return Double.POSITIVE_INFINITY;
        }
        return species.lifetime() * gamma();
    }

    /** Unit direction vector in world space, from the lattice tangent plus the betatron angles. */
    public Vec3 direction(Lattice lattice) {
        Vec3 tangent = lattice.tangentAt(s);
        Vec3 horizontal = lattice.horizontalNormalAt(s);
        Vec3 vertical = new Vec3(0, 1, 0);
        return tangent.add(horizontal.scale(xPrime)).add(vertical.scale(yPrime)).normalise();
    }

    /** Full world momentum vector in MeV/c. */
    public Vec3 momentumVector(Lattice lattice) {
        return direction(lattice).scale(momentum());
    }

    /** Velocity vector in m/s. */
    public Vec3 velocityVector(Lattice lattice) {
        return direction(lattice).scale(speed());
    }

    /** Relativistic kinetic energy that the particle would need to move at the given speed. */
    public void setBeta(double beta) {
        this.energy = Relativity.energyFromBeta(beta, species.mass());
    }

    /** Adds energy (MeV), keeping the speed of light as a hard limit. */
    public void addEnergy(double delta) {
        energy = Math.max(0.0, energy + delta);
    }

    /**
     * Computes the betatron amplitude from the Courant-Snyder invariant, used to decide whether a
     * particle survives the aperture.
     */
    public static double emittanceContribution(double x, double xPrime, double beta, double alpha) {
        double gamma = (1.0 + alpha * alpha) / beta;
        return gamma * x * x + 2.0 * alpha * x * xPrime + beta * xPrime * xPrime;
    }

    public void markLost(String reason, Lattice lattice) {
        if (!lost) {
            lost = true;
            lossReason = reason;
            previousWorld = world;
            if (lattice != null) {
                world = lattice.toWorld(s, Math.signum(x) * lattice.minAperture(), y);
            }
        }
    }

    /** Age the particle by dt seconds; returns true when the particle decays. */
    public boolean ageBy(double dt) {
        age += dt;
        if (species.isStable()) {
            return false;
        }
        double dilated = laboratoryLifetime();
        if (dilated <= 0) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() >= Math.exp(-dt / dilated);
    }

    public BeamParticle copy() {
        BeamParticle p = new BeamParticle(species, energy);
        p.weight = weight;
        p.s = s;
        p.x = x;
        p.y = y;
        p.xPrime = xPrime;
        p.yPrime = yPrime;
        p.z = z;
        p.phase = phase;
        p.age = age;
        p.chargeState = chargeState;
        p.turns = turns;
        p.lost = lost;
        p.lossReason = lossReason;
        p.world = world;
        p.previousWorld = previousWorld;
        return p;
    }
}
