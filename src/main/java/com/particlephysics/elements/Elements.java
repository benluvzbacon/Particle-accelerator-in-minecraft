package com.particlephysics.elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Access to all 118 elements and their isotopes. */
public final class Elements {
    private Elements() {
    }

    private static final List<Element> ALL = new ArrayList<>();
    private static final Map<Integer, Element> BY_Z = new HashMap<>();
    private static final Map<String, Element> BY_SYMBOL = new HashMap<>();

    static {
        for (int i = 0; i < ElementData.COUNT; i++) {
            Element element = new Element(ElementData.Z[i], ElementData.SYMBOL[i],
                    ElementData.NAME[i], ElementCategory.parse(ElementData.CATEGORY[i]),
                    ElementData.ATOMIC_MASS[i], Stability.parse(ElementData.STABILITY[i]));
            ALL.add(element);
            BY_Z.put(element.atomicNumber(), element);
            BY_SYMBOL.put(element.symbol().toLowerCase(java.util.Locale.ROOT), element);
        }
        for (int i = 0; i < IsotopeData.COUNT; i++) {
            Element parent = BY_Z.get(IsotopeData.PARENT_Z[i]);
            if (parent == null) {
                continue;
            }
            Isotope isotope = new Isotope(parent, IsotopeData.MASS_NUMBER[i],
                    IsotopeData.ATOMIC_MASS[i], IsotopeData.HALF_LIFE_SECONDS[i],
                    DecayMode.parse(IsotopeData.DECAY_MODE[i]), IsotopeData.ABUNDANCE[i]);
            parent.addIsotope(isotope);
        }
    }

    public static List<Element> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Element byZ(int z) {
        return BY_Z.get(z);
    }

    public static Element bySymbol(String symbol) {
        return symbol == null ? null
                : BY_SYMBOL.get(symbol.toLowerCase(java.util.Locale.ROOT));
    }

    public static int count() {
        return ALL.size();
    }

    /** Elements with a stable isotope - the ones found naturally on Earth. */
    public static List<Element> naturallyOccurring() {
        List<Element> list = new ArrayList<>();
        for (Element e : ALL) {
            if (e.stability() != Stability.SYNTHETIC) {
                list.add(e);
            }
        }
        return list;
    }

    /** Elements the player cannot mine and must transmute. */
    public static List<Element> synthetic() {
        List<Element> list = new ArrayList<>();
        for (Element e : ALL) {
            if (e.stability() == Stability.SYNTHETIC) {
                list.add(e);
            }
        }
        return list;
    }
}
