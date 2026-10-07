package com.particlephysics.physics;

/**
 * Everything the operator can control plus the physical state of the machine.
 *
 * <p>Setpoints are ramped towards their targets with rate limits derived from the installed power
 * supplies, exactly like a real synchrotron ramp: magnets cannot be energised instantly, and if the
 * power budget is not met the ramp stalls and the beam is lost.
 */
public final class MachineState {
    // --- operator setpoints -------------------------------------------------------------------
    public double dipoleFieldSetpoint;      // T
    public double quadrupoleScaleSetpoint = 1.0;
    public double sextupoleScaleSetpoint = 1.0;
    public double steeringTrimDegrees;      // orbit correction, degrees
    public double rfVoltageSetpointMV;
    public double rfFrequencySetpointMHz = 100.0;
    public double rfPhaseDegrees = 0.0;
    public double targetEnergyMeV = 100.0;

    // --- actual values ------------------------------------------------------------------------
    public double dipoleField;              // T
    public double quadrupoleScale = 1.0;
    public double sextupoleScale = 1.0;
    public double steeringTrim;
    public double rfVoltageMV;
    public double rfFrequencyMHz = 100.0;
    public double phaseRad;

    // --- machine state ------------------------------------------------------------------------
    public boolean running;
    public boolean valveOpen = true;
    public boolean superconducting;
    public boolean cryoReady;
    public boolean magnetQuench;
    public boolean vacuumFault;
    public boolean rfFault;
    public boolean coolingFault;
    public boolean interlockTripped;
    public double temperatureC = 20.0;
    public double cryoTemperatureK = 293.0;
    public double vacuumPressurePa = 101_325.0;
    public double powerAvailableKW;
    public double powerDemandKW;
    public double powerSuppliedKW;

    // --- calibration ---------------------------------------------------------------------------
    /** Random magnet misalignment/calibration error, in fractions of the nominal field. */
    public double dipoleCalibrationError;
    /** Random quadrupole calibration error. */
    public double quadrupoleCalibrationError;
    /** Residual orbit distortion from misalignments, in millimetres. */
    public double orbitDistortionMm;

    /** Convenience: is the machine able to circulate a beam at all. */
    public boolean canCirculate() {
        return running && !interlockTripped && !magnetQuench && powerSuppliedKW >= powerDemandKW * 0.98;
    }

    /** Sets a target dipole field that corresponds to a given beam momentum. */
    public void setTargetFromMomentum(double momentumMeV, double chargeState, double rho) {
        if (rho <= 0 || !Double.isFinite(rho)) {
            return;
        }
        dipoleFieldSetpoint = Units.RIGIDITY_FACTOR * Math.abs(chargeState) * rho > 0
                ? Math.abs(momentumMeV / 1000.0)
                / (Units.RIGIDITY_FACTOR * Math.abs(chargeState) * rho)
                : 0.0;
    }

    /**
     * Ramps the setpoints towards reality.
     *
     * @param dt            beam time step in seconds
     * @param powerFactor   0..1, how much of the power demand can actually be delivered
     * @param rampRatePerT  tesla per second the installed power supplies can deliver
     */
    public void ramp(double dt, double powerFactor, double rampRatePerT) {
        double rate = Math.max(1.0e-9, rampRatePerT) * Math.max(0.05, powerFactor);
        dipoleField = approach(dipoleField, dipoleFieldSetpoint, rate * dt);
        quadrupoleScale = approach(quadrupoleScale, quadrupoleScaleSetpoint, 0.25 * dt);
        sextupoleScale = approach(sextupoleScale, sextupoleScaleSetpoint, 0.25 * dt);
        steeringTrim = approach(steeringTrim, steeringTrimDegrees, 5.0 * dt);
        rfVoltageMV = approach(rfVoltageMV, rfVoltageSetpointMV, 0.4 * dt);
        rfFrequencyMHz = approach(rfFrequencyMHz, rfFrequencySetpointMHz, 2.0 * dt);
        phaseRad = rfPhaseDegrees * Math.PI / 180.0;
    }

    private static double approach(double current, double target, double maxStep) {
        double delta = target - current;
        if (Math.abs(delta) <= maxStep) {
            return target;
        }
        return current + Math.signum(delta) * maxStep;
    }

    /** Field error actually present on the magnets, including calibration errors. */
    public double effectiveDipoleField() {
        return dipoleField * (1.0 + dipoleCalibrationError) * (magnetQuench ? 0.15 : 1.0);
    }

    public double effectiveQuadrupoleScale() {
        return quadrupoleScale * (1.0 + quadrupoleCalibrationError) * (magnetQuench ? 0.2 : 1.0);
    }

    public String statusLine() {
        if (interlockTripped) {
            return "INTERLOCK TRIPPED";
        }
        if (magnetQuench) {
            return "MAGNET QUENCH";
        }
        if (vacuumFault) {
            return "VACUUM FAULT";
        }
        if (rfFault) {
            return "RF FAULT";
        }
        if (coolingFault) {
            return "COOLING FAULT";
        }
        if (!running) {
            return "STANDBY";
        }
        if (!canCirculate()) {
            return "INSUFFICIENT POWER";
        }
        return "RUNNING";
    }
}
