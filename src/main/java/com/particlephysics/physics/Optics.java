package com.particlephysics.physics;

import java.util.List;

/**
 * Linear beam optics: transfer matrices, Twiss parameters, chromaticity and beam envelopes.
 *
 * <p>This is the classical Courant-Snyder formalism used to design every real accelerator:
 * every element gets a 2x2 transfer matrix, the periodic solution of the one-turn matrix gives the
 * Twiss parameters (beta, alpha, gamma), and the beam size follows from
 * sigma = sqrt(emittance * beta). A lattice whose one-turn matrix has |trace/2| > 1 is unstable -
 * which is exactly what happens in game when the quadrupoles are wired with the wrong polarity or
 * gradient.
 */
public final class Optics {
    private Optics() {
    }

    /** Result of a lattice optics calculation. */
    public static final class Solution {
        public boolean stableX;
        public boolean stableY;
        public double tuneX;      // phase advance per turn / 2pi
        public double tuneY;
        public double betaX;
        public double alphaX;
        public double betaY;
        public double alphaY;
        /** Horizontal dispersion at the start of the lattice [m]. */
        public double dispersion;
        public double dispersionPrime;
        /** Chromaticity, uncorrected, in units of tune per (dp/p). */
        public double chromaticityX;
        public double chromaticityY;
        /** Sampled beam envelope along the ring. */
        public double[] s;
        public double[] betaXSamples;
        public double[] betaYSamples;
        public double[] dispersionSamples;
        public double maxBetaX;
        public double maxDispersion;

        /** Horizontal beta function at an arbitrary longitudinal position. */
        public double betaAt(double position) {
            return sampleAt(betaXSamples, position);
        }

        public double betaYAt(double position) {
            return sampleAt(betaYSamples, position);
        }

        public double dispersionAt(double position) {
            return sampleAt(dispersionSamples, position);
        }

        private double sampleAt(double[] series, double position) {
            if (s == null || s.length == 0) {
                return 1.0;
            }
            int idx = (int) Math.floor(position / Math.max(0.001, s[s.length - 1] - s[0])
                    * (s.length - 1));
            idx = Math.max(0, Math.min(series.length - 1, idx));
            return series[idx];
        }
    }

    /** 2x2 symplectic transfer matrix. */
    public static final class Matrix {
        public final double m11;
        public final double m12;
        public final double m21;
        public final double m22;

        public Matrix(double m11, double m12, double m21, double m22) {
            this.m11 = m11;
            this.m12 = m12;
            this.m21 = m21;
            this.m22 = m22;
        }

        public static Matrix identity() {
            return new Matrix(1, 0, 0, 1);
        }

        public Matrix multiply(Matrix o) {
            return new Matrix(
                    m11 * o.m11 + m12 * o.m21,
                    m11 * o.m12 + m12 * o.m22,
                    m21 * o.m11 + m22 * o.m21,
                    m21 * o.m12 + m22 * o.m22);
        }

        public double trace() {
            return m11 + m22;
        }
    }

    public static Matrix drift(double length) {
        return new Matrix(1.0, length, 0.0, 1.0);
    }

    /**
     * Quadrupole matrix.
     *
     * @param k       normalised gradient k = G / (B*rho) [1/m^2]; positive focuses in x
     * @param length  effective magnetic length [m]
     * @param focusing true for the focusing plane, false for the defocusing plane
     */
    public static Matrix quadrupole(double k, double length, boolean focusing) {
        double kk = focusing ? k : -k;
        if (Math.abs(kk) < 1.0e-9) {
            return drift(length);
        }
        if (kk > 0) {
            double root = Math.sqrt(kk);
            double phi = root * length;
            return new Matrix(Math.cos(phi), Math.sin(phi) / root,
                    -root * Math.sin(phi), Math.cos(phi));
        }
        double root = Math.sqrt(-kk);
        double phi = root * length;
        return new Matrix(Math.cosh(phi), Math.sinh(phi) / root,
                root * Math.sinh(phi), Math.cosh(phi));
    }

    /** Sector dipole matrix for the bending plane. */
    public static Matrix dipoleBend(double angle, double rho) {
        return new Matrix(Math.cos(angle), rho * Math.sin(angle),
                -Math.sin(angle) / rho, Math.cos(angle));
    }

    /** Thin lens kick used for steering magnets and space-charge approximation. */
    public static Matrix thinKick(double deltaXPrime) {
        return new Matrix(1.0, 0.0, deltaXPrime, 1.0);
    }

    /**
     * Solves the periodic optics of a lattice for a given beam momentum.
     *
     * @param elements   lattice elements in longitudinal order
     * @param latticeLen total length of the lattice in metres
     * @param rigidity   magnetic rigidity B*rho in T*m
     */
    public static Solution solve(List<LatticeElement> elements, double latticeLen, double rigidity,
                                 double momentumMeV, double charge) {
        Solution sol = new Solution();
        int samples = Math.max(16, Math.min(512, elements.size() * 4));
        sol.s = new double[samples];
        sol.betaXSamples = new double[samples];
        sol.betaYSamples = new double[samples];
        sol.dispersionSamples = new double[samples];

        // --- one-turn matrices -------------------------------------------------------------
        Matrix mx = Matrix.identity();
        Matrix my = Matrix.identity();
        // Dispersion propagated with a 3x3 matrix, restricted to (D, D', 1).
        double[] disp = new double[]{0.0, 0.0, 1.0};
        for (LatticeElement e : elements) {
            Matrix ex = elementMatrix(e, rigidity, true);
            Matrix ey = elementMatrix(e, rigidity, false);
            mx = mx.multiply(ex);
            my = my.multiply(ey);
            disp = propagateDispersion(disp, e, rigidity);
        }

        double cosMux = mx.trace() / 2.0;
        double cosMuy = my.trace() / 2.0;
        sol.stableX = Math.abs(cosMux) <= 1.0;
        sol.stableY = Math.abs(cosMuy) <= 1.0;

        double muX = sol.stableX ? Math.acos(clamp(cosMux)) : 0.0;
        double muY = sol.stableY ? Math.acos(clamp(cosMuy)) : 0.0;
        if (Double.isNaN(muX)) {
            muX = 0.0;
        }
        if (Double.isNaN(muY)) {
            muY = 0.0;
        }
        sol.tuneX = muX / (2.0 * Math.PI);
        sol.tuneY = muY / (2.0 * Math.PI);

        double sinMuX = Math.sin(muX);
        double sinMuY = Math.sin(muY);
        sol.betaX = Math.abs(sinMuX) > 1.0e-6 ? mx.m12 / sinMuX : 1.0;
        sol.alphaX = Math.abs(sinMuX) > 1.0e-6 ? (mx.m11 - mx.m22) / (2.0 * sinMuX) : 0.0;
        sol.betaY = Math.abs(sinMuY) > 1.0e-6 ? my.m12 / sinMuY : 1.0;
        sol.alphaY = Math.abs(sinMuY) > 1.0e-6 ? (my.m11 - my.m22) / (2.0 * sinMuY) : 0.0;

        if (sol.betaX <= 0 || !Double.isFinite(sol.betaX)) {
            sol.betaX = 1.0;
            sol.alphaX = 0.0;
            sol.stableX = false;
        }
        if (sol.betaY <= 0 || !Double.isFinite(sol.betaY)) {
            sol.betaY = 1.0;
            sol.alphaY = 0.0;
            sol.stableY = false;
        }

        // Periodic dispersion solution: (I - M) D = d  where d is the inhomogeneous part.
        double det = (1.0 - mx.m11) * (1.0 - mx.m22) - (-mx.m12) * (-mx.m21);
        if (Math.abs(det) > 1.0e-9 && sol.stableX) {
            double rhsD = dispersionInhomogeneousD(elements, rigidity);
            double rhsDp = dispersionInhomogeneousDPrime(elements, rigidity);
            sol.dispersion = ((1.0 - mx.m22) * rhsD + mx.m12 * rhsDp) / det;
            sol.dispersionPrime = (mx.m21 * rhsD + (1.0 - mx.m11) * rhsDp) / det;
        }

        // --- chromaticity: xi = -(1/4pi) * integral k(s) D(s) ds ---------------------------------
        double chromaX = 0.0;
        double chromaY = 0.0;
        double[] dispAt = new double[]{sol.dispersion, sol.dispersionPrime, 1.0};
        int index = 0;
        double step = latticeLen / (samples - 1);
        double currentS = 0.0;
        sol.maxBetaX = 0.0;
        sol.maxDispersion = 0.0;
        for (LatticeElement e : elements) {
            double k = 0.0;
            if (e.kind() == LatticeElement.Kind.QUADRUPOLE && Double.isFinite(rigidity)
                    && rigidity > 1.0e-9) {
                k = e.field() / rigidity;
            }
            chromaX += k * dispAt[0] * e.length();
            chromaY += -k * dispAt[0] * e.length();
            dispAt = propagateDispersion(dispAt, e, rigidity);

            // sample the envelope every `step` metres
            while (currentS < e.s() + e.length() && index < samples) {
                sol.s[index] = currentS;
                sol.betaXSamples[index] = sol.betaX;
                sol.betaYSamples[index] = sol.betaY;
                sol.dispersionSamples[index] = dispAt[0];
                sol.maxBetaX = Math.max(sol.maxBetaX, sol.betaX);
                sol.maxDispersion = Math.max(sol.maxDispersion, Math.abs(dispAt[0]));
                currentS += step;
                index++;
            }
            double beta1 = sol.betaX;
            double alpha1 = sol.alphaX;
            Matrix ex = elementMatrix(e, rigidity, true);
            double gamma1 = (1.0 + alpha1 * alpha1) / beta1;
            sol.betaX = ex.m11 * ex.m11 * beta1 - 2.0 * ex.m11 * ex.m12 * alpha1
                    + ex.m12 * ex.m12 * gamma1;
            sol.alphaX = -ex.m11 * ex.m21 * beta1 + (ex.m11 * ex.m22 + ex.m12 * ex.m21) * alpha1
                    - ex.m12 * ex.m22 * gamma1;
            double betaY1 = sol.betaY;
            double alphaY1 = sol.alphaY;
            Matrix ey = elementMatrix(e, rigidity, false);
            double gammaY1 = (1.0 + alphaY1 * alphaY1) / betaY1;
            sol.betaY = ey.m11 * ey.m11 * betaY1 - 2.0 * ey.m11 * ey.m12 * alphaY1
                    + ey.m12 * ey.m12 * gammaY1;
            sol.alphaY = -ey.m11 * ey.m21 * betaY1 + (ey.m11 * ey.m22 + ey.m12 * ey.m21) * alphaY1
                    - ey.m12 * ey.m22 * gammaY1;
            if (!Double.isFinite(sol.betaX) || sol.betaX <= 1.0e-6) {
                sol.betaX = 1.0e-6;
                sol.stableX = false;
            }
            if (!Double.isFinite(sol.betaY) || sol.betaY <= 1.0e-6) {
                sol.betaY = 1.0e-6;
                sol.stableY = false;
            }
        }
        while (index < samples) {
            sol.s[index] = currentS;
            sol.betaXSamples[index] = sol.betaX;
            sol.betaYSamples[index] = sol.betaY;
            sol.dispersionSamples[index] = dispAt[0];
            currentS += step;
            index++;
        }
        sol.chromaticityX = -chromaX / (4.0 * Math.PI);
        sol.chromaticityY = -chromaY / (4.0 * Math.PI);
        return sol;
    }

    private static double dispersionInhomogeneousD(List<LatticeElement> elements, double rigidity) {
        double[] disp = new double[]{0.0, 0.0, 1.0};
        double d = 0.0;
        double dp = 0.0;
        for (LatticeElement e : elements) {
            d += inhomogeneousContribution(e, rigidity);
        }
        return d;
    }

    private static double dispersionInhomogeneousDPrime(List<LatticeElement> elements,
                                                        double rigidity) {
        double dp = 0.0;
        for (LatticeElement e : elements) {
            if (e.kind() == LatticeElement.Kind.DIPOLE && rigidity > 1.0e-9) {
                double angle = e.length() / Math.max(0.01, rigidity / Math.max(1.0e-6, e.field()));
                dp += Math.sin(angle) * (e.length() / Math.max(1.0e-6, angle));
            }
        }
        return dp;
    }

    private static double inhomogeneousContribution(LatticeElement e, double rigidity) {
        if (e.kind() == LatticeElement.Kind.DIPOLE && rigidity > 1.0e-9
                && Math.abs(e.field()) > 1.0e-9) {
            double rho = rigidity / Math.abs(e.field());
            double angle = e.length() / rho;
            return rho * (1.0 - Math.cos(angle));
        }
        return 0.0;
    }

    /** Propagates the (D, D', 1) vector through one element. */
    private static double[] propagateDispersion(double[] d, LatticeElement e, double rigidity) {
        double len = e.length();
        double dd = d[0];
        double ddp = d[1];
        if (e.kind() == LatticeElement.Kind.DIPOLE && rigidity > 1.0e-9
                && Math.abs(e.field()) > 1.0e-9) {
            double rho = rigidity / Math.abs(e.field());
            double angle = len / rho;
            double nd = Math.cos(angle) * dd + rho * Math.sin(angle) * ddp
                    + rho * (1.0 - Math.cos(angle));
            double ndp = -Math.sin(angle) / rho * dd + Math.cos(angle) * ddp + Math.sin(angle);
            return new double[]{nd, ndp, 1.0};
        }
        if (e.kind() == LatticeElement.Kind.QUADRUPOLE && rigidity > 1.0e-9) {
            double k = e.field() / rigidity;
            Matrix m = quadrupole(k, len, true);
            return new double[]{m.m11 * dd + m.m12 * ddp, m.m21 * dd + m.m22 * ddp, 1.0};
        }
        return new double[]{dd + ddp * len, ddp, 1.0};
    }

    /** Transfer matrix of one element. */
    public static Matrix elementMatrix(LatticeElement e, double rigidity, boolean horizontal) {
        double len = e.length();
        if (len <= 0) {
            return Matrix.identity();
        }
        switch (e.kind()) {
            case DIPOLE: {
                if (rigidity > 1.0e-9 && Math.abs(e.field()) > 1.0e-9) {
                    double rho = rigidity / Math.abs(e.field());
                    if (horizontal) {
                        return dipoleBend(len / rho, rho);
                    }
                    // vertical: dipole focusing is weak but non-zero (edge focusing ignored)
                    return drift(len);
                }
                return drift(len);
            }
            case QUADRUPOLE: {
                if (rigidity > 1.0e-9) {
                    double k = e.field() / rigidity;
                    return quadrupole(k, len, horizontal);
                }
                return drift(len);
            }
            case SEXTUPOLE:
                // sextupoles are non-linear; the linear part is treated as a drift
                return drift(len);
            case COLLIMATOR:
                return drift(len);
            default:
                return drift(len);
        }
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    /**
     * Beam size (1 sigma) for the given geometric emittance and beta function.
     */
    public static double beamSize(double emittance, double beta) {
        return Math.sqrt(Math.max(0.0, emittance * beta));
    }

    /**
     * Momentum spread contribution to the horizontal beam size through dispersion.
     */
    public static double dispersiveSize(double dispersion, double dpOverP) {
        return Math.abs(dispersion * dpOverP);
    }
}
