package com.particlephysics.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Every particle species the accelerator can inject, accelerate, collide, transport and detect.
 *
 * <p>Masses are the CODATA / PDG values in MeV/c^2, charges are in units of the elementary charge
 * and lifetimes are in seconds (infinite for stable particles). The decay table is used by
 * {@link Decays} to model particle decay and by the detectors to decide which sub-detector observes
 * a particle.
 */
public enum ParticleSpecies {
    ELECTRON("Electron", "e\u207b", -1.0, Units.ELECTRON_MASS, Double.POSITIVE_INFINITY, 0.5,
            Category.LEPTON, 0.0, 1.0, 0x5AC8FA, true, false, false),
    POSITRON("Positron", "e\u207a", 1.0, Units.ELECTRON_MASS, Double.POSITIVE_INFINITY, 0.5,
            Category.LEPTON, 0.0, -1.0, 0x5AD8FF, true, false, false),
    PROTON("Proton", "p\u207a", 1.0, Units.PROTON_MASS, Double.POSITIVE_INFINITY, 0.5,
            Category.BARYON, 1.0, 0.0, 0xFF6B5B, true, true, false),
    ANTIPROTON("Antiproton", "p\u0304", -1.0, Units.PROTON_MASS, Double.POSITIVE_INFINITY, 0.5,
            Category.BARYON, -1.0, 0.0, 0xFF8A5B, true, true, false),
    NEUTRON("Neutron", "n", 0.0, Units.NEUTRON_MASS, 879.4, 0.5,
            Category.BARYON, 1.0, 0.0, 0xC8D4E3, false, true, false),
    ANTINEUTRON("Antineutron", "n\u0304", 0.0, Units.NEUTRON_MASS, 879.4, 0.5,
            Category.BARYON, -1.0, 0.0, 0xD8C8E3, false, true, false),
    MUON("Muon", "\u03bc\u207b", -1.0, Units.MUON_MASS, 2.1969811e-6, 0.5,
            Category.LEPTON, 0.0, 1.0, 0x8E7CFF, true, false, true),
    ANTIMUON("Antimuon", "\u03bc\u207a", 1.0, Units.MUON_MASS, 2.1969811e-6, 0.5,
            Category.LEPTON, 0.0, -1.0, 0xA08CFF, true, false, true),
    PION_PLUS("Pion+", "\u03c0\u207a", 1.0, Units.PION_CHARGED_MASS, 2.6033e-8, 0.0,
            Category.MESON, 0.0, 0.0, 0xFFD166, true, true, false),
    PION_MINUS("Pion-", "\u03c0\u207b", -1.0, Units.PION_CHARGED_MASS, 2.6033e-8, 0.0,
            Category.MESON, 0.0, 0.0, 0xFFC04D, true, true, false),
    PION_NEUTRAL("Pion0", "\u03c0\u2070", 0.0, Units.PION_NEUTRAL_MASS, 8.52e-17, 0.0,
            Category.MESON, 0.0, 0.0, 0xFFE9A8, false, true, false),
    KAON_PLUS("Kaon+", "K\u207a", 1.0, Units.KAON_CHARGED_MASS, 1.2380e-8, 0.0,
            Category.MESON, 0.0, 0.0, 0x7BE495, true, true, false),
    KAON_MINUS("Kaon-", "K\u207b", -1.0, Units.KAON_CHARGED_MASS, 1.2380e-8, 0.0,
            Category.MESON, 0.0, 0.0, 0x63D585, true, true, false),
    KAON_NEUTRAL("Kaon0", "K\u2070", 0.0, Units.KAON_NEUTRAL_MASS, 5.116e-8, 0.0,
            Category.MESON, 0.0, 0.0, 0x9BE8AE, false, true, false),
    PHOTON("Photon", "\u03b3", 0.0, 0.0, Double.POSITIVE_INFINITY, 1.0,
            Category.BOSON, 0.0, 0.0, 0xFFF9C4, false, true, false),
    NEUTRINO_E("Electron neutrino", "\u03bd\u2091", 0.0, 0.0, Double.POSITIVE_INFINITY, 0.5,
            Category.LEPTON, 0.0, 1.0, 0xB0BEC5, false, false, false),
    NEUTRINO_MU("Muon neutrino", "\u03bd\u03bc", 0.0, 0.0, Double.POSITIVE_INFINITY, 0.5,
            Category.LEPTON, 0.0, 1.0, 0xB0BEC5, false, false, false),
    DEUTERON("Deuteron", "\u00b2H\u207a", 1.0, Units.DEUTERON_MASS, Double.POSITIVE_INFINITY, 1.0,
            Category.NUCLEUS, 2.0, 0.0, 0x8ED1FC, true, true, false),
    TRITON("Triton", "\u00b3H\u207a", 1.0, Units.TRITON_MASS, Double.POSITIVE_INFINITY, 0.5,
            Category.NUCLEUS, 3.0, 0.0, 0x8EF0FC, true, true, false),
    HELIUM3("Helium-3 nucleus", "\u00b3He\u00b2\u207a", 2.0, Units.HELIUM3_MASS,
            Double.POSITIVE_INFINITY, 0.5, Category.NUCLEUS, 3.0, 0.0, 0xA5F3D0, true, true, false),
    ALPHA("Alpha particle", "\u2074He\u00b2\u207a", 2.0, Units.ALPHA_MASS, Double.POSITIVE_INFINITY,
            0.0, Category.NUCLEUS, 4.0, 0.0, 0xB5F3C0, true, true, false),
    CARBON12("Carbon-12 nucleus", "\u00b9\u00b2C\u2076\u207a", 6.0, 11177.9291,
            Double.POSITIVE_INFINITY, 0.0, Category.NUCLEUS, 12.0, 0.0, 0x9AD1D4, true, true, false),
    OXYGEN16("Oxygen-16 nucleus", "\u00b9\u2076O\u2078\u207a", 8.0, 14895.0775,
            Double.POSITIVE_INFINITY, 0.0, Category.NUCLEUS, 16.0, 0.0, 0x8FB8DE, true, true, false),
    IRON56("Iron-56 nucleus", "\u2075\u2076Fe\u00b2\u2076\u207a", 26.0, 52099.0,
            Double.POSITIVE_INFINITY, 0.0, Category.NUCLEUS, 56.0, 0.0, 0xB0A6A0, true, true, false),
    LEAD208("Lead-208 nucleus", "\u00b2\u2070\u2078Pb\u2078\u00b2\u207a", 82.0, 193687.0,
            Double.POSITIVE_INFINITY, 0.0, Category.NUCLEUS, 208.0, 0.0, 0x9E9E9E, true, true, false),
    URANIUM238("Uranium-238 nucleus", "\u00b2\u00b3\u2078U\u2079\u00b2\u207a", 92.0, 221694.9,
            1.41e17, 0.0, Category.NUCLEUS, 238.0, 0.0, 0x6FBF73, true, true, false),
    ION("Ion", "ion", 1.0, Units.PROTON_MASS, Double.POSITIVE_INFINITY, 0.0,
            Category.NUCLEUS, 1.0, 0.0, 0xBFD8B8, true, true, false);

    /** Broad classification used by the GUI and the detectors. */
    public enum Category {
        LEPTON,
        MESON,
        BARYON,
        NUCLEUS,
        BOSON
    }

    private final String displayName;
    private final String symbol;
    private final double charge;
    private final double mass;
    private final double lifetime;
    private final double spin;
    private final Category category;
    private final double baryonNumber;
    private final double leptonNumber;
    private final int colour;
    private final boolean charged;
    private final boolean hadronic;
    private final boolean penetrating;

    ParticleSpecies(String displayName, String symbol, double charge, double mass, double lifetime,
                    double spin, Category category, double baryonNumber, double leptonNumber,
                    int colour, boolean charged, boolean hadronic, boolean penetrating) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.charge = charge;
        this.mass = mass;
        this.lifetime = lifetime;
        this.spin = spin;
        this.category = category;
        this.baryonNumber = baryonNumber;
        this.leptonNumber = leptonNumber;
        this.colour = colour;
        this.charged = charged;
        this.hadronic = hadronic;
        this.penetrating = penetrating;
    }

    public String displayName() {
        return displayName;
    }

    public String symbol() {
        return symbol;
    }

    /** Charge in units of the elementary charge. */
    public double charge() {
        return charge;
    }

    /** Rest mass in MeV/c^2. */
    public double mass() {
        return mass;
    }

    /** Mean lifetime in seconds; {@link Double#POSITIVE_INFINITY} when stable. */
    public double lifetime() {
        return lifetime;
    }

    public double spin() {
        return spin;
    }

    public Category category() {
        return category;
    }

    public double baryonNumber() {
        return baryonNumber;
    }

    public double leptonNumber() {
        return leptonNumber;
    }

    public int colour() {
        return colour;
    }

    public boolean isCharged() {
        return charged;
    }

    public boolean isHadronic() {
        return hadronic;
    }

    /** True for particles that traverse the calorimeter and are seen by the muon detector. */
    public boolean isPenetrating() {
        return penetrating;
    }

    public boolean isStable() {
        return Double.isInfinite(lifetime);
    }

    public boolean isAntiparticle() {
        return this == POSITRON || this == ANTIPROTON || this == ANTINEUTRON || this == ANTIMUON
                || this == PION_MINUS || this == KAON_MINUS;
    }

    /** Antiparticle partner used by pair production style reactions. */
    public ParticleSpecies antiparticle() {
        return switch (this) {
            case ELECTRON -> POSITRON;
            case POSITRON -> ELECTRON;
            case PROTON -> ANTIPROTON;
            case ANTIPROTON -> PROTON;
            case NEUTRON -> ANTINEUTRON;
            case ANTINEUTRON -> NEUTRON;
            case MUON -> ANTIMUON;
            case ANTIMUON -> MUON;
            case PION_PLUS -> PION_MINUS;
            case PION_MINUS -> PION_PLUS;
            case KAON_PLUS -> KAON_MINUS;
            case KAON_MINUS -> KAON_PLUS;
            case NEUTRINO_E -> NEUTRINO_E;
            case NEUTRINO_MU -> NEUTRINO_MU;
            default -> this;
        };
    }

    /** Electric charge in coulomb. */
    public double chargeCoulomb() {
        return charge * Units.ELEMENTARY_CHARGE;
    }

    /** Mass in kilogram. */
    public double massKilogram() {
        return mass * 1.0e6 * Units.EV_TO_JOULE / (Units.C * Units.C);
    }

    /** Nucleon count for nuclei, 1 for baryons and 0 for everything else. */
    public int nucleons() {
        return switch (this) {
            case PROTON, ANTIPROTON, NEUTRON, ANTINEUTRON -> 1;
            case DEUTERON -> 2;
            case TRITON, HELIUM3 -> 3;
            case ALPHA -> 4;
            case CARBON12 -> 12;
            case OXYGEN16 -> 16;
            case IRON56 -> 56;
            case LEAD208 -> 208;
            case URANIUM238 -> 238;
            default -> 0;
        };
    }

    /** Atomic number for nucleus species, -1 when not a nucleus. */
    public int atomicNumber() {
        return switch (this) {
            case PROTON, ANTIPROTON -> 1;
            case DEUTERON -> 1;
            case TRITON -> 1;
            case HELIUM3, ALPHA -> 2;
            case CARBON12 -> 6;
            case OXYGEN16 -> 8;
            case IRON56 -> 26;
            case LEAD208 -> 82;
            case URANIUM238 -> 92;
            default -> -1;
        };
    }

    private static final java.util.Map<String, ParticleSpecies> BY_NAME = new java.util.HashMap<>();

    static {
        for (ParticleSpecies s : values()) {
            BY_NAME.put(s.name().toLowerCase(java.util.Locale.ROOT), s);
        }
    }

    public static ParticleSpecies byName(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** Species the accelerator can inject as a beam, ordered from the simplest to the heaviest. */
    public static List<ParticleSpecies> injectable() {
        List<ParticleSpecies> list = new ArrayList<>();
        for (ParticleSpecies s : values()) {
            if (s.isCharged()) {
                list.add(s);
            }
        }
        return list;
    }
}
