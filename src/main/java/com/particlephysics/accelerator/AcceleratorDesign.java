package com.particlephysics.accelerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The blueprint of an accelerator: a deterministic, buildable layout of every component.
 *
 * <p>A design is generated from a few parameters (kind, radius, number of cells) and is used for
 * three things: the ghost block preview, the construction validator and the material checklist. The
 * player never has to guess where anything goes - and every placement decision still matters,
 * because the physics uses the blocks as they were actually placed.
 */
public final class AcceleratorDesign {
    /** Which machine the blueprint describes. */
    public enum Kind {
        RING("Synchrotron / Collider", 12, 48, 16),
        LINAC("Linear Pre-Accelerator", 6, 40, 8);

        private final String displayName;
        public final int minSize;
        public final int maxSize;
        public final int defaultSize;

        Kind(String displayName, int minSize, int maxSize, int defaultSize) {
            this.displayName = displayName;
            this.minSize = minSize;
            this.maxSize = maxSize;
            this.defaultSize = defaultSize;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** One required component of the design. */
    public static final class Slot {
        public final BlockPos offset;
        public final MachineKind kind;
        public final Direction facing;
        public final MachineKind.System system;
        public final int section;
        public final String sectionName;
        public final boolean shielding;
        public final boolean infrastructure;
        /** Material key for the checklist ("dipole_magnet" or "lead_block"). */
        public final String materialKey;

        public Slot(BlockPos offset, MachineKind kind, Direction facing, int section,
                    String sectionName, boolean shielding, boolean infrastructure, String materialKey) {
            this.offset = offset;
            this.kind = kind;
            this.facing = facing;
            this.system = kind == null ? MachineKind.System.SHIELDING : kind.system();
            this.section = section;
            this.sectionName = sectionName;
            this.shielding = shielding;
            this.infrastructure = infrastructure;
            this.materialKey = materialKey;
        }

        public boolean isShielding() {
            return shielding;
        }

        public boolean isInfrastructure() {
            return infrastructure;
        }

        public String describe() {
            return kind == null ? materialKey : kind.displayName();
        }
    }

    private final Kind kind;
    private final int size;
    private final int cells;
    private final BlockPos origin;
    private final Direction rotation;
    private final List<Slot> slots = new ArrayList<>();
    private final List<BlockPos> beamPath = new ArrayList<>();
    private final Map<String, Integer> materials = new LinkedHashMap<>();
    private int sectionCount;
    private double radius;
    private double circumference;

    public AcceleratorDesign(Kind kind, BlockPos origin, int size, int cells, Direction rotation) {
        this.kind = kind;
        this.origin = origin;
        this.size = Math.max(kind.minSize, Math.min(kind.maxSize, size));
        this.cells = Math.max(4, cells - (cells % 4));
        this.rotation = rotation == null ? Direction.NORTH : rotation;
        if (kind == Kind.RING) {
            generateRing();
        } else {
            generateLinac();
        }
        buildMaterials();
    }

    // ------------------------------------------------------------------------------------------
    // Geometry generation
    // ------------------------------------------------------------------------------------------

    private void generateRing() {
        radius = size;
        List<BlockPos> ring = circlePoints(size, origin.getY());
        circumference = ring.size();
        sectionCount = cells;

        // Infrastructure and shielding are placed relative to the ring.
        List<BlockPos> shielding = circlePoints(size + 2, origin.getY());
        List<BlockPos> infrastructure = circlePoints(size + 3, origin.getY());

        int n = ring.size();
        for (int i = 0; i < n; i++) {
            BlockPos pos = ring.get(i);
            int cell = (int) Math.floor((double) i / n * cells) % cells;
            String sectionName = "Beamline Section " + (cell + 1);
            double angle = Math.atan2(pos.getZ() - origin.getZ(), pos.getX() - origin.getX());
            Direction tangent = tangent(angle, true);
            MachineKind component = componentForRing(i, n, cell);
            if (component != null) {
                Direction facing = tangent;
                slots.add(new Slot(pos.subtract(origin), component, facing, cell, sectionName,
                        false, false, component.id()));
                beamPath.add(pos.subtract(origin));
            } else {
                slots.add(nozzleDrift(pos, origin, cell, sectionName, tangent));
            }
        }
        // Shielding ring, one block outside the beam pipe.
        for (BlockPos pos : shielding) {
            slots.add(new Slot(pos.subtract(origin), null, Direction.NORTH, -1,
                    "Radiation Shielding", true, false, "concrete_shielding"));
        }
        // Shielding roof over the tunnel and a floor under it.
        for (BlockPos pos : shielding) {
            slots.add(new Slot(pos.subtract(origin).add(0, 1, 0), null, Direction.NORTH, -1,
                    "Radiation Shielding (Roof)", true, false, "lead_shielding"));
        }
        // Infrastructure ring: power, cooling, cryogenics, pumps.
        for (int i = 0; i < infrastructure.size(); i++) {
            BlockPos pos = infrastructure.get(i);
            int index = i % 8;
            String material;
            if (index == 0) {
                material = "power_supply";
            } else if (index == 1) {
                material = "cooling_unit";
            } else if (index == 2) {
                material = "cryogenic_unit";
            } else if (index == 3) {
                material = "power_cable";
            } else if (index == 4) {
                material = "vacuum_pump";
            } else {
                material = "power_cable";
            }
            MachineKind infraKind = MachineKind.byId(material);
            slots.add(new Slot(pos.subtract(origin), infraKind, Direction.NORTH, -1,
                    "Infrastructure", false, true, material));
        }
        // Control computer next to the injection straight.
        slots.add(new Slot(new BlockPos(size + 5, 0, 0), MachineKind.CONTROL_COMPUTER,
                Direction.WEST, -1, "Control Room", false, true, "control_computer"));
        slots.add(new Slot(new BlockPos(-size - 5, 0, 0), MachineKind.CONTROL_COMPUTER,
                Direction.EAST, -1, "Control Room", false, true, "control_computer"));
    }

    private Slot nozzleDrift(BlockPos pos, BlockPos origin, int cell, String sectionName,
                             Direction tangent) {
        return new Slot(pos.subtract(origin), MachineKind.BEAM_PIPE, tangent, cell, sectionName,
                false, false, "beam_pipe");
    }

    /**
     * Decides which component belongs at ring position {@code i}. The pattern follows the real
     * layout of a synchrotron: arcs filled with dipoles interrupted by focusing quadrupoles and
     * chromaticity correcting sextupoles, and four straight sections for injection, RF, collisions
     * and diagnostics.
     */
    private MachineKind componentForRing(int i, int n, int cell) {
        // four straight sections, each about 1/12 of the ring
        int straightLength = Math.max(4, n / 12);
        int[] straightStarts = {
                (int) (n * 0.00),
                (int) (n * 0.25),
                (int) (n * 0.50),
                (int) (n * 0.75)
        };
        for (int s = 0; s < 4; s++) {
            int start = straightStarts[s];
            for (int o = 0; o < straightLength; o++) {
                int index = (start + o) % n;
                if (index != i) {
                    continue;
                }
                return switch (s) {
                    case 0 -> switch (o % 6) {
                        case 0 -> MachineKind.PARTICLE_SOURCE;
                        case 1 -> MachineKind.INJECTOR;
                        case 2 -> MachineKind.STEERING_MAGNET;
                        case 3 -> MachineKind.BEAM_MONITOR;
                        case 4 -> MachineKind.VACUUM_VALVE;
                        default -> MachineKind.BEAM_PIPE;
                    };
                    case 1 -> switch (o % 4) {
                        case 0 -> MachineKind.RF_CAVITY;
                        case 1 -> MachineKind.RF_CAVITY;
                        case 2 -> MachineKind.BEAM_MONITOR;
                        default -> MachineKind.BEAM_PIPE;
                    };
                    case 2 -> switch (o % 8) {
                        case 0 -> MachineKind.COLLISION_CHAMBER;
                        case 1 -> MachineKind.TRACKING_DETECTOR;
                        case 2 -> MachineKind.CALORIMETER;
                        case 3 -> MachineKind.MUON_DETECTOR;
                        case 4 -> MachineKind.SCINTILLATION_DETECTOR;
                        case 5 -> MachineKind.CHERENKOV_DETECTOR;
                        case 6 -> MachineKind.RADIATION_DETECTOR;
                        default -> MachineKind.BEAM_PIPE;
                    };
                    default -> switch (o % 6) {
                        case 0 -> MachineKind.VACUUM_PUMP;
                        case 1 -> MachineKind.VACUUM_CHAMBER;
                        case 2 -> MachineKind.VACUUM_GAUGE;
                        case 3 -> MachineKind.VACUUM_VALVE;
                        case 4 -> MachineKind.BEAM_MONITOR;
                        default -> MachineKind.BEAM_PIPE;
                    };
                };
            }
        }
        // arcs: the cell pattern
        int inCell = i % Math.max(1, n / cells);
        return switch (inCell % 6) {
            case 0, 1, 2 -> MachineKind.DIPOLE_MAGNET;
            case 3 -> MachineKind.BEAM_PIPE;
            case 4 -> MachineKind.QUADRUPOLE_MAGNET;
            default -> MachineKind.SEXTUPOLE_MAGNET;
        };
    }

    private void generateLinac() {
        radius = 0;
        sectionCount = cells;
        int length = size * 2;
        for (int i = 0; i < length; i++) {
            BlockPos offset = new BlockPos(i - length / 2, 0, 0);
            MachineKind component;
            String material;
            if (i == 0) {
                component = MachineKind.PARTICLE_SOURCE;
                material = "particle_source";
            } else if (i == 1) {
                component = MachineKind.INJECTOR;
                material = "injector";
            } else if (i % 7 == 0) {
                component = MachineKind.RF_CAVITY;
                material = "rf_cavity";
            } else if (i % 11 == 0) {
                component = MachineKind.QUADRUPOLE_MAGNET;
                material = "quadrupole_magnet";
            } else if (i % 13 == 0) {
                component = MachineKind.DIPOLE_MAGNET;
                material = "dipole_magnet";
            } else if (i == length - 2) {
                component = MachineKind.BEAM_MONITOR;
                material = "beam_monitor";
            } else if (i == length - 1) {
                component = MachineKind.BEAM_DUMP;
                material = "beam_dump";
            } else if (i % 9 == 0) {
                component = MachineKind.VACUUM_PUMP;
                material = "vacuum_pump";
            } else if (i % 15 == 0) {
                component = MachineKind.VACUUM_GAUGE;
                material = "vacuum_gauge";
            } else {
                component = MachineKind.BEAM_PIPE;
                material = "beam_pipe";
            }
            slots.add(new Slot(offset, component, Direction.EAST, i / 4, "Linac Section " + (i / 4 + 1),
                    false, false, material));
            beamPath.add(offset);
            // shielding above and beside the linac
            slots.add(new Slot(offset.add(0, 2, 0), null, Direction.NORTH, -1, "Radiation Shielding",
                    true, false, "lead_shielding"));
            slots.add(new Slot(offset.add(0, 1, 1), null, Direction.NORTH, -1, "Radiation Shielding",
                    true, false, "concrete_shielding"));
            slots.add(new Slot(offset.add(0, 0, 1), null, Direction.NORTH, -1, "Cable Trench",
                    false, true, "power_cable"));
        }
        slots.add(new Slot(new BlockPos(-length / 2 - 2, 0, 2), MachineKind.CONTROL_COMPUTER,
                Direction.EAST, -1, "Control Room", false, true, "control_computer"));
        circumference = length;
    }

    private void buildMaterials() {
        materials.clear();
        for (Slot slot : slots) {
            materials.merge(slot.materialKey, 1, Integer::sum);
        }
    }

    /** Ordered block positions of the beam path (used for ghost previews and rendering). */
    public List<BlockPos> beamPath() {
        return beamPath;
    }

    public List<Slot> slots() {
        return slots;
    }

    public Map<String, Integer> materials() {
        return materials;
    }

    public Kind kind() {
        return kind;
    }

    public int size() {
        return size;
    }

    public int cells() {
        return cells;
    }

    public int sectionCount() {
        return sectionCount;
    }

    public BlockPos origin() {
        return origin;
    }

    public Direction rotation() {
        return rotation;
    }

    public double radius() {
        return radius;
    }

    public double circumference() {
        return circumference;
    }

    /** Number of blocks needed for all components (excluding shielding and infrastructure). */
    public int componentCount() {
        int count = 0;
        for (Slot slot : slots) {
            if (!slot.isShielding() && !slot.isInfrastructure()) {
                count++;
            }
        }
        return count;
    }

    public int slotCount() {
        return slots.size();
    }

    /** Slots of a single subsystem, used by the systems view. */
    public List<Slot> slotsOf(MachineKind.System system) {
        List<Slot> list = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.system == system) {
                list.add(slot);
            }
        }
        return list;
    }

    /** Distinct Y levels of the design, used by the layer view. */
    public List<Integer> layers() {
        List<Integer> layers = new ArrayList<>();
        for (Slot slot : slots) {
            int y = slot.offset.getY();
            if (!layers.contains(y)) {
                layers.add(y);
            }
        }
        layers.sort(Integer::compareTo);
        return layers;
    }

    /** Block positions of one construction layer. */
    public List<Slot> layer(int y) {
        List<Slot> list = new ArrayList<>();
        for (Slot slot : slots) {
            if (slot.offset.getY() == y) {
                list.add(slot);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------------------------------
    // Design -> world transforms
    // ------------------------------------------------------------------------------------------

    /** Number of 90 degree turns needed to map the design's +X axis onto the rotation. */
    private int rotationTurns() {
        return switch (rotation) {
            case EAST -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            default -> 3; // NORTH
        };
    }

    /** World position of a design slot (the design's local +X axis points at {@link #rotation()}). */
    public BlockPos worldPos(Slot slot) {
        int x = slot.offset.getX();
        int z = slot.offset.getZ();
        for (int i = 0; i < rotationTurns(); i++) {
            int nx = -z;
            int nz = x;
            x = nx;
            z = nz;
        }
        return origin.add(x, slot.offset.getY(), z);
    }

    /** World direction of a slot's facing. */
    public Direction worldFacing(Slot slot) {
        return rotateFacing(slot.facing, rotation);
    }

    public static Direction rotateFacing(Direction dir, Direction rotation) {
        if (dir == null || dir.getAxis().isVertical()) {
            return dir == null ? Direction.NORTH : dir;
        }
        int turns = switch (rotation) {
            case EAST -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            default -> 3;
        };
        Direction d = dir;
        for (int i = 0; i < turns; i++) {
            d = switch (d) {
                case EAST -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case WEST -> Direction.NORTH;
                default -> Direction.EAST;
            };
        }
        return d;
    }

    private static List<BlockPos> circlePoints(double radius, int y) {
        List<BlockPos> points = new ArrayList<>();
        int steps = (int) Math.max(32, Math.round(2.0 * Math.PI * radius * 1.6));
        int previousX = Integer.MIN_VALUE;
        int previousZ = Integer.MIN_VALUE;
        // walk the circle in order, snapping to blocks and removing duplicates
        for (int i = 0; i < steps; i++) {
            double angle = 2.0 * Math.PI * i / steps;
            int x = (int) Math.round(radius * Math.cos(angle));
            int z = (int) Math.round(radius * Math.sin(angle));
            if (x == previousX && z == previousZ) {
                continue;
            }
            points.add(new BlockPos(x, y, z));
            previousX = x;
            previousZ = z;
        }
        return points;
    }

    private static Direction tangent(double angle, boolean ccw) {
        double dx = -Math.sin(angle);
        double dz = Math.cos(angle);
        if (!ccw) {
            dx = -dx;
            dz = -dz;
        }
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
