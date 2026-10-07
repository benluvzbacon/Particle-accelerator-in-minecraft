package com.particlephysics.elements;

import com.particlephysics.physics.Units;

/**
 * A nuclide: a specific number of protons and neutrons.
 *
 * <p>Half lives come from the measured values in the data tables; a configurable time compression
 * factor is applied when the game simulates decay so that 4.5 billion year uranium and 0.7 ms
 * oganesson are both playable without changing the underlying ratios inside one sample.
 */
public final class Isotope {
    private final Element element;
    private final int massNumber;
    private final double atomicMass;
    private final double halfLifeSeconds;
    private final DecayMode decayMode;
    private final double naturalAbundance;

    public Isotope(Element element, int massNumber, double atomicMass, double halfLifeSeconds,
                   DecayMode decayMode, double naturalAbundance) {
        this.element = element;
        this.massNumber = massNumber;
        this.atomicMass = atomicMass;
        this.halfLifeSeconds = halfLifeSeconds;
        this.decayMode = decayMode;
        this.naturalAbundance = naturalAbundance;
    }

    public Element element() {
        return element;
    }

    public int massNumber() {
        return massNumber;
    }

    /** Atomic mass in unified atomic mass units. */
    public double atomicMass() {
        return atomicMass;
    }

    /** Mass in MeV/c^2. */
    public double massMeV() {
        return atomicMass * Units.AMU_TO_MEV;
    }

    public boolean isStable() {
        return decayMode == DecayMode.STABLE || halfLifeSeconds < 0;
    }

    /** Real world half life in seconds (infinite for stable nuclides). */
    public double halfLifeSeconds() {
        return isStable() ? Double.POSITIVE_INFINITY : halfLifeSeconds;
    }

    /** Half life as it is simulated in game, after the configured compression. */
    public double gameHalfLife(double compressionFactor) {
        if (isStable()) {
            return Double.POSITIVE_INFINITY;
        }
        double compressed = halfLifeSeconds / Math.max(1.0e-6, compressionFactor);
        return Math.max(0.05, Math.min(compressed, 60.0 * 60.0 * 24.0 * 30.0));
    }

    public DecayMode decayMode() {
        return decayMode;
    }

    public double naturalAbundance() {
        return naturalAbundance;
    }

    /** Decay constant lambda = ln2 / T_half in 1/s. */
    public double decayConstant() {
        if (isStable()) {
            return 0.0;
        }
        return Math.log(2.0) / Math.max(1.0e-12, halfLifeSeconds);
    }

    /**
     * Specific activity in becquerel per gram:
     * A = ln2/T * N_A / M, with M the molar mass in g/mol.
     */
    public double activityPerGram() {
        if (isStable()) {
            return 0.0;
        }
        return decayConstant() * Units.AVOGADRO / Math.max(0.001, atomicMass);
    }

    /**
     * Q value of the decay in MeV, computed from the mass difference of parent and daughters.
     * This is the energy that ends up in the emitted radiation and is what feeds the dose model.
     */
    public double decayQValueMeV() {
        if (isStable()) {
            return 0.0;
        }
        double parent = massMeV();
        return switch (decayMode) {
            case ALPHA -> {
                Isotope daughter = daughter();
                double daughterMass = daughter != null ? daughter.massMeV() : parent - 4000.0;
                yield Math.max(0.0, parent - daughterMass - Units.ALPHA_MASS);
            }
            case BETA_MINUS, DOUBLE_BETA -> {
                Isotope daughter = daughter();
                double daughterMass = daughter != null ? daughter.massMeV() : parent;
                yield Math.max(0.0, parent - daughterMass);
            }
            case BETA_PLUS -> {
                Isotope daughter = daughter();
                double daughterMass = daughter != null ? daughter.massMeV() : parent;
                yield Math.max(0.0, parent - daughterMass - 2.0 * Units.ELECTRON_MASS);
            }
            case EC -> {
                Isotope daughter = daughter();
                double daughterMass = daughter != null ? daughter.massMeV() : parent;
                yield Math.max(0.0, parent - daughterMass);
            }
            default -> decayMode.energyMeV();
        };
    }

    /** Name such as "Uranium-235". */
    public String name() {
        return element.name() + "-" + massNumber;
    }

    /** Compact notation such as "235U". */
    public String notation() {
        return massNumber + element.symbol();
    }

    /** The daughter nuclide produced by alpha or beta decay, or null when not in the tables. */
    public Isotope daughter() {
        return switch (decayMode) {
            case ALPHA -> Isotopes.find(element.atomicNumber() - 2, massNumber - 4);
            case BETA_MINUS, DOUBLE_BETA -> Isotopes.find(element.atomicNumber() + 1, massNumber);
            case BETA_PLUS, EC -> Isotopes.find(element.atomicNumber() - 1, massNumber);
            case IT -> Isotopes.find(element.atomicNumber(), massNumber);
            default -> null;
        };
    }

    /** The second daughter for the fission mode: the heavy fragment. */
    public Isotope heavyFissionFragment(java.util.Random random) {
        int a = 130 + random.nextInt(16);
        return Isotopes.find((int) Math.round(element.atomicNumber() * 0.55), a);
    }

    /** The light fission fragment. */
    public Isotope lightFissionFragment(java.util.Random random) {
        int a = 90 + random.nextInt(16);
        return Isotopes.find((int) Math.round(element.atomicNumber() * 0.42), a);
    }

    @Override
    public String toString() {
        return notation();
    }
}
