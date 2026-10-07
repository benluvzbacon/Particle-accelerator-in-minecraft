package com.particlephysics.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * The accelerator lattice: the ordered optical elements plus the design orbit geometry.
 *
 * <p>The design orbit is a polyline through the centres of the blocks the player placed, so the
 * geometry in game is the geometry used by the physics. Converting between the curvilinear
 * coordinates (s, x, y) used for tracking and world coordinates is done by
 * {@link #toWorld(double, double, double)}.
 */
public final class Lattice {
    private final List<LatticeElement> elements = new ArrayList<>();
    private final List<Vec3> orbit = new ArrayList<>();
    private final List<Double> orbitS = new ArrayList<>();
    private final boolean ring;
    private double length;
    private double minAperture = 0.05;
    private double bendingRadius = Double.POSITIVE_INFINITY;
    private double totalBendAngle;
    private double vacuumPressure = 1.0e-6;
    private double geometricBendingRadius = Double.POSITIVE_INFINITY;
    private double rfFrequencyMHz = 100.0;
    private double totalRfVoltageMV;

    public Lattice(boolean ring) {
        this.ring = ring;
    }

    public boolean isRing() {
        return ring;
    }

    public List<LatticeElement> elements() {
        return elements;
    }

    /** Adds an element and advances the longitudinal position automatically. */
    public LatticeElement add(LatticeElement element, double length) {
        element.setS(this.length);
        element.setLength(length);
        this.length += length;
        elements.add(element);
        return element;
    }

    /** Total length of the lattice in metres (circumference for a ring). */
    public double circumference() {
        return length;
    }

    /**
     * Sets the design orbit polyline. The points must be ordered along the beam direction.
     */
    public void setOrbit(List<Vec3> points) {
        orbit.clear();
        orbitS.clear();
        orbit.addAll(points);
        double s = 0;
        orbitS.add(0.0);
        for (int i = 1; i < points.size(); i++) {
            s += points.get(i).subtract(points.get(i - 1)).length();
            orbitS.add(s);
        }
        if (ring && points.size() > 2) {
            s += points.get(0).subtract(points.get(points.size() - 1)).length();
        }
        this.length = Math.max(this.length, s);
        recalculateGeometry();
    }

    private void recalculateGeometry() {
        double dipoleLength = 0;
        double bendAngle = 0;
        double rfVoltage = 0;
        double minA = Double.POSITIVE_INFINITY;
        for (LatticeElement e : elements) {
            if (e.kind() == LatticeElement.Kind.DIPOLE && Math.abs(e.field()) > 1.0e-9) {
                dipoleLength += e.length();
            }
            if (e.kind() == LatticeElement.Kind.RF_CAVITY && e.powered()) {
                rfVoltage += e.rfVoltage();
            }
            if (e.aperture() > 0.0) {
                minA = Math.min(minA, e.aperture());
            }
        }
        minAperture = Double.isFinite(minA) ? minA : 0.05;
        if (ring) {
            bendAngle = 2.0 * Math.PI;
        } else {
            bendAngle = 0.0;
        }
        totalBendAngle = bendAngle;
        bendingRadius = dipoleLength > 0.01 ? dipoleLength / Math.max(1.0e-6, bendAngle)
                : Double.POSITIVE_INFINITY;
        totalRfVoltageMV = rfVoltage;
    }

    public double ballisticLength() {
        return orbitS.isEmpty() ? 0 : orbitS.get(orbitS.size() - 1);
    }

    public double minAperture() {
        return Math.min(minAperture, 0.25);
    }

    /**
     * Sets the geometric bending radius of the design orbit (the radius of the ring as built).
     */
    public void setGeometricBendingRadius(double metres) {
        this.geometricBendingRadius = metres;
    }

    /** Bending radius used by the physics: the geometric radius when known. */
    public double bendingRadius() {
        if (Double.isFinite(geometricBendingRadius) && geometricBendingRadius > 0.05) {
            return geometricBendingRadius;
        }
        return Math.min(bendingRadius, 1.0e6);
    }

    /** Momentum (MeV/c) matched to the current dipole field on the design orbit. */
    public double matchedMomentum(double dipoleFieldTesla, double chargeState) {
        double rho = bendingRadius();
        if (!Double.isFinite(rho) || rho <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        return Units.RIGIDITY_FACTOR * Math.abs(chargeState) * Math.abs(dipoleFieldTesla) * rho
                * 1000.0;
    }

    /** Point on the design orbit at the given arc length. */
    public Vec3 pointAt(double s) {
        if (orbit.size() < 2) {
            return Vec3.ZERO;
        }
        double total = orbitPathLength();
        double pos = ring ? mod(s, total) : Math.max(0, Math.min(total, s));
        int idx = segmentIndex(pos);
        Vec3 a = orbit.get(idx);
        Vec3 b = orbit.get((idx + 1) % orbit.size());
        double segStart = orbitS.get(idx);
        double segLength = a.subtract(b).length();
        if (segLength < 1.0e-9) {
            return a;
        }
        double t = (pos - segStart) / segLength;
        return a.add(b.subtract(a).scale(t));
    }

    /** Tangent (beam direction) at the given arc length. */
    public Vec3 tangentAt(double s) {
        if (orbit.size() < 2) {
            return new Vec3(0, 0, 1);
        }
        double total = orbitPathLength();
        double pos = ring ? mod(s, total) : Math.max(0, Math.min(total, s));
        int idx = segmentIndex(pos);
        Vec3 a = orbit.get(idx);
        Vec3 b = orbit.get((idx + 1) % orbit.size());
        Vec3 d = b.subtract(a);
        return d.length() < 1.0e-9 ? new Vec3(0, 0, 1) : d.normalise();
    }

    /** Unit vector perpendicular to the beam in the horizontal plane (the "x" direction). */
    public Vec3 horizontalNormalAt(double s) {
        Vec3 tangent = tangentAt(s);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 normal = up.cross(tangent);
        if (normal.length() < 1.0e-6) {
            normal = new Vec3(1, 0, 0);
        }
        return normal.normalise();
    }

    /** Converts curvilinear beam coordinates into world coordinates. */
    public Vec3 toWorld(double s, double x, double y) {
        Vec3 p = pointAt(s);
        Vec3 n = horizontalNormalAt(s);
        return p.add(n.scale(x)).add(new Vec3(0, y, 0));
    }

    /** Finds the closest arc length for a world position (used to place beam losses). */
    public double nearestS(Vec3 world) {
        double best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < orbit.size(); i++) {
            double d = orbit.get(i).subtract(world).lengthSquared();
            if (d < bestDistance) {
                bestDistance = d;
                best = orbitS.get(i);
            }
        }
        return best;
    }

    private double orbitPathLength() {
        double total = orbitS.get(orbitS.size() - 1);
        if (ring && orbit.size() > 2) {
            total += orbit.get(0).subtract(orbit.get(orbit.size() - 1)).length();
        }
        return Math.max(0.01, total);
    }

    private int segmentIndex(double pos) {
        int lo = 0;
        int hi = orbitS.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (orbitS.get(mid) <= pos) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return Math.min(lo, orbit.size() - 2);
    }

    private static double mod(double a, double b) {
        double r = a % b;
        return r < 0 ? r + b : r;
    }

    public double vacuumPressure() {
        return vacuumPressure;
    }

    public void setVacuumPressure(double pressurePa) {
        this.vacuumPressure = pressurePa;
    }

    public double rfFrequencyMHz() {
        return rfFrequencyMHz;
    }

    public void setRfFrequencyMHz(double mhz) {
        this.rfFrequencyMHz = mhz;
    }

    /** Total RF voltage available in MV when all cavities are powered. */
    public double totalRfVoltageMV() {
        double sum = 0;
        for (LatticeElement e : elements) {
            if (e.kind() == LatticeElement.Kind.RF_CAVITY && e.powered()) {
                sum += e.rfVoltage();
            }
        }
        return sum;
    }

    public int countElements(LatticeElement.Kind kind) {
        int count = 0;
        for (LatticeElement e : elements) {
            if (e.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    public double totalMagnetLength(LatticeElement.Kind kind) {
        double sum = 0;
        for (LatticeElement e : elements) {
            if (e.kind() == kind) {
                sum += e.length();
            }
        }
        return sum;
    }

    /** Total bending angle of all dipoles [rad]. */
    public double totalBendAngle() {
        return totalBendAngle;
    }

    /** Revolution frequency in Hz for a particle with the given beta. */
    public double revolutionFrequency(double beta) {
        return Relativity.clampBeta(beta) * Units.C / Math.max(1.0, circumference());
    }

    /** RF harmonic number matched to the given design velocity. */
    public int harmonicNumber(double designBeta) {
        double fRev = revolutionFrequency(designBeta);
        if (fRev < 1.0e-9) {
            return 1;
        }
        return Math.max(1, (int) Math.round(rfFrequencyMHz() * 1.0e6 / fRev));
    }

    /** Longitudinal position of the interaction point, or -1 when there is none. */
    public double interactionPointS() {
        for (LatticeElement e : elements) {
            if (e.kind() == LatticeElement.Kind.COLLISION_POINT) {
                return e.s();
            }
        }
        return -1;
    }

    public double injectionPointS() {
        for (LatticeElement e : elements) {
            if (e.kind() == LatticeElement.Kind.INJECTION) {
                return e.s();
            }
        }
        return 0.0;
    }

    /** Average magnetic field error of the lattice (0 = perfect, 1 = way off). */
    public double averageFieldError() {
        double sum = 0;
        int count = 0;
        for (LatticeElement e : elements) {
            if (e.isMagnet()) {
                sum += Math.abs(e.steeringAngle()) * 1.0e3;
                count++;
            }
        }
        return count == 0 ? 0 : sum / count;
    }
}
