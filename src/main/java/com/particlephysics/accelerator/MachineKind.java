package com.particlephysics.accelerator;

import com.particlephysics.physics.LatticeElement;

/**
 * Catalogue of every accelerator component. One entry drives the block, its block entity, the
 * blueprint layout, the construction validator, the physics lattice and the quest book, so the
 * definition of a "dipole magnet" exists in exactly one place.
 */
public enum MachineKind {
    // --- beam line ---------------------------------------------------------------------------
    BEAM_PIPE("beam_pipe", "Beam Pipe", System.BEAMLINE, Orientation.AXIS, 0.0),
    VACUUM_CHAMBER("vacuum_chamber", "Vacuum Chamber", System.VACUUM, Orientation.AXIS, 0.0),
    VACUUM_PUMP("vacuum_pump", "Vacuum Pump", System.VACUUM, Orientation.NONE, 5.0),
    VACUUM_GAUGE("vacuum_gauge", "Vacuum Gauge", System.VACUUM, Orientation.NONE, 0.0),
    VACUUM_VALVE("vacuum_valve", "Vacuum Valve", System.VACUUM, Orientation.AXIS, 0.0),
    BEAM_MONITOR("beam_monitor", "Beam Monitor", System.BEAMLINE, Orientation.AXIS, 0.0),
    BEAM_DUMP("beam_dump", "Beam Dump", System.BEAMLINE, Orientation.AXIS, 0.0),

    // --- magnets -----------------------------------------------------------------------------
    DIPOLE_MAGNET("dipole_magnet", "Dipole Magnet", System.MAGNET, Orientation.AXIS, 1.8),
    QUADRUPOLE_MAGNET("quadrupole_magnet", "Quadrupole Magnet", System.MAGNET, Orientation.AXIS, 20.0),
    SEXTUPOLE_MAGNET("sextupole_magnet", "Sextupole Magnet", System.MAGNET, Orientation.AXIS, 200.0),
    STEERING_MAGNET("steering_magnet", "Steering Magnet", System.MAGNET, Orientation.AXIS, 0.05),
    SUPERCONDUCTING_DIPOLE("superconducting_dipole", "Superconducting Dipole", System.MAGNET,
            Orientation.AXIS, 8.3),

    // --- radio frequency ---------------------------------------------------------------------
    RF_CAVITY("rf_cavity", "RF Cavity", System.RF, Orientation.AXIS, 2.0),

    // --- sources -----------------------------------------------------------------------------
    PARTICLE_SOURCE("particle_source", "Particle Source", System.BEAMLINE, Orientation.NONE, 0.0),
    INJECTOR("injector", "Particle Injector", System.BEAMLINE, Orientation.AXIS, 0.0),

    // --- detectors ---------------------------------------------------------------------------
    TRACKING_DETECTOR("tracking_detector", "Tracking Detector", System.DETECTOR, Orientation.AXIS, 0.0),
    CALORIMETER("calorimeter", "Calorimeter", System.DETECTOR, Orientation.AXIS, 0.0),
    SCINTILLATION_DETECTOR("scintillation_detector", "Scintillation Detector", System.DETECTOR,
            Orientation.AXIS, 0.0),
    CHERENKOV_DETECTOR("cherenkov_detector", "Cherenkov Detector", System.DETECTOR, Orientation.AXIS,
            0.0),
    MUON_DETECTOR("muon_detector", "Muon Detector", System.DETECTOR, Orientation.AXIS, 0.0),
    RADIATION_DETECTOR("radiation_detector", "Radiation Detector", System.DETECTOR, Orientation.NONE,
            0.0),
    COLLISION_CHAMBER("collision_chamber", "Collision Chamber", System.DETECTOR, Orientation.AXIS,
            0.0),

    // --- infrastructure ----------------------------------------------------------------------
    CONTROL_COMPUTER("control_computer", "Control Computer", System.CONTROL, Orientation.FACING,
            0.0),
    POWER_SUPPLY("power_supply", "Power Supply", System.POWER, Orientation.FACING, 250.0),
    POWER_CABLE("power_cable", "Power Cable", System.POWER, Orientation.AXIS, 150.0),
    COOLING_UNIT("cooling_unit", "Cooling Unit", System.COOLING, Orientation.FACING, 80.0),
    CRYOGENIC_UNIT("cryogenic_unit", "Cryogenic Unit", System.CRYO, Orientation.FACING, 300.0),
    TARGET_STATION("target_station", "Target Station", System.BEAMLINE, Orientation.AXIS, 0.0),
    DECAY_CHAMBER("decay_chamber", "Decay Chamber", System.BEAMLINE, Orientation.NONE, 0.0),
    WARNING_LIGHT("warning_light", "Radiation Warning Light", System.CONTROL, Orientation.NONE, 0.05);

    /** Subsystem a component belongs to; used by the systems view of the blueprint. */
    public enum System {
        BEAMLINE("Beam line", 0x8FD3FF),
        VACUUM("Vacuum", 0x9BE8AE),
        MAGNET("Magnets", 0xFF8A65),
        RF("RF", 0xFFD166),
        DETECTOR("Detectors", 0xC792EA),
        CONTROL("Control", 0xB0BEC5),
        POWER("Power", 0xFFF176),
        COOLING("Cooling", 0x81D4FA),
        CRYO("Cryogenics", 0xB39DDB),
        SHIELDING("Shielding", 0x9E9E9E);

        private final String displayName;
        private final int colour;

        System(String displayName, int colour) {
            this.displayName = displayName;
            this.colour = colour;
        }

        public String displayName() {
            return displayName;
        }

        public int colour() {
            return colour;
        }
    }

    /** Which block state properties the block needs. */
    public enum Orientation {
        NONE,
        FACING,
        AXIS
    }

    private final String id;
    private final String displayName;
    private final System system;
    private final Orientation orientation;
    private final double capacity; // T for dipoles, T/m for quadrupoles, MV for cavities, m^3/s pumps

    MachineKind(String id, String displayName, System system, Orientation orientation,
                double capacity) {
        this.id = id;
        this.displayName = displayName;
        this.system = system;
        this.orientation = orientation;
        this.capacity = capacity;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public System system() {
        return system;
    }

    public Orientation orientation() {
        return orientation;
    }

    /**
     * Nominal capability of the component: dipole field in T, quadrupole gradient in T/m,
     * sextupole strength in T/m^2, RF voltage in MV, pump speed in m^3/s, cooling power in kW.
     */
    public double capacity() {
        return capacity;
    }

    public boolean isBeamlineComponent() {
        return system == System.BEAMLINE || system == System.MAGNET || system == System.RF
                || system == System.VACUUM || system == System.DETECTOR;
    }

    public boolean hasScreen() {
        return this == CONTROL_COMPUTER || this == TARGET_STATION || this == DECAY_CHAMBER
                || this == PARTICLE_SOURCE || this == INJECTOR
                || system == System.DETECTOR || system == System.VACUUM
                || this == POWER_SUPPLY || this == COOLING_UNIT || this == CRYOGENIC_UNIT;
    }

    public boolean isMagnet() {
        return system == System.MAGNET;
    }

    /** Maps the component onto the optical element the tracking code uses. */
    public LatticeElement.Kind toLatticeKind() {
        return switch (this) {
            case DIPOLE_MAGNET, SUPERCONDUCTING_DIPOLE -> LatticeElement.Kind.DIPOLE;
            case QUADRUPOLE_MAGNET -> LatticeElement.Kind.QUADRUPOLE;
            case SEXTUPOLE_MAGNET -> LatticeElement.Kind.SEXTUPOLE;
            case STEERING_MAGNET -> LatticeElement.Kind.STEERING;
            case RF_CAVITY -> LatticeElement.Kind.RF_CAVITY;
            case BEAM_MONITOR -> LatticeElement.Kind.BEAM_MONITOR;
            case VACUUM_PUMP -> LatticeElement.Kind.VACUUM_PUMP;
            case VACUUM_VALVE -> LatticeElement.Kind.VALVE;
            case COLLISION_CHAMBER -> LatticeElement.Kind.COLLISION_POINT;
            case INJECTOR, PARTICLE_SOURCE -> LatticeElement.Kind.INJECTION;
            case TARGET_STATION, DECAY_CHAMBER, BEAM_DUMP -> LatticeElement.Kind.COLLIMATOR;
            default -> LatticeElement.Kind.DRIFT;
        };
    }

    private static final java.util.Map<String, MachineKind> BY_ID = new java.util.HashMap<>();

    static {
        for (MachineKind kind : values()) {
            BY_ID.put(kind.id, kind);
        }
    }

    public static MachineKind byId(String id) {
        return BY_ID.get(id);
    }

    public static MachineKind parse(String name) {
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
