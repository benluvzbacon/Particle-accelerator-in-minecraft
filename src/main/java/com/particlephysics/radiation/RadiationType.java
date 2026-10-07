package com.particlephysics.radiation;

/**
 * Radiation types with their ICRP weighting factors and their typical range in air, which is what
 * makes alpha emitters harmless outside the body but extremely dangerous inside a shielding
 * calculation when the source is internalised.
 */
public enum RadiationType {
    ALPHA("Alpha", 20.0, 0.04, 0.005),
    BETA("Beta", 1.0, 3.0, 0.4),
    GAMMA("Gamma", 1.0, 120.0, 5.0),
    NEUTRON("Neutron", 10.0, 200.0, 20.0),
    PROTON("Proton", 2.0, 5.0, 0.5),
    MUON("Muon", 1.0, 1000.0, 100.0);

    private final String displayName;
    private final double weightFactor;
    /** Range in air in metres. */
    private final double rangeAir;
    /** Range in concrete in metres. */
    private final double rangeConcrete;

    RadiationType(String displayName, double weightFactor, double rangeAir, double rangeConcrete) {
        this.displayName = displayName;
        this.weightFactor = weightFactor;
        this.rangeAir = rangeAir;
        this.rangeConcrete = rangeConcrete;
    }

    public String displayName() {
        return displayName;
    }

    /** ICRP equivalent dose weighting factor w_R. */
    public double weightFactor() {
        return weightFactor;
    }

    public double rangeAir() {
        return rangeAir;
    }

    public double rangeConcrete() {
        return rangeConcrete;
    }

    /** Charged particles are stopped by any solid material. */
    public boolean isIonisingParticle() {
        return this == ALPHA || this == BETA || this == PROTON;
    }

    public static RadiationType fromDecayMode(
            com.particlephysics.elements.DecayMode mode) {
        return switch (mode) {
            case ALPHA -> ALPHA;
            case BETA_MINUS, BETA_PLUS, EC, DOUBLE_BETA -> BETA;
            case IT -> GAMMA;
            case SF -> NEUTRON;
            default -> GAMMA;
        };
    }
}
