package com.particlephysics.world;

import java.util.ArrayList;
import java.util.List;

import com.particlephysics.elements.Isotope;
import com.particlephysics.elements.Isotopes;
import com.particlephysics.physics.Units;
import com.particlephysics.radiation.RadiationType;
import com.particlephysics.radiation.Shielding;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The radiation field of the world.
 *
 * <p>Every radioactive source in the world (isotope samples, activated target stations, beam loss
 * hot spots, collision points) is stored with its activity, and the dose rate anywhere in the world
 * is computed from the inverse square law with the shielding attenuation along the line of sight.
 * Sources decay with their real half lives (compressed by the config), so an activated machine
 * cools down over time just like the real thing.
 */
public class RadiationState extends PersistentStore {
    /** One radioactive source. */
    public static final class Source {
        public BlockPos pos;
        public int z;
        public int a;
        /** Initial activity in becquerel. */
        public double activity;
        /** Seconds of beam time elapsed since the source was created. */
        public double elapsed;
        /** Extra multiplier for prompt radiation (beam losses). */
        public double promptFactor;
        /** True for sources created by the accelerator rather than by placed material. */
        public boolean accelerator;
        /** Radiation type emitted. */
        public RadiationType type;
        /** Energy per decay in MeV. */
        public double energyMeV;

        public double currentActivity(double compression) {
            double halfLife = gameHalfLife(compression);
            if (!Double.isFinite(halfLife) || halfLife <= 0) {
                return 0;
            }
            return activity * Math.pow(0.5, elapsed / halfLife);
        }

        public double gameHalfLife(double compression) {
            Isotope isotope = Isotopes.find(z, a);
            if (isotope == null) {
                return 600.0 / Math.max(1.0, compression);
            }
            if (isotope.isStable()) {
                return Double.POSITIVE_INFINITY;
            }
            double halfLife = isotope.halfLifeSeconds() / Math.max(1.0e-6, compression);
            return Math.max(1.0, Math.min(halfLife, 2.0e6));
        }
    }

    private final List<Source> sources = new ArrayList<>();
    private double compression = 3600.0;
    private double scale = 2000.0;

    public RadiationState() {
        super("radiation.nbt");
    }

    public void setCompression(double compression) {
        this.compression = Math.max(1.0, compression);
    }

    public void setScale(double scale) {
        this.scale = Math.max(0.0, scale);
    }

    public List<Source> sources() {
        return sources;
    }

    /** Adds (or refreshes) an isotope source with the given mass in grams. */
    public Source addIsotopeSource(BlockPos pos, Isotope isotope, double grams) {
        if (isotope == null || isotope.isStable()) {
            return null;
        }
        Source source = new Source();
        source.pos = pos.toImmutable();
        source.z = isotope.element().atomicNumber();
        source.a = isotope.massNumber();
        source.activity = isotope.activityPerGram() * Math.max(1.0e-6, grams);
        source.energyMeV = Math.max(0.05, isotope.decayQValueMeV());
        source.type = RadiationType.fromDecayMode(isotope.decayMode());
        source.elapsed = 0;
        source.promptFactor = 1.0;
        merge(source);
        markDirty();
        return source;
    }

    /**
     * Records prompt radiation from a beam loss or a collision.
     *
     * @param joules energy deposited at the location
     * @param weight quality factor of the radiation produced
     */
    public Source addPromptSource(BlockPos pos, double joules, RadiationType type,
                                  double weight) {
        if (joules <= 0) {
            return null;
        }
        Source source = new Source();
        source.pos = pos.toImmutable();
        // Prompt radiation is modelled as an artificially decaying source with a few minutes of
        // equivalent decay time so the tunnel stays hot while the machine runs.
        source.z = 27;
        source.a = 60;
        source.activity = joules * 4.0e11 * weight;
        source.energyMeV = type == RadiationType.NEUTRON ? 2.0 : 1.2;
        source.type = type;
        source.elapsed = 0;
        source.promptFactor = 1.0 / Math.max(1.0, scale);
        source.accelerator = true;
        merge(source);
        markDirty();
        return source;
    }

    private void merge(Source source) {
        for (Source existing : sources) {
            if (existing.pos.equals(source.pos)
                    && existing.z == source.z && existing.a == source.a) {
                existing.activity += source.activity;
                existing.elapsed = 0;
                existing.promptFactor = Math.max(existing.promptFactor, source.promptFactor);
                return;
            }
        }
        sources.add(source);
        if (sources.size() > 2048) {
            // keep the strongest sources when the list grows unreasonable
            sources.sort((a, b) -> Double.compare(b.activity * b.promptFactor,
                    a.activity * a.promptFactor));
            while (sources.size() > 1024) {
                sources.remove(sources.size() - 1);
            }
        }
    }

    public void removeSourcesNear(BlockPos pos, double radius) {
        double radiusSquared = radius * radius;
        sources.removeIf(source -> source.pos.getSquaredDistance(pos) < radiusSquared);
        markDirty();
    }

    /** Advances the decay of every source. */
    public void tick(double seconds) {
        boolean changed = false;
        for (Source source : sources) {
            source.elapsed += seconds;
            if (source.currentActivity(compression) < 1.0e-3 * source.promptFactor) {
                changed = true;
            }
        }
        if (changed) {
            sources.removeIf(source -> source.currentActivity(compression) < 1.0e-3);
            markDirty();
        }
    }

    /**
     * Dose rate at a world position in sievert per second, before the game balance scaling.
     */
    public double absorbedDoseRate(World world, Vec3d position) {
        double total = 0.0;
        for (Source source : sources) {
            Vec3d sourcePos = new Vec3d(source.pos.getX() + 0.5, source.pos.getY() + 0.5,
                    source.pos.getZ() + 0.5);
            double distance = position.distanceTo(sourcePos);
            if (distance > 64.0) {
                continue;
            }
            double activity = source.currentActivity(compression);
            double power = activity * source.energyMeV * 1.0e6 * Units.EV_TO_JOULE
                    * source.promptFactor;
            if (power <= 0) {
                continue;
            }
            double attenuation = Shielding.transmission(world, sourcePos, position, source.type);
            // flux (W/m^2) -> dose for a 70 kg body presenting 0.5 m^2
            double dose = power / (4.0 * Math.PI * Math.max(0.25, distance * distance))
                    * (0.5 / 70.0) * source.type.weightFactor() * attenuation;
            total += dose;
        }
        return total;
    }

    /** Dose rate including the game balance scaling, in sievert per second. */
    public double doseRate(World world, Vec3d position) {
        double total = 0.0;
        for (Source source : sources) {
            Vec3d sourcePos = new Vec3d(source.pos.getX() + 0.5, source.pos.getY() + 0.5,
                    source.pos.getZ() + 0.5);
            double distance = position.distanceTo(sourcePos);
            if (distance > 64.0) {
                continue;
            }
            double activity = source.currentActivity(compression);
            double power = activity * source.energyMeV * 1.0e6 * Units.EV_TO_JOULE
                    * source.promptFactor;
            if (power <= 0) {
                continue;
            }
            double attenuation = Shielding.transmission(world, sourcePos, position, source.type);
            double dose = power / (4.0 * Math.PI * Math.max(0.25, distance * distance))
                    * (0.5 / 70.0) * source.type.weightFactor() * attenuation * scale;
            total += dose;
        }
        return total;
    }

    /** Dose rate in microsievert per hour for the HUD and the radiation meters. */
    public double doseRateMicroSvPerHour(World world, Vec3d position) {
        return doseRate(world, position) * 3.6e9;
    }

    /** Heat produced by the decay of the sources near a position, in watts. */
    public double decayHeatWatts(Vec3d position, double radius) {
        double total = 0;
        for (Source source : sources) {
            Vec3d sourcePos = new Vec3d(source.pos.getX() + 0.5, source.pos.getY() + 0.5,
                    source.pos.getZ() + 0.5);
            if (position.distanceTo(sourcePos) > radius) {
                continue;
            }
            total += source.currentActivity(compression) * source.energyMeV * 1.0e6
                    * Units.EV_TO_JOULE;
        }
        return total;
    }

    /** Doubles are stored as their raw IEEE bits because NBT has no double array type. */
    private static long[] bits(double[] values) {
        long[] out = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = Double.doubleToRawLongBits(values[i]);
        }
        return out;
    }

    private static double[] values(long[] bits) {
        double[] out = new double[bits.length];
        for (int i = 0; i < bits.length; i++) {
            out[i] = Double.longBitsToDouble(bits[i]);
        }
        return out;
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        root.putDouble("compression", compression);
        root.putDouble("scale", scale);
        int n = sources.size();
        int[] positions = new int[n * 3];
        int[] za = new int[n * 2];
        double[] activity = new double[n];
        double[] elapsed = new double[n];
        double[] prompt = new double[n];
        double[] energy = new double[n];
        int[] types = new int[n];
        for (int i = 0; i < n; i++) {
            Source source = sources.get(i);
            positions[i * 3] = source.pos.getX();
            positions[i * 3 + 1] = source.pos.getY();
            positions[i * 3 + 2] = source.pos.getZ();
            za[i * 2] = source.z;
            za[i * 2 + 1] = source.a;
            activity[i] = source.activity;
            elapsed[i] = source.elapsed;
            prompt[i] = source.promptFactor;
            energy[i] = source.energyMeV;
            types[i] = source.type.ordinal();
        }
        root.putIntArray("positions", positions);
        root.putIntArray("za", za);
        root.putIntArray("types", types);
        root.putLongArray("activity", bits(activity));
        root.putLongArray("elapsed", bits(elapsed));
        root.putLongArray("prompt", bits(prompt));
        root.putLongArray("energy", bits(energy));
        return root;
    }

    @Override
    public void fromNbt(NbtCompound root) {
        compression = root.contains("compression") ? root.getDouble("compression") : 3600.0;
        scale = root.contains("scale") ? root.getDouble("scale") : 2000.0;
        sources.clear();
        int[] positions = root.getIntArray("positions");
        int[] za = root.getIntArray("za");
        double[] activity = values(root.getLongArray("activity"));
        double[] elapsed = values(root.getLongArray("elapsed"));
        double[] prompt = values(root.getLongArray("prompt"));
        double[] energy = values(root.getLongArray("energy"));
        int[] types = root.getIntArray("types");
        int n = positions.length / 3;
        for (int i = 0; i < n; i++) {
            Source source = new Source();
            source.pos = new BlockPos(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2]);
            source.z = za.length > i * 2 ? za[i * 2] : 27;
            source.a = za.length > i * 2 + 1 ? za[i * 2 + 1] : 60;
            source.activity = i < activity.length ? activity[i] : 0;
            source.elapsed = i < elapsed.length ? elapsed[i] : 0;
            source.promptFactor = i < prompt.length ? prompt[i] : 1.0;
            source.energyMeV = i < energy.length ? energy[i] : 1.2;
            source.type = i < types.length && types[i] < RadiationType.values().length
                    ? RadiationType.values()[types[i]] : RadiationType.GAMMA;
            sources.add(source);
        }
    }
}
