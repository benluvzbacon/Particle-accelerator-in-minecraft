package com.particlephysics.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Collision physics: cross sections, event generation and hadronisation.
 *
 * <p>The generators here are engineering approximations of the real thing, but they respect the
 * physics that makes the game believable:
 * <ul>
 *   <li>exact energy and momentum conservation (the remnants absorb the recoil),</li>
 *   <li>kinematic thresholds - you cannot create a pion before sqrt(s) allows it,</li>
 *   <li>multiplicity growing with sqrt(s) following the measured KNO scaling,</li>
 *   <li>cross sections with the correct 1/s behaviour, including the Z resonance and the
 *       e+e- -> hadrons R ratio,</li>
 *   <li>rare hard processes (W/Z/Higgs/top) appearing with a rate suppressed like 1/s so that
 *       hunting for resonances is a genuine experimental task.</li>
 * </ul>
 */
public final class Collisions {
    private Collisions() {
    }

    /** 1 barn in m^2. */
    public static final double BARN = 1.0e-28;
    /** hbar*c in MeV*m. */
    public static final double HBAR_C = 1.97327e-13;

    /** A single event product. */
    public record Product(ParticleSpecies species, double energyMeV, Vec3 momentum) {
        public double pT() {
            return Math.sqrt(momentum.x() * momentum.x() + momentum.z() * momentum.z());
        }

        public double pseudorapidity() {
            double p = momentum.length();
            if (p <= Math.abs(momentum.y()) || p < 1.0e-9) {
                return 0.0;
            }
            return 0.5 * Math.log((p + momentum.y()) / (p - momentum.y()));
        }
    }

    /** Everything a detector would record about one interaction. */
    public static final class Event {
        public String channel = "elastic";
        /** Centre of mass energy in GeV. */
        public double sqrtS;
        /** All stable-ish final state particles. */
        public final List<Product> products = new ArrayList<>();
        public int chargedMultiplicity;
        public int totalMultiplicity;
        /** True when a hard / rare process was produced (W, Z, Higgs, top, jet). */
        public boolean rare;
        public String rareProcess;
        /** Invariant mass of the rare resonance in GeV, 0 when not applicable. */
        public double resonanceMass;
        /** Highest transverse momentum in the event, in GeV/c. */
        public double leadingPT;
        /** Energy of all neutrinos leaving the detector, in GeV. */
        public double missingEnergy;
        /** Number of nucleon participants for heavy ion collisions. */
        public int participants;
        /** Approximate impact parameter in fm. */
        public double impactParameter;

        public double totalEnergy() {
            double sum = 0;
            for (Product p : products) {
                sum += p.energyMeV() + p.species().mass();
            }
            return sum;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Cross sections
    // ------------------------------------------------------------------------------------------

    /** Total proton-proton cross section in m^2 (measured shape, parameterised). */
    public static double protonProtonCrossSection(double sqrtSGeV) {
        double lnSqrtS = Math.log(Math.max(2.0, sqrtSGeV));
        double millibarn = 52.0 + 18.0 * (lnSqrtS - 6.3);
        millibarn = Math.max(20.0, Math.min(140.0, millibarn));
        return millibarn * 1.0e-3 * BARN;
    }

    /** Antiproton-proton cross section: larger at low energy, converging at high energy. */
    public static double antiprotonProtonCrossSection(double sqrtSGeV) {
        double pp = protonProtonCrossSection(sqrtSGeV);
        return pp * (1.0 + 2.5 / Math.max(1.0, sqrtSGeV));
    }

    /**
     * Point cross section 4*pi*alpha^2/(3s) in m^2, the baseline for e+e- annihilation.
     */
    public static double pointCrossSection(double sqrtSGeV) {
        double sMeV2 = sqrtSGeV * 1000.0 * sqrtSGeV * 1000.0;
        return 4.0 * Math.PI * Units.ALPHA * Units.ALPHA / (3.0 * sMeV2)
                * HBAR_C * HBAR_C;
    }

    /** Hadronic R ratio including the J/psi, Upsilon and Z resonances. */
    public static double rRatio(double sqrtSGeV) {
        double r = 3.6;
        if (sqrtSGeV > 3.0) {
            r += 1.2;
        }
        if (sqrtSGeV > 9.5) {
            r += 1.3;
        }
        if (sqrtSGeV > 40.0) {
            r += 1.0;
        }
        // Z resonance: Breit-Wigner peak on top of the continuum
        r += 1200.0 * breitWigner(sqrtSGeV, 91.1876, 2.4952);
        // J/psi and Upsilon
        r += 60.0 * breitWigner(sqrtSGeV, 3.0969, 9.3e-5);
        r += 40.0 * breitWigner(sqrtSGeV, 9.4603, 5.4e-5);
        return r;
    }

    private static double breitWigner(double mass, double pole, double width) {
        double dm = mass - pole;
        return (width * width / 4.0) / (dm * dm + width * width / 4.0);
    }

    /** e+e- total cross section in m^2 including all modelled channels. */
    public static double electronPositronCrossSection(double sqrtSGeV) {
        double point = pointCrossSection(sqrtSGeV);
        return point * (rRatio(sqrtSGeV) + 1.0 /* Bhabha */ + 1.0 /* muon pairs */);
    }

    /** Total cross section for a pair of species, in m^2. */
    public static double totalCrossSection(ParticleSpecies a, ParticleSpecies b, double sqrtSGeV) {
        boolean leptons = a.category() == ParticleSpecies.Category.LEPTON
                && b.category() == ParticleSpecies.Category.LEPTON;
        if (leptons) {
            return electronPositronCrossSection(sqrtSGeV);
        }
        boolean antimatter = a.isAntiparticle() != b.isAntiparticle();
        boolean baryons = a.baryonNumber() != 0 && b.baryonNumber() != 0;
        if (baryons) {
            if (a.nucleons() > 4 || b.nucleons() > 4) {
                // heavy ions: geometric scaling with the nuclear radii
                double ra = 1.2e-15 * Math.cbrt(Math.max(1, a.nucleons()));
                double rb = 1.2e-15 * Math.cbrt(Math.max(1, b.nucleons()));
                return Math.PI * Math.pow(ra + rb, 2) * 1.4;
            }
            return antimatter ? antiprotonProtonCrossSection(sqrtSGeV)
                    : protonProtonCrossSection(sqrtSGeV);
        }
        // meson / lepton-hadron mixtures: geometric estimate
        return 30.0e-3 * BARN;
    }

    // ------------------------------------------------------------------------------------------
    // Event generation
    // ------------------------------------------------------------------------------------------

    /**
     * Generates a collision event between two particles.
     *
     * @param energyA kinetic energy of particle A in MeV
     * @param energyB kinetic energy of particle B in MeV; for colliding beams this is the energy of
     *                the counter-circulating bunch, for fixed target physics pass 0.
     */
    public static Event collide(ParticleSpecies a, double energyA, Vec3 directionA,
                               ParticleSpecies b, double energyB, Vec3 directionB, Random random) {
        Event event = new Event();
        double totalA = energyA + a.mass();
        double totalB = energyB + b.mass();
        double pA = Relativity.momentumFromKinetic(energyA, a.mass());
        double pB = Relativity.momentumFromKinetic(energyB, b.mass());
        double cosAngle = directionA.normalise().dot(directionB.normalise());
        double sqrtS = Relativity.centreOfMassEnergy(totalA, pA, a.mass(), totalB, pB, b.mass(),
                cosAngle);
        event.sqrtS = sqrtS / 1000.0;

        boolean leptons = a.category() == ParticleSpecies.Category.LEPTON
                && b.category() == ParticleSpecies.Category.LEPTON;
        if (leptons && a.isAntiparticle() != b.isAntiparticle()
                && Math.abs(a.charge() + b.charge()) < 1.0e-9) {
            generateAnnihilation(event, sqrtS, directionA, random);
        } else if (a.nucleons() > 4 || b.nucleons() > 4) {
            generateHeavyIon(event, a, b, sqrtS, directionA, random);
        } else if (a.baryonNumber() != 0 && b.baryonNumber() != 0) {
            boolean annihilation = a.isAntiparticle() != b.isAntiparticle();
            generateHadronic(event, a, b, sqrtS, directionA, random, annihilation);
        } else {
            generateHadronic(event, a, b, sqrtS, directionA, random, false);
        }
        summarise(event);
        return event;
    }

    /**
     * Fixed target event: the beam hits a stationary target nucleus.
     */
    public static Event fixedTarget(ParticleSpecies beam, double energyMeV, int targetZ,
                                    int targetA, Random random) {
        ParticleSpecies target = speciesForNucleus(targetZ, targetA);
        Event event = collide(beam, energyMeV, new Vec3(0, 0, 1), target, 0.0,
                new Vec3(0, 0, 0), random);
        event.channel = "fixed target: " + beam.symbol() + " on Z=" + targetZ + " A=" + targetA;
        return event;
    }

    private static void generateAnnihilation(Event event, double sqrtS, Vec3 direction,
                                             Random random) {
        double sqrtSGeV = sqrtS / 1000.0;
        double roll = random.nextDouble();
        double point = pointCrossSection(sqrtSGeV);
        double hadronic = point * rRatio(sqrtSGeV);
        double muonic = point;
        double bhabha = point * 1.0;
        double total = hadronic + muonic + bhabha;

        // Z pole: the resonance decays to fermion pairs, including neutrinos (invisible)
        if (breitWigner(sqrtSGeV, 91.1876, 2.4952) > 0.05) {
            event.rare = true;
            event.rareProcess = "Z boson";
            event.resonanceMass = 91.1876;
            double channel = random.nextDouble();
            if (channel < 0.20) {
                emitPair(event, ParticleSpecies.NEUTRINO_E, ParticleSpecies.NEUTRINO_E, sqrtS,
                        direction, random);
                event.missingEnergy = sqrtSGeV;
            } else if (channel < 0.35) {
                emitPair(event, ParticleSpecies.MUON, ParticleSpecies.ANTIMUON, sqrtS, direction,
                        random);
            } else if (channel < 0.5) {
                emitPair(event, ParticleSpecies.ELECTRON, ParticleSpecies.POSITRON, sqrtS,
                        direction, random);
            } else {
                hadronise(event, sqrtS, 0.0, direction, random, 0);
            }
            return;
        }

        if (roll * total < muonic) {
            event.channel = "e+e- -> mu+mu-";
            emitPair(event, ParticleSpecies.MUON, ParticleSpecies.ANTIMUON, sqrtS, direction,
                    random);
        } else if (roll * total < muonic + bhabha) {
            event.channel = "e+e- -> e+e- (Bhabha)";
            emitPair(event, ParticleSpecies.ELECTRON, ParticleSpecies.POSITRON, sqrtS, direction,
                    random);
        } else {
            event.channel = "e+e- -> hadrons";
            hadronise(event, sqrtS, 0.0, direction, random, 0);
        }
    }

    private static void emitPair(Event event, ParticleSpecies a, ParticleSpecies b, double sqrtS,
                                 Vec3 direction, Random random) {
        // analytic two body splitting: each daughter carries sqrt(s)/2 energy in the centre of mass
        double energy = sqrtS / 2.0;
        double momentum = Math.sqrt(Math.max(0.0, energy * energy - a.mass() * a.mass()));
        Vec3 unit = direction.normalise();
        event.products.add(new Product(a, energy - a.mass(), unit.scale(momentum)));
        event.products.add(new Product(b, energy - b.mass(), unit.scale(-momentum)));
    }

    private static void generateHadronic(Event event, ParticleSpecies a, ParticleSpecies b,
                                         double sqrtS, Vec3 direction, Random random,
                                         boolean annihilation) {
        double sqrtSGeV = sqrtS / 1000.0;
        double massSum = a.mass() + b.mass();
        if (sqrtS <= massSum * 1.02) {
            event.channel = "elastic";
            emitPair(event, a, b, sqrtS, direction, random);
            return;
        }

        double inelasticity = annihilation ? 0.95 : 0.15 + 0.45 * random.nextDouble();
        double centralMass = sqrtS * Math.sqrt(inelasticity);
        // threshold check: at least one pion must fit in the central system
        if (centralMass < Units.PION_CHARGED_MASS * 2.0) {
            event.channel = "quasi-elastic";
            emitPair(event, a, b, sqrtS, direction, random);
            return;
        }

        double hardProbability = Math.min(0.25, 4.0e-4 * Math.pow(sqrtSGeV, 1.4)
                / Math.max(1.0, protonProtonCrossSection(sqrtSGeV) / BARN * 1000.0));
        boolean hard = !annihilation && random.nextDouble() < Math.max(0.002, hardProbability);
        if (hard) {
            // a hard parton-parton scattering creates two high pT jets, and above threshold the
            // electroweak resonances can be produced for real.
            if (sqrtSGeV > 80.0 && random.nextDouble() < 0.0025) {
                event.rare = true;
                if (sqrtSGeV > 173.0 && random.nextDouble() < 0.25) {
                    event.rareProcess = "top quark pair";
                    event.resonanceMass = 173.0;
                } else if (sqrtSGeV > 125.0 && random.nextDouble() < 0.4) {
                    event.rareProcess = "Higgs boson candidate";
                    event.resonanceMass = 125.25;
                } else if (sqrtSGeV > 91.2) {
                    event.rareProcess = "Z boson";
                    event.resonanceMass = 91.1876;
                } else {
                    event.rareProcess = "W boson";
                    event.resonanceMass = 80.377;
                }
            } else if (sqrtSGeV > 3.1 && random.nextDouble() < 0.01) {
                event.rare = true;
                event.rareProcess = "charmonium (J/psi) candidate";
                event.resonanceMass = 3.0969;
            }
        }

        int generated = hadronise(event, sqrtS, centralMass, direction, random,
                annihilation ? -1 : 0);
        if (hard) {
            event.channel = (a.isAntiparticle() != b.isAntiparticle() ? "p pbar" : "p p")
                    + " hard scattering, " + generated + " charged";
        } else {
            event.channel = (a.isAntiparticle() != b.isAntiparticle() ? "p pbar" : "p p")
                    + " inelastic, " + generated + " charged";
        }
        addRemnants(event, a, b, sqrtS, centralMass, direction);
    }

    /**
     * Statistical hadronisation: samples a multiplicity from the measured KNO/NBD scaling and
     * distributes the available energy among pions, kaons and nucleons.
     *
     * @return the charged multiplicity
     */
    private static int hadronise(Event event, double sqrtS, double centralMass, Vec3 direction,
                                 Random random, int annihilation) {
        double sqrtSGeV = sqrtS / 1000.0;
        double meanCharged;
        if (sqrtSGeV < 50.0) {
            meanCharged = 2.0 + 1.3 * Math.log(Math.max(1.2, sqrtSGeV));
        } else {
            double ln = Math.log(sqrtSGeV);
            meanCharged = 94.2 - 27.97 * ln + 2.795 * ln * ln;
        }
        if (annihilation > 0) {
            meanCharged = 2.0 + 1.9 * Math.log(Math.max(1.2, sqrtSGeV));
        }
        meanCharged = Math.max(2.0, meanCharged) * 0.62;

        int multiplicity = sampleNegativeBinomial(random, meanCharged, 5.0);
        double available = centralMass > 0 ? centralMass : sqrtS * 0.35;

        List<Product> central = new ArrayList<>();
        double energyBudget = available;
        double totalMass = 0.0;
        for (int i = 0; i < multiplicity; i++) {
            ParticleSpecies species = sampleHadron(random);
            double mass = species.mass();
            if (mass > energyBudget) {
                species = Units.PION_CHARGED_MASS < energyBudget
                        ? (random.nextBoolean() ? ParticleSpecies.PION_PLUS
                        : ParticleSpecies.PION_MINUS)
                        : ParticleSpecies.PHOTON;
                mass = species.mass();
            }
            totalMass += mass;
            energyBudget -= mass;

            double pT = sampleTransverseMomentum(random, sqrtSGeV);
            double phi = random.nextDouble() * Math.PI * 2.0;
            double y = (random.nextDouble() * 2.0 - 1.0) * Math.log(Math.max(2.0, sqrtSGeV));
            double pz = pT * Math.sinh(y);
            double p2 = pT * pT + pz * pz;
            double energy = Math.sqrt(Math.max(0.0, p2 + mass * mass));
            Vec3 momentum = new Vec3(pT * Math.cos(phi), pz, pT * Math.sin(phi));
            central.add(new Product(species, energy - mass, momentum));
        }

        // Rescale the central system so that it is at rest and carries exactly `available` energy:
        // this is the momentum reshuffling step that guarantees four-momentum conservation.
        double sumPx = 0;
        double sumPy = 0;
        double sumPz = 0;
        double sumE = 0;
        for (Product p : central) {
            sumPx += p.momentum().x();
            sumPy += p.momentum().y();
            sumPz += p.momentum().z();
            sumE += p.energyMeV() + p.species().mass();
        }
        if (central.isEmpty()) {
            return 0;
        }
        double kx = -sumPx / central.size();
        double ky = -sumPy / central.size();
        double kz = -sumPz / central.size();
        double scale = sumE > 1.0e-9 ? available / sumE : 1.0;
        for (Product p : central) {
            Vec3 m = p.momentum().add(new Vec3(kx, ky, kz)).scale(scale);
            double energy = Math.sqrt(Math.max(0.0, m.lengthSquared()
                    + p.species().mass() * p.species().mass()));
            event.products.add(new Product(p.species(), energy - p.species().mass(), m));
        }

        // Rotate the central system into the laboratory frame (the centre of mass system of a
        // colliding beam machine is already the laboratory frame, so no boost is applied here).
        rotateIntoLab(event, direction, 0.0, sqrtS);
        return multiplicity;
    }

    private static void addRemnants(Event event, ParticleSpecies a, ParticleSpecies b, double sqrtS,
                                    double centralMass, Vec3 direction) {
        double remaining = Math.max(0.0, sqrtS - centralMass - Units.PION_CHARGED_MASS * 2.0);
        double energyEach = remaining / 2.0;
        ParticleSpecies remnantA = a.nucleons() > 1 ? a : ParticleSpecies.PROTON;
        ParticleSpecies remnantB = b.nucleons() > 1 ? b : ParticleSpecies.PROTON;
        if (a.isAntiparticle()) {
            remnantA = ParticleSpecies.ANTIPROTON;
        }
        if (b.isAntiparticle()) {
            remnantB = ParticleSpecies.ANTIPROTON;
        }
        double momentum = Math.sqrt(Math.max(0.0,
                energyEach * energyEach - Units.PROTON_MASS * Units.PROTON_MASS));
        Vec3 unit = direction.normalise();
        event.products.add(new Product(remnantA, Math.max(0.0, energyEach - Units.PROTON_MASS),
                unit.scale(momentum)));
        event.products.add(new Product(remnantB, Math.max(0.0, energyEach - Units.PROTON_MASS),
                unit.scale(-momentum)));
    }

    private static void generateHeavyIon(Event event, ParticleSpecies a, ParticleSpecies b,
                                         double sqrtS, Vec3 direction, Random random) {
        // Glauber-like participant estimate
        int nucleonA = Math.max(1, a.nucleons());
        int nucleonB = Math.max(1, b.nucleons());
        double bMax = 1.2e-15 * (Math.cbrt(nucleonA) + Math.cbrt(nucleonB));
        double impact = Math.cbrt(random.nextDouble()) * bMax;
        double overlap = Math.max(0.0, 1.0 - Math.pow(impact / Math.max(1.0e-15, bMax), 2));
        int participants = (int) Math.max(2, (nucleonA + nucleonB) * (0.25 + 0.75 * overlap));
        event.participants = participants;
        event.impactParameter = impact * 1.0e15;
        event.channel = "heavy ion " + a.symbol() + " + " + b.symbol() + " (" + participants
                + " participants)";

        double sqrtSGeV = sqrtS / 1000.0;
        double centralMass = sqrtS * (0.35 + 0.45 * overlap);

        // fireball temperature grows with the energy density
        double temperature = 0.14 + 0.00004 * sqrtSGeV * overlap;
        int multiplicity = (int) Math.min(400,
                participants * (2.2 + 1.5 * Math.log(Math.max(2.0, sqrtSGeV)) * (0.4 + 0.6 * overlap)));
        event.rare = multiplicity > 60;
        if (event.rare) {
            event.rareProcess = "quark gluon plasma candidate";
        }
        double available = centralMass;
        List<Product> central = new ArrayList<>();
        for (int i = 0; i < multiplicity && available > Units.PION_CHARGED_MASS; i++) {
            ParticleSpecies species = sampleThermalHadron(random, temperature);
            double mass = species.mass();
            if (mass > available) {
                continue;
            }
            available -= mass;
            double pT = sampleTransverseMomentum(random, sqrtSGeV);
            double phi = random.nextDouble() * Math.PI * 2.0;
            double y = (random.nextDouble() * 2.0 - 1.0) * Math.log(Math.max(2.0, sqrtSGeV));
            double pz = pT * Math.sinh(y);
            Vec3 momentum = new Vec3(pT * Math.cos(phi), pz, pT * Math.sin(phi));
            double energy = Math.sqrt(momentum.lengthSquared() + mass * mass);
            central.add(new Product(species, energy - mass, momentum));
        }
        double sumE = 0;
        double sumPz = 0;
        for (Product p : central) {
            sumE += p.energyMeV() + p.species().mass();
            sumPz += p.momentum().y();
        }
        if (central.isEmpty()) {
            return;
        }
        double scale = sumE > 1.0e-9 ? centralMass / sumE : 1.0;
        for (Product p : central) {
            Vec3 m = new Vec3(p.momentum().x(), p.momentum().y() - sumPz / central.size(),
                    p.momentum().z()).scale(scale);
            double energy = Math.sqrt(m.lengthSquared()
                    + p.species().mass() * p.species().mass());
            event.products.add(new Product(p.species(), energy - p.species().mass(), m));
        }
        rotateIntoLab(event, direction, 0.0, sqrtS);
    }

    /** Rotates the (currently centre-of-mass frame) products into the laboratory frame. */
    private static void rotateIntoLab(Event event, Vec3 direction, double betaCm, double sqrtS) {
        Vec3 unit = direction.normalise();
        Vec3[] basis = Decays.orthonormalBasis(unit);
        Vec3 e1 = basis[0];
        Vec3 e2 = basis[1];
        double gamma = 1.0 / Math.sqrt(Math.max(1.0e-9, 1.0 - betaCm * betaCm));
        List<Product> converted = new ArrayList<>(event.products.size());
        for (Product p : event.products) {
            double pxLocal = p.momentum().dot(e1);
            double pyLocal = p.momentum().dot(e2);
            double pzLocal = p.momentum().dot(unit);
            double energy = Math.sqrt(p.momentum().lengthSquared()
                    + p.species().mass() * p.species().mass());
            double newPz = gamma * (pzLocal + betaCm * energy);
            double newEnergy = gamma * (energy + betaCm * pzLocal);
            Vec3 momentum = e1.scale(pxLocal).add(e2.scale(pyLocal)).add(unit.scale(newPz));
            converted.add(new Product(p.species(), Math.max(0.0, newEnergy - p.species().mass()),
                    momentum));
        }
        event.products.clear();
        event.products.addAll(converted);
    }

    private static void summarise(Event event) {
        int charged = 0;
        double leading = 0;
        double missing = 0;
        for (Product p : event.products) {
            if (p.species().isCharged()) {
                charged++;
            }
            leading = Math.max(leading, Math.sqrt(p.momentum().x() * p.momentum().x()
                    + p.momentum().z() * p.momentum().z()));
            if (p.species() == ParticleSpecies.NEUTRINO_E
                    || p.species() == ParticleSpecies.NEUTRINO_MU) {
                missing += p.energyMeV() + p.species().mass();
            }
        }
        event.chargedMultiplicity = charged;
        event.totalMultiplicity = event.products.size();
        event.leadingPT = leading / 1000.0;
        if (missing > 0) {
            event.missingEnergy = missing / 1000.0;
        }
    }

    private static ParticleSpecies sampleHadron(Random random) {
        double roll = random.nextDouble();
        if (roll < 0.42) {
            return ParticleSpecies.PION_PLUS;
        }
        if (roll < 0.84) {
            return ParticleSpecies.PION_MINUS;
        }
        if (roll < 0.90) {
            return ParticleSpecies.PION_NEUTRAL;
        }
        if (roll < 0.935) {
            return ParticleSpecies.KAON_PLUS;
        }
        if (roll < 0.965) {
            return ParticleSpecies.KAON_MINUS;
        }
        if (roll < 0.978) {
            return ParticleSpecies.KAON_NEUTRAL;
        }
        if (roll < 0.988) {
            return ParticleSpecies.PROTON;
        }
        if (roll < 0.994) {
            return ParticleSpecies.NEUTRON;
        }
        if (roll < 0.998) {
            return ParticleSpecies.ANTIPROTON;
        }
        return ParticleSpecies.PHOTON;
    }

    /** Boltzmann-like sampling of a hadron at the fireball temperature (in GeV). */
    private static ParticleSpecies sampleThermalHadron(Random random, double temperatureGeV) {
        double total = 0;
        ParticleSpecies[] candidates = {ParticleSpecies.PION_PLUS, ParticleSpecies.PION_MINUS,
                ParticleSpecies.PION_NEUTRAL, ParticleSpecies.KAON_PLUS, ParticleSpecies.KAON_MINUS,
                ParticleSpecies.PROTON, ParticleSpecies.NEUTRON, ParticleSpecies.PHOTON};
        double[] weights = new double[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            double mass = candidates[i].mass() / 1000.0;
            double degeneracy = switch (candidates[i]) {
                case PION_PLUS, PION_MINUS, PION_NEUTRAL, KAON_PLUS, KAON_MINUS, PHOTON -> 1.0;
                default -> 1.5;
            };
            weights[i] = degeneracy * Math.pow(mass, 1.5)
                    * Math.exp(-mass / Math.max(0.05, temperatureGeV));
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < candidates.length; i++) {
            roll -= weights[i];
            if (roll <= 0) {
                return candidates[i];
            }
        }
        return ParticleSpecies.PION_PLUS;
    }

    private static double sampleTransverseMomentum(Random random, double sqrtSGeV) {
        // exponential spectrum with a power law tail (min bias + jets)
        double temperature = 0.16 + 0.0002 * sqrtSGeV;
        double pT = -temperature * Math.log(Math.max(1.0e-9, random.nextDouble()));
        if (random.nextDouble() < 0.02) {
            pT += Math.pow(random.nextDouble(), -0.3);
        }
        return Math.min(pT, sqrtSGeV / 2.0) * 1000.0;
    }

    /** Samples a negative binomial multiplicity distribution (KNO scaling). */
    public static int sampleNegativeBinomial(Random random, double mean, double k) {
        if (mean <= 0) {
            return 0;
        }
        double p = k / (k + mean);
        double successes = 0;
        for (int i = 0; i < k; i++) {
            if (random.nextDouble() < p) {
                successes++;
            }
        }
        // Approximate: scale the binomial by the negative binomial shape
        double gauss = mean + Math.sqrt(mean * (1 + mean / k)) * random.nextGaussian();
        return (int) Math.max(0, Math.round(gauss));
    }

    /** Maps a nucleus to a species the simulation knows about. */
    public static ParticleSpecies speciesForNucleus(int z, int a) {
        if (z == 1 && a == 1) {
            return ParticleSpecies.PROTON;
        }
        if (z == 1 && a == 2) {
            return ParticleSpecies.DEUTERON;
        }
        if (z == 1 && a == 3) {
            return ParticleSpecies.TRITON;
        }
        if (z == 2 && a == 3) {
            return ParticleSpecies.HELIUM3;
        }
        if (z == 2 && a == 4) {
            return ParticleSpecies.ALPHA;
        }
        if (z == 6 && a == 12) {
            return ParticleSpecies.CARBON12;
        }
        if (z == 8 && a == 16) {
            return ParticleSpecies.OXYGEN16;
        }
        if (z == 26 && a == 56) {
            return ParticleSpecies.IRON56;
        }
        if (z == 82 && a == 208) {
            return ParticleSpecies.LEAD208;
        }
        if (z == 92 && a == 238) {
            return ParticleSpecies.URANIUM238;
        }
        return ParticleSpecies.ION;
    }
}
