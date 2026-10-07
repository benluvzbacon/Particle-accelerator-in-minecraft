package com.particlephysics.physics;

/**
 * A single optical element of the accelerator lattice as seen by the simulation.
 *
 * <p>These are generated from the blocks the player actually placed: the position along the design
 * orbit, the physical length and the field strengths all come from
 * {@code com.particlephysics.accelerator.AcceleratorLattice}.
 */
public final class LatticeElement {
    /** Kind of optical element. */
    public enum Kind {
        DRIFT,
        DIPOLE,
        QUADRUPOLE,
        SEXTUPOLE,
        STEERING,
        RF_CAVITY,
        BEAM_MONITOR,
        COLLIMATOR,
        COLLISION_POINT,
        INJECTION,
        VACUUM_PUMP,
        VALVE
    }

    private final Kind kind;
    private final String label;
    private double s;              // position along the design orbit [m]
    private double length;         // physical length [m]
    private double field;          // dipole field [T], quadrupole gradient [T/m], sextupole [T/m^2]
    private double steeringAngle;  // radians, steering magnets only
    private double rfVoltage;      // MV per cavity (peak)
    private double rfFrequency;    // MHz
    private double rfPhase;        // radians
    private double aperture;       // beam pipe radius [m]
    private boolean powered;
    /** Quarter turns of the magnet around the beam axis (0, 1, 2, 3). */
    private int rotation;
    /** Bending direction of a dipole: +1 or -1, set by the block orientation. */
    private double bendSign = 1.0;
    /** Maximum field the magnet can produce [T] (capacity of the installed magnet type). */
    private double capacity = 1.8;
    private int blockX;
    private int blockY;
    private int blockZ;

    public LatticeElement(Kind kind, String label) {
        this.kind = kind;
        this.label = label;
    }

    public Kind kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public double s() {
        return s;
    }

    public LatticeElement setS(double s) {
        this.s = s;
        return this;
    }

    public double length() {
        return length;
    }

    public LatticeElement setLength(double length) {
        this.length = length;
        return this;
    }

    /** Dipole field in T, quadrupole gradient in T/m or sextupole strength in T/m^2. */
    public double field() {
        return field;
    }

    public LatticeElement setField(double field) {
        this.field = field;
        return this;
    }

    public double steeringAngle() {
        return steeringAngle;
    }

    public LatticeElement setSteeringAngle(double radians) {
        this.steeringAngle = radians;
        return this;
    }

    public double rfVoltage() {
        return rfVoltage;
    }

    public LatticeElement setRfVoltage(double mv) {
        this.rfVoltage = mv;
        return this;
    }

    public double rfFrequency() {
        return rfFrequency;
    }

    public LatticeElement setRfFrequency(double mhz) {
        this.rfFrequency = mhz;
        return this;
    }

    public double rfPhase() {
        return rfPhase;
    }

    public LatticeElement setRfPhase(double radians) {
        this.rfPhase = radians;
        return this;
    }

    public double aperture() {
        return aperture;
    }

    public LatticeElement setAperture(double metres) {
        this.aperture = metres;
        return this;
    }

    public int rotation() {
        return rotation;
    }

    public LatticeElement setRotation(int quarterTurns) {
        this.rotation = ((quarterTurns % 4) + 4) % 4;
        return this;
    }

    public double bendSign() {
        return bendSign;
    }

    public LatticeElement setBendSign(double sign) {
        this.bendSign = sign >= 0 ? 1.0 : -1.0;
        return this;
    }

    public double capacity() {
        return capacity;
    }

    public LatticeElement setCapacity(double tesla) {
        this.capacity = tesla;
        return this;
    }

    public boolean powered() {
        return powered;
    }

    public LatticeElement setPowered(boolean powered) {
        this.powered = powered;
        return this;
    }

    public int blockX() {
        return blockX;
    }

    public int blockY() {
        return blockY;
    }

    public int blockZ() {
        return blockZ;
    }

    public LatticeElement setBlock(int x, int y, int z) {
        this.blockX = x;
        this.blockY = y;
        this.blockZ = z;
        return this;
    }

    public boolean isMagnet() {
        return kind == Kind.DIPOLE || kind == Kind.QUADRUPOLE || kind == Kind.SEXTUPOLE
                || kind == Kind.STEERING;
    }

    /** True when the element contributes to the energy loss of the beam. */
    public boolean bends() {
        return kind == Kind.DIPOLE;
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "%s[%s] s=%.2f L=%.2f", label, kind, s, length);
    }
}
