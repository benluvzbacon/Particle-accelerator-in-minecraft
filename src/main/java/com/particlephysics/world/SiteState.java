package com.particlephysics.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.particlephysics.accelerator.AcceleratorDesign;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Every accelerator site in the world, with its blueprint and its operator settings.
 *
 * <p>A site is created when the player deploys the blueprint: it fixes the origin, the type, the
 * radius and the number of cells of the design, and it stores the setpoints of the machine
 * (magnet field, RF voltage and frequency, target energy, valve state). The blocks themselves are
 * never duplicated into the file - the machine is always read back from the world - so the physics
 * follows what is actually built.
 */
public class SiteState extends PersistentStore {
    /** One accelerator. */
    public static final class Site {
        public UUID id = UUID.randomUUID();
        public String name = "Accelerator";
        public AcceleratorDesign.Kind kind = AcceleratorDesign.Kind.RING;
        public int originX;
        public int originY;
        public int originZ;
        public String world = "minecraft:overworld";
        public int size = 24;
        public int cells = 8;
        public Direction rotation = Direction.EAST;

        // --- operator setpoints -----------------------------------------------------------------
        public double dipoleField;
        public double quadrupoleScale = 1.0;
        public double sextupoleScale = 1.0;
        public double steeringTrim;
        public double rfVoltageMV;
        public double rfFrequencyMHz = 100.0;
        public double rfPhaseDegrees;
        public double targetEnergyMeV = 100.0;
        public boolean running;
        public boolean valveOpen = true;
        public boolean superconducting;
        /** Ion species currently selected in the source, as a particle species name. */
        public String sourceSpecies = "PROTON";
        /** Injection: number of particles per bunch the injector tries to fill. */
        public double injectionIntensity = 1.0e10;
        public int bunches = 1;

        // --- bookkeeping ------------------------------------------------------------------------
        public long createdGameTime;
        public long lastBeamTime;
        /** Discovered by the player, used by the quest book. */
        public boolean firstBeam;

        public BlockPos origin() {
            return new BlockPos(originX, originY, originZ);
        }

        public BlockPos center() {
            return origin();
        }

        public AcceleratorDesign design() {
            return new AcceleratorDesign(kind, origin(), size, cells, rotation);
        }

        public double radius() {
            return size;
        }

        public boolean contains(BlockPos pos, int margin) {
            double dx = pos.getX() - originX;
            double dz = pos.getZ() - originZ;
            double r = Math.sqrt(dx * dx + dz * dz);
            return Math.abs(pos.getY() - originY) <= 8 && r <= size + margin;
        }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("id", id);
            nbt.putString("name", name);
            nbt.putString("kind", kind.name());
            nbt.putString("world", world);
            nbt.putInt("x", originX);
            nbt.putInt("y", originY);
            nbt.putInt("z", originZ);
            nbt.putInt("size", size);
            nbt.putInt("cells", cells);
            nbt.putString("rotation", rotation.getName());
            nbt.putDouble("field", dipoleField);
            nbt.putDouble("quad", quadrupoleScale);
            nbt.putDouble("sext", sextupoleScale);
            nbt.putDouble("steer", steeringTrim);
            nbt.putDouble("rfv", rfVoltageMV);
            nbt.putDouble("rff", rfFrequencyMHz);
            nbt.putDouble("rfp", rfPhaseDegrees);
            nbt.putDouble("energy", targetEnergyMeV);
            nbt.putBoolean("running", running);
            nbt.putBoolean("valve", valveOpen);
            nbt.putBoolean("super", superconducting);
            nbt.putString("species", sourceSpecies);
            nbt.putDouble("intensity", injectionIntensity);
            nbt.putInt("bunches", bunches);
            nbt.putLong("created", createdGameTime);
            nbt.putLong("lastBeam", lastBeamTime);
            nbt.putBoolean("firstBeam", firstBeam);
            return nbt;
        }

        public static Site fromNbt(NbtCompound nbt) {
            Site site = new Site();
            if (nbt.contains("id")) {
                site.id = nbt.getUuid("id");
            }
            site.name = nbt.getString("name");
            try {
                site.kind = AcceleratorDesign.Kind.valueOf(nbt.getString("kind"));
            } catch (IllegalArgumentException e) {
                site.kind = AcceleratorDesign.Kind.RING;
            }
            if (nbt.contains("world")) {
                site.world = nbt.getString("world");
            }
            site.originX = nbt.getInt("x");
            site.originY = nbt.getInt("y");
            site.originZ = nbt.getInt("z");
            site.size = Math.max(site.kind.minSize, nbt.getInt("size"));
            site.cells = Math.max(4, nbt.getInt("cells"));
            Direction rotation = Direction.byName(nbt.getString("rotation"));
            site.rotation = rotation == null ? Direction.EAST : rotation;
            site.dipoleField = nbt.getDouble("field");
            site.quadrupoleScale = nbt.contains("quad") ? nbt.getDouble("quad") : 1.0;
            site.sextupoleScale = nbt.contains("sext") ? nbt.getDouble("sext") : 1.0;
            site.steeringTrim = nbt.getDouble("steer");
            site.rfVoltageMV = nbt.getDouble("rfv");
            site.rfFrequencyMHz = nbt.contains("rff") ? nbt.getDouble("rff") : 100.0;
            site.rfPhaseDegrees = nbt.getDouble("rfp");
            site.targetEnergyMeV = nbt.getDouble("energy");
            site.running = nbt.getBoolean("running");
            site.valveOpen = !nbt.contains("valve") || nbt.getBoolean("valve");
            site.superconducting = nbt.getBoolean("super");
            if (nbt.contains("species")) {
                site.sourceSpecies = nbt.getString("species");
            }
            site.injectionIntensity = nbt.contains("intensity") ? nbt.getDouble("intensity")
                    : 1.0e10;
            site.bunches = Math.max(1, nbt.getInt("bunches"));
            site.createdGameTime = nbt.getLong("created");
            site.lastBeamTime = nbt.getLong("lastBeam");
            site.firstBeam = nbt.getBoolean("firstBeam");
            return site;
        }
    }

    private final List<Site> sites = new ArrayList<>();

    public SiteState() {
        super("sites.nbt");
    }

    public List<Site> sites() {
        return sites;
    }

    public Site byId(UUID id) {
        for (Site site : sites) {
            if (site.id.equals(id)) {
                return site;
            }
        }
        return null;
    }

    /** The machine whose volume contains the given position. */
    public Site at(BlockPos pos) {
        for (Site site : sites) {
            if (site.contains(pos, 4)) {
                return site;
            }
        }
        return null;
    }

    /** Creates a site unless one already covers the volume; returns the existing one then. */
    public Site create(AcceleratorDesign.Kind kind, BlockPos origin, int size, int cells,
                       Direction rotation, long gameTime, String world) {
        Site existing = at(origin);
        if (existing != null) {
            return existing;
        }
        Site site = new Site();
        site.kind = kind;
        site.world = world;
        site.originX = origin.getX();
        site.originY = origin.getY();
        site.originZ = origin.getZ();
        site.size = size;
        site.cells = cells;
        site.rotation = rotation;
        site.name = kind.displayName() + " " + (sites.size() + 1);
        site.createdGameTime = gameTime;
        sites.add(site);
        markDirty();
        return site;
    }

    /** The site in the given world whose volume contains the position, or null. */
    public Site at(BlockPos pos, String worldId) {
        for (Site site : sites) {
            if (site.world.equals(worldId) && site.contains(pos, 4)) {
                return site;
            }
        }
        return null;
    }

    public boolean remove(Site site) {
        boolean removed = sites.remove(site);
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public int count() {
        return sites.size();
    }

    @Override
    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (Site site : sites) {
            list.add(site.toNbt());
        }
        root.put("sites", list);
        return root;
    }

    @Override
    public void fromNbt(NbtCompound root) {
        sites.clear();
        NbtList list = root.getList("sites", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            sites.add(Site.fromNbt(list.getCompound(i)));
        }
    }
}
