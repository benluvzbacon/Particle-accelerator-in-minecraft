package com.particlephysics.elements;

/** Stability classification of an element as a whole. */
public enum Stability {
    STABLE("All isotopes stable (or observationally stable)"),
    RADIOACTIVE("Has at least one primordial radioactive isotope"),
    SYNTHETIC("No stable isotopes, only produced artificially or in decay chains");

    private final String description;

    Stability(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    static Stability parse(String name) {
        return switch (name) {
            case "RADIOACTIVE" -> RADIOACTIVE;
            case "SYNTHETIC" -> SYNTHETIC;
            default -> STABLE;
        };
    }
}
