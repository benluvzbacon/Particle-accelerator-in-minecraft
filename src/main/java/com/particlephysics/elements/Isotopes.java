package com.particlephysics.elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Lookup and manipulation of the 532 tabulated nuclides. */
public final class Isotopes {
    private Isotopes() {
    }

    private static final Map<Long, Isotope> BY_ZA = new HashMap<>();
    private static final List<Isotope> ALL = new ArrayList<>();
    private static final Map<Integer, List<Isotope>> BY_Z = new HashMap<>();

    static {
        for (Element element : Elements.all()) {
            for (Isotope isotope : element.isotopes()) {
                ALL.add(isotope);
                BY_ZA.put(key(element.atomicNumber(), isotope.massNumber()), isotope);
                BY_Z.computeIfAbsent(element.atomicNumber(), z -> new ArrayList<>()).add(isotope);
            }
        }
    }

    private static long key(int z, int a) {
        return ((long) z << 20) | (a & 0xFFFFFL);
    }

    public static List<Isotope> all() {
        return ALL;
    }

    public static List<Isotope> forElement(Element element) {
        return BY_Z.getOrDefault(element.atomicNumber(), List.of());
    }

    /**
     * Finds a nuclide. When the exact mass number is not tabulated the closest tabulated nuclide
     * with the same proton number is returned, which keeps decay chains and reaction products
     * physically sensible.
     */
    public static Isotope find(int z, int a) {
        if (z < 1 || z > 118 || a < 1) {
            return null;
        }
        Isotope exact = BY_ZA.get(key(z, a));
        if (exact != null) {
            return exact;
        }
        List<Isotope> candidates = BY_Z.get(z);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        Isotope best = candidates.get(0);
        int bestDistance = Math.abs(best.massNumber() - a);
        for (Isotope candidate : candidates) {
            int distance = Math.abs(candidate.massNumber() - a) * 4
                    + (candidate.isStable() ? 0 : 1);
            if (distance < bestDistance * 4) {
                best = candidate;
                bestDistance = Math.abs(candidate.massNumber() - a);
            }
        }
        return best;
    }

    /** Parses notations such as "235U", "U-235" or "U235". */
    public static Isotope parse(String notation) {
        if (notation == null || notation.isEmpty()) {
            return null;
        }
        String digits = notation.replaceAll("[^0-9]", "");
        String symbol = notation.replaceAll("[^A-Za-z]", "");
        Element element = Elements.bySymbol(symbol);
        if (element == null || digits.isEmpty()) {
            return null;
        }
        return find(element.atomicNumber(), Integer.parseInt(digits));
    }

    /**
     * Picks an isotope of the element that is closest to the requested neutron number deviation.
     *
     * @param deltaNeutrons positive for neutron rich products (fission, spallation)
     */
    public static Isotope withNeutronShift(int z, int a, int deltaNeutrons) {
        return find(z, a + deltaNeutrons);
    }

    /** Random isotope of an element, weighted by natural abundance when available. */
    public static Isotope weightedRandom(Element element, Random random) {
        List<Isotope> isotopes = forElement(element);
        if (isotopes.isEmpty()) {
            return null;
        }
        double total = 0;
        for (Isotope i : isotopes) {
            total += i.naturalAbundance() > 0 ? i.naturalAbundance() : 0.5;
        }
        double roll = random.nextDouble() * total;
        for (Isotope i : isotopes) {
            roll -= i.naturalAbundance() > 0 ? i.naturalAbundance() : 0.5;
            if (roll <= 0) {
                return i;
            }
        }
        return isotopes.get(isotopes.size() - 1);
    }

    /** Isotopes that occur naturally on Earth. */
    public static List<Isotope> naturallyOccurring() {
        List<Isotope> list = new ArrayList<>();
        for (Isotope i : ALL) {
            if (i.naturalAbundance() > 0) {
                list.add(i);
            }
        }
        return list;
    }

    public static int count() {
        return ALL.size();
    }
}
