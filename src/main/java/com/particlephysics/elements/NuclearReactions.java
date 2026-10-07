package com.particlephysics.elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.particlephysics.physics.ParticleSpecies;
import com.particlephysics.physics.Units;

/**
 * Nuclear reactions: the only way to make new elements.
 *
 * <p>Four mechanisms are modelled, all of them with the correct energy dependence:
 * <ul>
 *   <li><b>Fusion</b> needs the projectile to tunnel through the Coulomb barrier: the cross section
 *       contains the Sommmerfeld parameter eta and therefore grows exponentially once the beam
 *       energy approaches E_c = Z1 Z2 e^2 /(4 pi eps0 R), i.e. several MeV for light nuclei and
 *       hundreds of MeV for lead on lead. This is why the accelerator is required.</li>
 *   <li><b>Neutron capture</b> follows the 1/v law and works with thermal neutrons produced by the
 *       machine (or by spontaneous fission).</li>
 *   <li><b>Spallation</b> at high energy removes several nucleons from the target, producing the
 *       neutron deficient isotopes used for research.</li>
 *   <li><b>Fission</b> of actinides produces the asymmetric light/heavy fragment pairs.</li>
 * </ul>
 */
public final class NuclearReactions {
    private NuclearReactions() {
    }

    /** A reaction channel with its products and probability weight. */
    public record Reaction(String kind, String description, List<Isotope> products,
                           double crossSectionBarn, double energyReleasedMeV) {
        public Isotope primaryProduct() {
            return products.isEmpty() ? null : products.get(0);
        }
    }

    /** Coulomb barrier between two nuclei in MeV. */
    public static double coulombBarrierMeV(int z1, int a1, int z2, int a2) {
        double r = 1.2e-15 * (Math.cbrt(a1) + Math.cbrt(a2)) + 2.0e-15;
        double energyJoules = z1 * z2 * Units.ELEMENTARY_CHARGE * Units.ELEMENTARY_CHARGE
                / (4.0 * Math.PI * Units.EPSILON_0 * r);
        return energyJoules / (1.0e6 * Units.EV_TO_JOULE);
    }

    /** Gamow factor exp(-2 pi eta) for the given centre of mass energy. */
    public static double gamowFactor(double energyMeV, double barrierMeV) {
        if (energyMeV <= 0 || barrierMeV <= 0) {
            return 0.0;
        }
        double exponent = -Math.PI * Math.sqrt(4.0 * barrierMeV / Math.max(1.0e-9, energyMeV));
        return Math.exp(Math.max(-700.0, exponent));
    }

    /** Fusion cross section in barn, geometric times the tunnelling probability. */
    public static double fusionCrossSectionBarn(double energyMeV, int z1, int a1, int z2, int a2) {
        double barrier = coulombBarrierMeV(z1, a1, z2, a2);
        double r = 1.2e-15 * (Math.cbrt(a1) + Math.cbrt(a2));
        double geometric = Math.PI * r * r * 1.0e28; // barn
        double tunnelling = gamowFactor(energyMeV, barrier);
        double aboveBarrier = energyMeV > barrier ? 1.0 + 0.5 * (energyMeV / barrier - 1.0)
                * Math.exp(-(energyMeV / barrier - 1.0) * 0.2) : 1.0;
        return geometric * Math.min(1.0, tunnelling * aboveBarrier);
    }

    /**
     * Performs a reaction of a projectile on a target.
     *
     * @param projectile     beam species
     * @param energyMeV      projectile kinetic energy
     * @param target         target element
     * @return a reaction channel, or null when nothing happens (beam passes through)
     */
    public static Reaction react(ParticleSpecies projectile, double energyMeV, Element target,
                                 Random random) {
        int z1 = projectile.atomicNumber() >= 0 ? projectile.atomicNumber()
                : (int) Math.abs(projectile.charge());
        int a1 = projectile.nucleons() > 0 ? projectile.nucleons() : 1;
        if (projectile == ParticleSpecies.NEUTRON) {
            z1 = 0;
            a1 = 1;
        }
        if (projectile == ParticleSpecies.PHOTON) {
            return photonuclear(energyMeV, target, random);
        }
        if (projectile == ParticleSpecies.ELECTRON || projectile == ParticleSpecies.POSITRON) {
            // electrons do not fuse; they produce bremsstrahlung photons and can photodisintegrate
            double chance = Math.min(0.5, energyMeV / 40.0);
            if (random.nextDouble() < chance) {
                return photonuclear(energyMeV * 0.4, target, random);
            }
            return null;
        }
        if (z1 == 0) {
            return neutronCapture(target, random);
        }
        int z2 = target.atomicNumber();
        int a2 = (int) Math.round(target.atomicMass());

        double barrier = coulombBarrierMeV(z1, a1, z2, a2);
        double sigma = fusionCrossSectionBarn(energyMeV, z1, a1, z2, a2);

        // spallation / fragmentation at high energy
        if (energyMeV > 100.0 * Math.max(1, z2) / 20.0 && random.nextDouble() < 0.6) {
            return spallation(energyMeV, z1, a2, target, random);
        }
        if (energyMeV > 5.0 * Math.max(1.0, barrier) && random.nextDouble() < 0.35) {
            return spallation(energyMeV, z1, a2, target, random);
        }
        if (sigma <= 1.0e-12 || random.nextDouble() > Math.min(1.0, sigma / 1.0e-6)) {
            return null;
        }
        return fuse(z1, a1, z2, a2, energyMeV, random, sigma);
    }

    /** Compound nucleus formation with subsequent evaporation of a few nucleons. */
    private static Reaction fuse(int z1, int a1, int z2, int a2, double energyMeV, Random random,
                                 double sigma) {
        int zCompound = z1 + z2;
        int aCompound = a1 + a2 + (int) Math.round(energyMeV / 60.0);
        List<Isotope> products = new ArrayList<>();
        if (zCompound > 118) {
            // the compound nucleus fissions immediately (this is how superheavy elements fail)
            Isotope heavy = Isotopes.find(Math.min(118, zCompound - 10), aCompound / 2 + 20);
            Isotope light = Isotopes.find(Math.min(118, zCompound - 24), aCompound / 2 - 20);
            if (heavy != null) {
                products.add(heavy);
            }
            if (light != null) {
                products.add(light);
            }
            return new Reaction("fission", "Compound nucleus " + zCompound + " splits (fission)",
                    products, sigma * 0.9, 180.0);
        }
        Isotope compound = Isotopes.find(zCompound, aCompound);
        if (compound == null) {
            return null;
        }
        products.add(compound);
        // evaporation of 1-4 nucleons, mostly neutrons, some alphas
        int evaporated = 1 + random.nextInt(4);
        StringBuilder description = new StringBuilder("Fusion-evaporation: ");
        int currentZ = zCompound;
        int currentA = aCompound;
        for (int i = 0; i < evaporated; i++) {
            if (random.nextDouble() < 0.25 && currentZ > 2) {
                currentZ -= 2;
                currentA -= 4;
                description.append("alpha ");
            } else {
                currentA -= 1;
                description.append("neutron ");
            }
        }
        Isotope finalNucleus = Isotopes.find(currentZ, currentA);
        if (finalNucleus != null) {
            products.clear();
            products.add(finalNucleus);
        }
        description.append("-> ").append(products.isEmpty() ? "?" : products.get(0).notation());
        return new Reaction("fusion", description.toString(), products, sigma, 20.0);
    }

    /** Neutron capture (n,gamma): moves the nucleus to a heavier isotope of the same element. */
    public static Reaction neutronCapture(Element target, Random random) {
        Isotope isotope = Isotopes.weightedRandom(target, random);
        if (isotope == null) {
            return null;
        }
        Isotope product = Isotopes.find(target.atomicNumber(), isotope.massNumber() + 1);
        List<Isotope> products = new ArrayList<>();
        if (product != null) {
            products.add(product);
        }
        double sigma = 0.5 + 2.0 * random.nextDouble();
        if (target.atomicNumber() > 82) {
            // heavy actinides can fission after neutron capture
            if (random.nextDouble() < 0.3) {
                return spontaneousFission(target, random);
            }
        }
        return new Reaction("neutron_capture",
                "Neutron capture on " + isotope.notation() + " -> "
                        + (product == null ? "?" : product.notation()), products, sigma, 6.0);
    }

    /** Fission of a heavy nucleus into asymmetric fragments plus prompt neutrons. */
    public static Reaction spontaneousFission(Element target, Random random) {
        Isotope isotope = Isotopes.weightedRandom(target, random);
        List<Isotope> products = new ArrayList<>();
        if (isotope != null) {
            Isotope heavy = isotope.heavyFissionFragment(random);
            Isotope light = isotope.lightFissionFragment(random);
            if (heavy != null) {
                products.add(heavy);
            }
            if (light != null) {
                products.add(light);
            }
        }
        return new Reaction("fission", "Fission of " + (isotope == null ? target.symbol()
                : isotope.notation()) + " into asymmetric fragments", products, 580.0, 200.0);
    }

    /** Spallation: a high energy projectile chips nucleons off the target. */
    private static Reaction spallation(double energyMeV, int projectileZ, int targetA,
                                       Element target, Random random) {
        int removed = 1 + (int) Math.min(12, energyMeV / 120.0 + random.nextDouble() * 3.0);
        int newA = Math.max(1, targetA - removed);
        int newZ = target.atomicNumber() - (random.nextDouble() < 0.5 ? 0
                : Math.min(3, 1 + random.nextInt(2)));
        Isotope product = Isotopes.find(Math.max(1, newZ), newA);
        List<Isotope> products = new ArrayList<>();
        if (product != null) {
            products.add(product);
        }
        return new Reaction("spallation", "Spallation of " + target.symbol() + ": "
                + removed + " nucleons knocked out -> "
                + (product == null ? "?" : product.notation()), products,
                0.05 + 0.02 * removed, 40.0);
    }

    /** Photonuclear reaction: a bremsstrahlung photon knocks a neutron out of the nucleus. */
    public static Reaction photonuclear(double photonEnergyMeV, Element target, Random random) {
        double threshold = 6.0 + target.atomicNumber() * 0.1;
        if (photonEnergyMeV < threshold) {
            return null;
        }
        Isotope isotope = Isotopes.weightedRandom(target, random);
        if (isotope == null) {
            return null;
        }
        Isotope product = Isotopes.find(target.atomicNumber(), isotope.massNumber() - 1);
        List<Isotope> products = new ArrayList<>();
        if (product != null) {
            products.add(product);
        }
        return new Reaction("photonuclear", "Photonuclear (gamma,n) on " + isotope.notation()
                + " -> " + (product == null ? "?" : product.notation()), products, 0.02, 10.0);
    }

    /**
     * Heavy ion fusion towards the superheavy elements, including the survival probability that
     * makes element 118 so hard to reach (it falls off exponentially with the charge of the
     * compound system).
     */
    public static Reaction heavyIonFusion(int z1, int a1, int z2, int a2, double energyMeV,
                                          Random random, boolean magicBeam, boolean magicTarget) {
        double barrier = coulombBarrierMeV(z1, a1, z2, a2);
        double energyFactor = Math.min(1.0, Math.max(0.0, (energyMeV - barrier) / barrier + 0.5));
        double survival = Math.exp(-(z1 + z2) * 0.02) * (magicBeam ? 3.0 : 1.0)
                * (magicTarget ? 2.0 : 1.0);
        double probability = energyFactor * survival * 1.0e-4;
        if (random.nextDouble() > probability) {
            return null;
        }
        int zCompound = z1 + z2;
        int aCompound = a1 + a2;
        List<Isotope> products = new ArrayList<>();
        int evaporated = random.nextInt(4);
        int finalZ = zCompound;
        int finalA = aCompound - evaporated - (random.nextDouble() < 0.3 ? 2 : 0);
        if (random.nextDouble() < 0.3 && finalZ > 2) {
            finalZ -= 2;
            finalA -= 2;
        }
        Isotope product = Isotopes.find(Math.min(118, finalZ), Math.max(1, finalA));
        if (product != null) {
            products.add(product);
        }
        return new Reaction("heavy_ion_fusion",
                "Cold fusion of Z=" + z1 + " and Z=" + z2 + " -> compound Z=" + zCompound
                        + (product == null ? "" : " evaporating to " + product.notation()),
                products, 1.0e-6, 25.0);
    }
}
