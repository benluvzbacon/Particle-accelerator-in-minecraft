package com.particlephysics.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * The eight chapters of the progression and their objectives.
 *
 * <p>Every objective is measurable, and all of them are evaluated from the research record and from
 * the state of the machine that is actually built. There are no placeholder quests: an objective
 * that says "reach 1 GeV" is satisfied by the beam energy the simulation reports.
 */
public final class QuestCatalog {
    /** How an objective is measured. */
    public enum Kind {
        ELEMENT_COUNT,
        ISOTOPE_COUNT,
        MILESTONE,
        UNLOCK,
        COLLISIONS,
        DOSE,
        MACHINE
    }

    public record Objective(String text, Kind kind, String key, double target, String unit) {
    }

    public record Chapter(String id, String title, String description, List<Objective> objectives) {
    }

    private QuestCatalog() {
    }

    private static Objective element(String text, double target) {
        return new Objective(text, Kind.ELEMENT_COUNT, "", target, "elements");
    }

    private static Objective isotope(String text, double target) {
        return new Objective(text, Kind.ISOTOPE_COUNT, "", target, "nuclides");
    }

    private static Objective milestone(String text, String key, double target, String unit) {
        return new Objective(text, Kind.MILESTONE, key, target, unit);
    }

    private static Objective unlock(String text, String key) {
        return new Objective(text, Kind.UNLOCK, key, 1.0, "");
    }

    private static Objective machine(String text, String key, double target, String unit) {
        return new Objective(text, Kind.MACHINE, key, target, unit);
    }

    public static List<Chapter> chapters() {
        List<Chapter> list = new ArrayList<>();

        list.add(new Chapter("foundations", "Foundations",
                "Mine the materials a laboratory is made of.\n"
                        + "Lead is the shielding material, copper carries the current, iron and "
                        + "niobium go into the magnets.",
                List.of(
                        element("Identify 5 elements in the materials you mine", 5),
                        element("Identify 12 elements", 12),
                        machine("Place a control computer", "computer", 1, ""),
                        unlock("Craft the accelerator blueprint", "crafted_blueprint"),
                        unlock("Craft the research journal", "crafted_journal"))));

        list.add(new Chapter("nuclear", "Nuclear Physics",
                "Nuclei, isotopes and decay.\n"
                        + "Every element of the periodic table has its isotopes, and unstable "
                        + "nuclides decay with realistic modes and compressed half lives.",
                List.of(
                        isotope("Discover 3 nuclides", 3),
                        isotope("Discover 8 nuclides", 8),
                        element("Collect a radioactive source (uranium or thorium)", 26),
                        unlock("Observe a decay chain: discover a daughter nuclide", "decay_chain"),
                        element("Discover an element you cannot mine", 40))));

        list.add(new Chapter("particle", "Particle Physics",
                "Relativistic beams, cross sections and events.\n"
                        + "sqrt(s) is computed from the energy and the collision geometry, and the "
                        + "products come from the real branching of the interaction.",
                List.of(
                        milestone("Record an experiment at 1 GeV", "max_sqrt_s_gev", 1.0, "GeV"),
                        milestone("Reach sqrt(s) = 10 GeV", "max_sqrt_s_gev", 10.0, "GeV"),
                        machine("Observe 10 collisions", "collisions", 10, ""),
                        unlock("Observe a rare process", "rare_observed"),
                        milestone("Record a 10-track event", "max_multiplicity", 10, "tracks"))));

        list.add(new Chapter("build", "Build the Accelerator",
                "Deploy the blueprint and follow the ghosts.\n"
                        + "The validator checks every block: missing components, wrong blocks and "
                        + "wrong orientations are reported with their coordinates.",
                List.of(
                        machine("Deploy a blueprint", "deploy", 1, ""),
                        machine("Place 10 magnets", "magnets", 10, ""),
                        machine("Place 4 RF cavities", "cavities", 4, ""),
                        machine("Shield 200 blocks of the tunnel", "shielding", 200, "blocks"),
                        machine("Complete the machine with zero validation errors", "complete", 1, ""),
                        machine("Provide 500 kW of power", "power", 500, "kW"))));

        list.add(new Chapter("firstbeam", "First Beam",
                "From the ion source to a circulating beam.\n"
                        + "Pump the pipe down to 1e-4 Pa, match the dipole field to the momentum, "
                        + "tune the quadrupoles into the stable region, then inject.",
                List.of(
                        machine("Pump the beam pipe below 1e-3 Pa", "vacuum", 1.0e-3, "Pa"),
                        machine("Pump the beam pipe below 1e-5 Pa", "vacuum", 1.0e-5, "Pa"),
                        machine("Inject a beam", "beam", 1, ""),
                        machine("Circulate a beam for one minute", "beam_time", 60, "s"),
                        machine("Correct the orbit to better than 10 mm", "orbit", 10, "mm"))));

        list.add(new Chapter("energy", "High Energy",
                "1 MeV to 100 TeV.\n"
                        + "Higher energy needs much more field, much more RF voltage and much more "
                        + "power: the RF voltage has to overcome the synchrotron radiation loss per "
                        + "turn, which grows with the fourth power of the energy per unit mass.",
                List.of(
                        machine("Reach 1 MeV", "energy", 1.0, "MeV"),
                        machine("Reach 100 MeV", "energy", 100.0, "MeV"),
                        machine("Reach 10 GeV", "energy", 1.0e4, "MeV"),
                        machine("Reach 1 TeV", "energy", 1.0e6, "MeV"),
                        machine("Reach 100 TeV", "energy", 1.0e8, "MeV"),
                        machine("Keep the beam alive for 10 minutes", "beam_time", 600, "s"))));

        list.add(new Chapter("elements", "Element Collection",
                "All 118 elements.\n"
                        + "Most of them do not exist in nature: you have to make them, by neutron "
                        + "capture, by spallation, by fission or by heavy ion fusion.",
                List.of(
                        element("Discover 40 elements", 40),
                        element("Discover 70 elements", 70),
                        element("Discover 92 elements (up to uranium)", 92),
                        element("Discover all 118 elements", 118))));

        list.add(new Chapter("ultimate", "The Ultimate Experiment",
                "Collide, detect, analyse.\n"
                        + "Bring the luminosity up, run every detector at once and collect the "
                        + "physics: rare processes are the reward.",
                List.of(
                        machine("Reach a luminosity of 1e30 cm-2 s-1", "luminosity", 1.0e30, ""),
                        machine("Observe 1000 collisions", "collisions", 1000, ""),
                        milestone("Reach sqrt(s) = 100 GeV", "max_sqrt_s_gev", 100.0, "GeV"),
                        machine("Keep the machine running without a fault for 30 minutes",
                                "fault_free", 1800, "s"),
                        unlock("Find a rare process at high energy", "rare_high_energy"))));

        return list;
    }

    public static Chapter byId(String id) {
        for (Chapter chapter : chapters()) {
            if (chapter.id().equals(id)) {
                return chapter;
            }
        }
        return null;
    }

    public static int count() {
        return chapters().size();
    }
}
