package com.particlephysics.elements;

/** The classic periodic table categories. */
public enum ElementCategory {
    ALKALI_METAL("Alkali metal", 0xFFB74D),
    ALKALINE_EARTH_METAL("Alkaline earth metal", 0xFFD54F),
    TRANSITION_METAL("Transition metal", 0xFF8A65),
    POST_TRANSITION_METAL("Post-transition metal", 0xB0BEC5),
    METALLOID("Metalloid", 0x81C784),
    REACTIVE_NONMETAL("Reactive nonmetal", 0x4FC3F7),
    HALOGEN("Halogen", 0x4DD0E1),
    NOBLE_GAS("Noble gas", 0xB39DDB),
    LANTHANIDE("Lanthanide", 0xF06292),
    ACTINIDE("Actinide", 0xBA68C8),
    TRANSACTINIDE("Transactinide", 0x90A4AE);

    private final String displayName;
    private final int colour;

    ElementCategory(String displayName, int colour) {
        this.displayName = displayName;
        this.colour = colour;
    }

    public String displayName() {
        return displayName;
    }

    public int colour() {
        return colour;
    }

    static ElementCategory parse(String name) {
        return switch (name) {
            case "ALKALI" -> ALKALI_METAL;
            case "ALKALINE" -> ALKALINE_EARTH_METAL;
            case "TRANSITION" -> TRANSITION_METAL;
            case "POST_TRANSITION" -> POST_TRANSITION_METAL;
            case "METALLOID" -> METALLOID;
            case "NONMETAL" -> REACTIVE_NONMETAL;
            case "HALOGEN" -> HALOGEN;
            case "NOBLE_GAS" -> NOBLE_GAS;
            case "LANTHANIDE" -> LANTHANIDE;
            case "ACTINIDE" -> ACTINIDE;
            default -> TRANSACTINIDE;
        };
    }
}
