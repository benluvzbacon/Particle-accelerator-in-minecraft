package com.particlephysics.elements;

import java.util.ArrayList;
import java.util.List;

import com.particlephysics.physics.Units;

/** One chemical element: name, symbol, atomic number, atomic mass, category, stability, isotopes. */
public final class Element {
    private final int atomicNumber;
    private final String symbol;
    private final String name;
    private final ElementCategory category;
    private final double atomicMass;
    private final Stability stability;
    private final List<Isotope> isotopes = new ArrayList<>();

    public Element(int atomicNumber, String symbol, String name, ElementCategory category,
                   double atomicMass, Stability stability) {
        this.atomicNumber = atomicNumber;
        this.symbol = symbol;
        this.name = name;
        this.category = category;
        this.atomicMass = atomicMass;
        this.stability = stability;
    }

    public int atomicNumber() {
        return atomicNumber;
    }

    public String symbol() {
        return symbol;
    }

    public String name() {
        return name;
    }

    public ElementCategory category() {
        return category;
    }

    /** Standard atomic weight in u. */
    public double atomicMass() {
        return atomicMass;
    }

    /** Molar mass in g/mol. */
    public double molarMass() {
        return atomicMass;
    }

    public Stability stability() {
        return stability;
    }

    public List<Isotope> isotopes() {
        return isotopes;
    }

    void addIsotope(Isotope isotope) {
        isotopes.add(isotope);
    }

    /** Isotopes with a non-zero natural abundance. */
    public List<Isotope> naturalIsotopes() {
        List<Isotope> list = new ArrayList<>();
        for (Isotope i : isotopes) {
            if (i.naturalAbundance() > 0) {
                list.add(i);
            }
        }
        return list;
    }

    /** The most abundant stable isotope, or the longest lived one when none is stable. */
    public Isotope primaryIsotope() {
        Isotope best = null;
        double bestScore = -1;
        for (Isotope i : isotopes) {
            double score = i.isStable() ? 1.0e9 + i.naturalAbundance() : i.halfLifeSeconds();
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Total rest mass of one atom in MeV/c^2. */
    public double atomMassMeV() {
        return atomicMass * Units.AMU_TO_MEV;
    }

    /** Human readable one line description used by the quest book and tooltips. */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "%s (%s) Z=%d, %.4f u, %s, %s", name, symbol,
                atomicNumber, atomicMass, category.displayName(), stability.description());
    }

    @Override
    public String toString() {
        return symbol;
    }
}
