package com.particlephysics.elements;

/**
 * Nuclear decay modes with the radiation they produce.
 *
 * <p>Quality factors follow ICRP 103 (alpha = 20, beta/gamma = 1, neutrons = 5-20) which is what
 * makes alpha emitters so much more dangerous per unit of energy deposited.
 */
public enum DecayMode {
    STABLE("Stable", "none", 0.0, 0.0, 0.0),
    ALPHA("Alpha decay", "alpha", 5.0, 2.0, 20.0),
    BETA_MINUS("Beta minus decay", "beta-/gamma", 0.6, 1.2, 1.0),
    BETA_PLUS("Positron emission", "beta+/annihilation", 0.9, 1.2, 1.0),
    EC("Electron capture", "X-rays/Auger", 0.1, 0.8, 1.0),
    IT("Isomeric transition", "gamma", 0.4, 1.0, 1.0),
    SF("Spontaneous fission", "fission fragments/neutrons", 3.0, 2.0, 10.0),
    DOUBLE_BETA("Double beta decay", "beta-", 0.3, 1.0, 1.0);

    private final String displayName;
    private final String radiation;
    /** Typical emitted energy per decay in MeV, used for dose calculations. */
    private final double energyMeV;
    /** Typical range in centimetres of dense material (lead/concrete comparison). */
    private final double rangeCm;
    /** ICRP radiation weighting factor. */
    private final double weightFactor;

    DecayMode(String displayName, String radiation, double energyMeV, double rangeCm,
              double weightFactor) {
        this.displayName = displayName;
        this.radiation = radiation;
        this.energyMeV = energyMeV;
        this.rangeCm = rangeCm;
        this.weightFactor = weightFactor;
    }

    public String displayName() {
        return displayName;
    }

    /** The radiation produced, as a human readable string. */
    public String radiation() {
        return radiation;
    }

    public double energyMeV() {
        return energyMeV;
    }

    public double rangeCm() {
        return rangeCm;
    }

    public double weightFactor() {
        return weightFactor;
    }

    public boolean emitsNeutrons() {
        return this == SF;
    }

    public boolean emitsChargedParticles() {
        return this == ALPHA || this == BETA_MINUS || this == BETA_PLUS || this == EC;
    }

    static DecayMode parse(String name) {
        return switch (name) {
            case "ALPHA" -> ALPHA;
            case "BETA_MINUS" -> BETA_MINUS;
            case "BETA_PLUS" -> BETA_PLUS;
            case "EC" -> EC;
            case "IT" -> IT;
            case "SF" -> SF;
            case "DOUBLE_BETA" -> DOUBLE_BETA;
            default -> STABLE;
        };
    }
}
