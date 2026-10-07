package com.particlephysics.accelerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import com.particlephysics.block.AxisMachineBlock;
import com.particlephysics.block.MachineBlock;
import com.particlephysics.block.OrientedMachineBlock;
import com.particlephysics.block.ValveBlock;
import com.particlephysics.block.WarningLightBlock;
import com.particlephysics.blockentity.AbstractMachineBlockEntity;
import com.particlephysics.config.ModConfig;
import com.particlephysics.elements.Element;
import com.particlephysics.elements.Elements;
import com.particlephysics.elements.Isotope;
import com.particlephysics.elements.Isotopes;
import com.particlephysics.elements.ItemElements;
import com.particlephysics.elements.NuclearReactions;
import com.particlephysics.physics.Beam;
import com.particlephysics.physics.BeamSimulator;
import com.particlephysics.physics.Collisions;
import com.particlephysics.physics.Lattice;
import com.particlephysics.physics.LatticeElement;
import com.particlephysics.physics.MachineState;
import com.particlephysics.physics.Optics;
import com.particlephysics.physics.ParticleSpecies;
import com.particlephysics.physics.Relativity;
import com.particlephysics.physics.Units;
import com.particlephysics.physics.Vacuum;
import com.particlephysics.physics.Vec3;
import com.particlephysics.radiation.RadiationType;
import com.particlephysics.world.ModState;
import com.particlephysics.world.ResearchState;
import com.particlephysics.world.RadiationState;
import com.particlephysics.world.SiteState;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * The runtime of one accelerator.
 *
 * <p>The network reads the machine back from the world (never from a saved copy), builds the optical
 * lattice from the blocks that are actually placed, and advances the beam, the vacuum, the
 * cryogenics, the power balance, the detector readout and the radiation field. Everything the player
 * sees in the control room is computed here.
 */
public final class AcceleratorNetwork {
    /** One placed component of the machine. */
    public static final class Component {
        public final AcceleratorDesign.Slot slot;
        public final BlockPos pos;
        public MachineKind expected;
        public MachineKind kind;
        public boolean present;
        public boolean correctKind = true;
        public boolean correctOrientation = true;
        /** Quarter turns between the placed orientation and the design orientation. */
        public int rotationError;
        public double capacity;
        public int length = 1;
        public boolean active;
        public double level;
        public ItemStack target = ItemStack.EMPTY;

        Component(AcceleratorDesign.Slot slot, BlockPos pos) {
            this.slot = slot;
            this.pos = pos;
            this.expected = slot.kind;
        }
    }

    /** Live readout of one detector. */
    public static final class DetectorReading {
        public MachineKind kind;
        public BlockPos pos;
        public double rateHz;
        public double lastEnergyGeV;
        public double integratedEnergyGeV;
        public double multiplicity;
        public long events;
        public long chargedTracks;
        public long neutralHits;
        public long muons;
        public String lastEvent = "";

        public String label() {
            return kind == null ? "Detector" : kind.displayName();
        }
    }

    /** A construction error, with the exact position and the exact message. */
    public record Issue(BlockPos pos, String message, boolean error) {
    }

    private static final Random RNG = new Random();

    public final SiteState.Site site;
    public final AcceleratorDesign design;
    public final Lattice lattice;
    public final MachineState machine = new MachineState();
    public final Beam beam = new Beam();
    public Optics.Solution optics = new Optics.Solution();
    public final List<Component> components = new ArrayList<>();
    public final List<DetectorReading> detectors = new ArrayList<>();
    public final List<Issue> issues = new ArrayList<>();
    public final List<String> log = new ArrayList<>();
    public final Map<String, Integer> requiredMaterials = new LinkedHashMap<>();
    public final Map<String, Integer> placedMaterials = new LinkedHashMap<>();

    // --- subsystems ----------------------------------------------------------------------------
    public double vacuumPressurePa = 101_325.0;
    public double pumpSpeedM3S;
    public double chamberVolumeM3;
    public double outgassingPaM3S;
    public double leakPaM3S;
    public double coilTemperatureC = 20.0;
    public double cryoTemperatureK = 293.0;
    public double cryoLoadKW;
    public double coolantAvailableKW;
    public double heliumCharge;
    public double powerSupplyKW;
    public double powerDemandKW;
    public double powerStoredKJ;
    public double radiationPromptSvH;
    public double collisionRateHz;
    public long totalCollisions;
    public double peakEnergyMeV;
    public double luminosity;               // 1/(cm^2 s)
    public double targetActivationBq;
    public String lastFailure = "";
    public long lastRefreshTick;
    public boolean beamWasLost;
    public double doseRateHere;

    private int structureHash;
    private double opticsMomentum = -1.0;
    private long ticks;
    private double structureCheckTimer;

    public AcceleratorNetwork(SiteState.Site site) {
        this.site = site;
        this.design = site.design();
        this.lattice = new Lattice(site.kind == AcceleratorDesign.Kind.RING);
        this.beam.species = ParticleSpecies.PROTON;
        this.beam.energy = 0.0;
        this.beam.intensity = 0.0;
        for (Map.Entry<String, Integer> entry : design.materials().entrySet()) {
            requiredMaterials.put(entry.getKey(), entry.getValue());
        }
    }

    // ------------------------------------------------------------------------------------------
    // Reading the machine from the world
    // ------------------------------------------------------------------------------------------

    /** Rebuilds the component list and the lattice from the blocks in the world. */
    public void refresh(ServerWorld world) {
        components.clear();
        detectors.clear();
        issues.clear();
        placedMaterials.clear();

        List<AcceleratorDesign.Slot> ordered = new ArrayList<>();
        for (AcceleratorDesign.Slot slot : design.slots()) {
            if (slot.isShielding() || slot.isInfrastructure()) {
                continue;
            }
            ordered.add(slot);
        }

        // --- components ---------------------------------------------------------------------
        for (AcceleratorDesign.Slot slot : ordered) {
            BlockPos pos = design.worldPos(slot);
            Component component = new Component(slot, pos);
            BlockState state = world.getBlockState(pos);
            MachineKind actual = actualKind(state);
            component.present = actual != null;
            component.kind = actual != null ? actual : null;
            if (actual != null) {
                component.correctKind = actual == slot.kind;
                component.correctOrientation = orientationMatches(state, design.worldFacing(slot));
                component.rotationError = rotationError(state, design.worldFacing(slot));
                component.capacity = actual.capacity();
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof AbstractMachineBlockEntity machine) {
                    component.active = machine.isActive();
                    component.level = machine.level();
                    component.target = machine.getStack(0);
                }
            }
            components.add(component);
            String key = actual != null ? actual.id() : "missing";
            placedMaterials.merge(key, 1, Integer::sum);
            if (actual == null) {
                issues.add(new Issue(pos, "Missing: " + slot.kind.displayName() + " at "
                        + slot.sectionName, true));
            } else if (!component.correctKind) {
                issues.add(new Issue(pos, "Wrong component: found " + actual.displayName()
                        + " where " + slot.kind.displayName() + " is required at "
                        + slot.sectionName + " (" + describePosition(pos) + ")", true));
            } else if (!component.correctOrientation) {
                issues.add(new Issue(pos, "Wrong orientation: " + slot.kind.displayName()
                        + " at " + slot.sectionName + " must point "
                        + design.worldFacing(slot).asString(), true));
            }
        }

        // --- shielding and infrastructure -----------------------------------------------------
        for (AcceleratorDesign.Slot slot : design.slots()) {
            if (!slot.isShielding() && !slot.isInfrastructure()) {
                continue;
            }
            BlockPos pos = design.worldPos(slot);
            MachineKind actual = actualKind(world.getBlockState(pos));
            placedMaterials.merge(actual != null ? actual.id() : "missing", 1, Integer::sum);
        }

        // --- lattice --------------------------------------------------------------------------
        buildLattice(world, ordered);

        // --- infrastructure accounting --------------------------------------------------------
        scanInfrastructure(world);

        structureHash = computeStructureHash();
        lastRefreshTick = world.getTime();
    }

    private void buildLattice(ServerWorld world, List<AcceleratorDesign.Slot> ordered) {
        lattice.elements().clear();
        if (ordered.isEmpty()) {
            return;
        }
        List<BlockPos> path = new ArrayList<>(ordered.size());
        for (AcceleratorDesign.Slot slot : ordered) {
            path.add(design.worldPos(slot));
        }
        boolean ring = site.kind == AcceleratorDesign.Kind.RING;
        int count = path.size();
        for (int i = 0; i < count; i++) {
            BlockPos pos = path.get(i);
            BlockPos next = path.get((i + 1) % count);
            double length = next.getSquaredDistance(pos) > 0
                    ? Math.sqrt(next.getSquaredDistance(pos)) : 1.0;
            if (!ring && i == count - 1) {
                length = 1.0;
            }
            Component component = components.get(i);
            MachineKind kind = component.kind;
            LatticeElement.Kind elementKind = kind == null ? LatticeElement.Kind.DRIFT
                    : kind.toLatticeKind();
            if (kind == null && component.present) {
                elementKind = LatticeElement.Kind.DRIFT;
            }
            LatticeElement element = new LatticeElement(elementKind,
                    kind == null ? "unbuilt" : kind.displayName());
            element.setBlock(pos.getX(), pos.getY(), pos.getZ());
            applyProperties(element, component, kind);
            lattice.add(element, length);
        }

        // --- design orbit, in world coordinates -------------------------------------------------
        List<Vec3> orbit = new ArrayList<>(count);
        for (BlockPos pos : path) {
            orbit.add(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        }
        lattice.setOrbit(orbit);
        double measuredRadius = measuredRadius(path);
        lattice.setGeometricBendingRadius(measuredRadius);
        lattice.setVacuumPressure(vacuumPressurePa);
        lattice.setRfFrequencyMHz(machine.rfFrequencyMHz);
    }

    private void applyProperties(LatticeElement element, Component component, MachineKind kind) {
        double aperture = switch (kind == null ? MachineKind.BEAM_PIPE : kind) {
            case BEAM_PIPE -> 0.035;
            case VACUUM_CHAMBER -> 0.060;
            case COLLISION_CHAMBER -> 0.120;
            case TARGET_STATION, BEAM_DUMP -> 0.100;
            case TRACKING_DETECTOR, CALORIMETER, SCINTILLATION_DETECTOR, CHERENKOV_DETECTOR,
                 MUON_DETECTOR -> 0.150;
            default -> 0.045;
        };
        element.setAperture(aperture);
        element.setPowered(component.active);
        if (kind == null) {
            // an unbuilt position is an open gap in the vacuum system and a drift for the beam
            element.setAperture(0.20);
            return;
        }
        switch (kind) {
            case DIPOLE_MAGNET, SUPERCONDUCTING_DIPOLE -> {
                element.setField(kind.capacity());
                element.setCapacity(kind.capacity());
                element.setBendSign(component.correctOrientation ? 1.0 : 0.0);
            }
            case QUADRUPOLE_MAGNET -> {
                element.setField(kind.capacity());
                element.setCapacity(kind.capacity());
                element.setRotation(component.rotationError % 2 == 0 ? 0 : 1);
            }
            case SEXTUPOLE_MAGNET -> {
                element.setField(kind.capacity());
                element.setCapacity(kind.capacity());
                element.setRotation(component.rotationError % 2 == 0 ? 0 : 1);
            }
            case STEERING_MAGNET -> {
                element.setField(kind.capacity());
                element.setSteeringAngle(0.0);
                element.setBendSign(component.correctOrientation ? 1.0 : -1.0);
            }
            case RF_CAVITY -> {
                element.setRfVoltage(kind.capacity());
                element.setRfFrequency(site.rfFrequencyMHz);
                element.setRfPhase(Math.toRadians(site.rfPhaseDegrees));
            }
            case VACUUM_VALVE -> element.setAperture(0.035);
            default -> {
                // drift-like: nothing to configure
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Simulation step
    // ------------------------------------------------------------------------------------------

    public void tick(ServerWorld world, double dt, List<ServerPlayerEntity> nearbyPlayers) {
        ticks++;
        ModConfig config = ModConfig.get();

        structureCheckTimer += dt;
        if (structureCheckTimer > 2.0) {
            structureCheckTimer = 0.0;
            int hash = computeStructureHash();
            if (hash != structureHash) {
                refresh(world);
                log("Structure changed: machine re-validated");
            }
        }

        updateResources(world, dt, config);
        updateMachineState(dt, config);

        boolean hadBeam = beam.intensity > 1.0;
        if (machine.running && !machine.interlockTripped) {
            injectIfEmpty();
            stepBeam(dt, config);
        } else {
            beam.intensity = Math.max(0.0, beam.intensity * Math.exp(-dt / 60.0));
            if (beam.intensity < 1.0) {
                beam.clear();
                beam.intensity = 0.0;
            }
        }
        boolean hasBeam = beam.intensity > 1.0;
        if (hadBeam && !hasBeam) {
            beamWasLost = true;
            log("Beam lost: " + (beam.lossReason.isEmpty() ? "unknown" : beam.lossReason));
            site.running = false;
            machine.running = false;
        }

        runCollisions(world, dt, config, nearbyPlayers);
        writeBackReadouts(world);
        updateVisuals(world);
    }

    // ------------------------------------------------------------------------------------------
    // Infrastructure: vacuum, cooling, cryogenics, power
    // ------------------------------------------------------------------------------------------

    private void scanInfrastructure(ServerWorld world) {
        pumpSpeedM3S = 0.0;
        chamberVolumeM3 = 0.0;
        leakPaM3S = 0.0;
        coolantAvailableKW = 0.0;
        cryoLoadKW = 0.0;
        powerSupplyKW = 0.0;
        powerStoredKJ = 0.0;
        heliumCharge = 0.0;
        int superconducting = 0;

        for (Component component : components) {
            if (component.kind == null) {
                // an open gap vents the section
                leakPaM3S += 1.0e-1;
                continue;
            }
            switch (component.kind) {
                case VACUUM_PUMP -> pumpSpeedM3S += component.kind.capacity()
                        * (component.active ? 1.0 : 0.4);
                case VACUUM_CHAMBER -> chamberVolumeM3 += 0.45;
                case BEAM_PIPE, VACUUM_VALVE -> chamberVolumeM3 += 0.035;
                case COLLISION_CHAMBER -> chamberVolumeM3 += 1.2;
                case RF_CAVITY -> chamberVolumeM3 += 0.12;
                case COOLING_UNIT -> coolantAvailableKW += component.kind.capacity()
                        * (component.active ? 1.0 : 0.0);
                case CRYOGENIC_UNIT -> {
                    cryoLoadKW += component.kind.capacity() * (component.active ? 1.0 : 0.0);
                    heliumCharge += component.level;
                }
                case POWER_SUPPLY -> powerSupplyKW += component.kind.capacity()
                        * (component.active ? 1.0 : 0.5);
                case POWER_CABLE -> powerSupplyKW += component.kind.capacity() * 0.25;
                case SUPERCONDUCTING_DIPOLE -> superconducting++;
                default -> {
                    // everything else draws power, computed in updateResources
                }
            }
        }
        if (superconducting > 0) {
            cryoLoadKW = Math.min(cryoLoadKW, 1.0e9);
        }
        // surface outgassing of stainless steel: thermal desorption of hydrogen
        outgassingPaM3S = 1.3e-6 * chamberVolumeM3 * 12.0;
    }

    private void updateResources(ServerWorld world, double dt, ModConfig config) {
        // --- vacuum ---------------------------------------------------------------------------
        if (config.vacuumEnabled) {
            if (!site.valveOpen) {
                leakPaM3S += 2.0e-2;
            }
            double pumps = pumpSpeedM3S;
            vacuumPressurePa = Vacuum.pumpStep(chamberVolumeM3, chamberVolumeM3 * 12.0, pumps,
                    outgassingPaM3S + leakPaM3S, vacuumPressurePa, dt);
            machine.vacuumFault = vacuumPressurePa > 1.0e-2;
        } else {
            vacuumPressurePa = 1.0e-7;
            machine.vacuumFault = false;
        }
        lattice.setVacuumPressure(vacuumPressurePa);

        // --- magnet power and cooling ----------------------------------------------------------
        double magnetPower = 0.0;
        double heatLoad = 0.0;
        int resistiveMagnets = 0;
        int superconductingMagnets = 0;
        double magnetLength = 0.0;
        for (Component component : components) {
            MachineKind kind = component.kind;
            if (kind == null || !kind.isMagnet()) {
                continue;
            }
            double ratio = kind.capacity() > 0
                    ? Math.min(1.5, Math.abs(setpointFor(kind)) / kind.capacity()) : 0.0;
            magnetLength += 1.0;
            if (kind == MachineKind.SUPERCONDUCTING_DIPOLE) {
                superconductingMagnets++;
                magnetPower += 0.05 * ratio * ratio * kind.capacity();
                heatLoad += 0.25;     // cryostat static heat leak, watt scale mapped to kW
            } else {
                resistiveMagnets++;
                double field = ratio * kind.capacity();
                magnetPower += 12.0 * field * field * 1.0;
                heatLoad += 0.6 * 12.0 * field * field;
            }
        }
        machine.superconducting = superconductingMagnets > 0 && resistiveMagnets == 0;
        double rfPower = Math.abs(machine.rfVoltageMV) * 1000.0
                * Math.max(1.0e-3, beam.current) + 4.0 * Math.abs(machine.rfVoltageMV);
        double vacuumPower = pumpSpeedM3S * 0.6 + 0.4;
        double cryoPower = superconductingMagnets * 3.0;
        double controlPower = 0.6 * Math.max(1, components.size() / 40);
        double demand = (magnetPower + rfPower + vacuumPower + cryoPower + controlPower)
                * config.powerScale;
        powerDemandKW = demand;
        machine.powerAvailableKW = powerSupplyKW;
        machine.powerDemandKW = demand;
        double supplied = Math.min(powerSupplyKW, demand);
        machine.powerSuppliedKW = supplied;
        powerStoredKJ = powerSupplyKW > 0 ? powerStoredKJ : 0;

        if (demand > 0) {
            double load = supplied / demand;
            if (load < 0.98) {
                // brown-out: the magnets sag, the RF drops out
                machine.dipoleField *= 0.6 + 0.4 * load;
                machine.rfVoltageMV *= 0.6 + 0.4 * load;
                if (load < 0.5) {
                    lastFailure = "Power failure: " + String.format(Locale.ROOT, "%.0f kW required, %.0f kW available", demand, powerSupplyKW);
                }
            }
        }

        // --- cooling --------------------------------------------------------------------------
        double coolingCapacity = coolantAvailableKW;
        double targetTemperature = 20.0 + (heatLoad > 0
                ? 80.0 * Math.max(0.0, heatLoad - coolingCapacity) / Math.max(1.0, heatLoad) : 0.0);
        if (coolingCapacity <= 0.0 && heatLoad > 0.0) {
            targetTemperature = 220.0;
        }
        coilTemperatureC += (targetTemperature - coilTemperatureC) * Math.min(1.0, dt / 8.0);
        if (coilTemperatureC > 90.0) {
            machine.coolingFault = true;
            if (lastFailure.isEmpty()) {
                lastFailure = "Overheating: magnets at "
                        + String.format(Locale.ROOT, "%.0f C", coilTemperatureC);
            }
            machine.magnetQuench = true;
        } else {
            machine.coolingFault = false;
            if (coilTemperatureC < 45.0 && !machine.interlockTripped) {
                machine.magnetQuench = false;
            }
        }

        // --- cryogenics -----------------------------------------------------------------------
        if (superconductingMagnets > 0) {
            double cryoTarget = cryoLoadKW > 0.5 && heliumCharge > 0.0 ? 4.5 : 293.0;
            cryoTemperatureK += (cryoTarget - cryoTemperatureK) * Math.min(1.0, dt / 20.0);
            machine.cryoReady = cryoTemperatureK < 6.0;
            if (!machine.cryoReady && site.superconducting) {
                machine.magnetQuench = true;
                if (lastFailure.isEmpty()) {
                    lastFailure = heliumCharge <= 0.0 ? "Cryo failure: no helium"
                            : "Cryo failure: magnets at "
                                    + String.format(Locale.ROOT, "%.0f K", cryoTemperatureK);
                }
            }
            heliumCharge = Math.max(0.0, heliumCharge - cryoLoadKW * 1.0e-4 * dt);
        } else {
            machine.cryoReady = true;
            cryoTemperatureK = 293.0;
        }
        machine.temperatureC = coilTemperatureC;
        machine.cryoTemperatureK = cryoTemperatureK;
        machine.vacuumPressurePa = vacuumPressurePa;

        if (supplied >= demand * 0.98 && !machine.magnetQuench && !machine.coolingFault) {
            lastFailure = "";
        }
        machine.interlockTripped = machine.magnetQuench;
        site.lastBeamTime = world.getTime();
    }

    /** The setpoint that drives a given magnet type. */
    private double setpointFor(MachineKind kind) {
        return switch (kind) {
            case DIPOLE_MAGNET, SUPERCONDUCTING_DIPOLE -> site.dipoleField;
            case QUADRUPOLE_MAGNET -> site.quadrupoleScale * kind.capacity();
            case SEXTUPOLE_MAGNET -> site.sextupoleScale * kind.capacity();
            case STEERING_MAGNET -> site.steeringTrim * 0.05;
            default -> 0.0;
        };
    }

    // ------------------------------------------------------------------------------------------
    // The beam
    // ------------------------------------------------------------------------------------------

    private void updateMachineState(double dt, ModConfig config) {
        machine.dipoleFieldSetpoint = site.dipoleField;
        machine.quadrupoleScaleSetpoint = site.quadrupoleScale;
        machine.sextupoleScaleSetpoint = site.sextupoleScale;
        machine.steeringTrimDegrees = site.steeringTrim;
        machine.rfVoltageSetpointMV = site.rfVoltageMV;
        machine.rfFrequencySetpointMHz = site.rfFrequencyMHz;
        machine.rfPhaseDegrees = site.rfPhaseDegrees;
        machine.targetEnergyMeV = site.targetEnergyMeV;
        machine.running = site.running;
        machine.valveOpen = site.valveOpen;
        machine.superconducting = site.superconducting;

        // magnets ramp towards their setpoint at the rate an operator would use
        double rampRate = 0.25; // T per second
        double steps = dt * rampRate;
        machine.dipoleField += clamp(machine.dipoleFieldSetpoint - machine.dipoleField, steps);
        machine.quadrupoleScale += clamp(machine.quadrupoleScaleSetpoint - machine.quadrupoleScale,
                dt * 0.25);
        machine.sextupoleScale += clamp(machine.sextupoleScaleSetpoint - machine.sextupoleScale,
                dt * 0.25);
        machine.steeringTrim += clamp(machine.steeringTrimDegrees - machine.steeringTrim,
                dt * 0.5);
        machine.rfVoltageMV += clamp(machine.rfVoltageSetpointMV - machine.rfVoltageMV,
                dt * 0.5);
        machine.rfFrequencyMHz += clamp(machine.rfFrequencySetpointMHz - machine.rfFrequencyMHz,
                dt * 5.0);
    }

    private void injectIfEmpty() {
        if (beam.intensity > 1.0) {
            return;
        }
        ParticleSpecies species = ParticleSpecies.byName(site.sourceSpecies);
        if (species == null || !species.isCharged()) {
            species = ParticleSpecies.PROTON;
        }
        double injectionEnergy = injectionEnergy(species);
        if (beam.species != species || beam.energy < injectionEnergy * 0.5) {
            beam.species = species;
            beam.energy = injectionEnergy;
            beam.clear();
            BeamSimulator.populate(beam, lattice, ModConfig.get().simulationQuality(), RNG,
                    site.injectionIntensity);
        }
        beam.intensity = Math.max(beam.intensity, site.injectionIntensity);
        beam.current = beam.computeCurrent(lattice.revolutionFrequency(beam.beta()));
    }

    private double injectionEnergy(ParticleSpecies species) {
        // The injector is a small linac: 0.1 MV per injector block, plus the source energy.
        double injectors = 1.0;
        for (Component component : components) {
            if (component.kind == MachineKind.INJECTOR && component.active) {
                injectors += 1.0;
            }
        }
        double voltage = injectors * 0.15;    // MV
        double charge = Math.abs(species.charge());
        return Math.max(0.05, voltage * charge);
    }

    private void stepBeam(double dt, ModConfig config) {
        double charge = beam.species.charge();
        double momentumMeV = beam.momentum();
        double rigidity = Relativity.rigidity(momentumMeV / 1000.0,
                Math.abs(charge) < 1.0e-9 ? 1.0 : charge);
        if (optics == null || Math.abs(momentumMeV - opticsMomentum) > 0.02 * Math.max(1.0, momentumMeV)) {
            optics = Optics.solve(lattice.elements(), lattice.circumference(), rigidity,
                    momentumMeV, charge);
            opticsMomentum = momentumMeV;
        }

        // --- RF synchronisation -----------------------------------------------------------------
        double beta = beam.beta();
        double revolution = lattice.revolutionFrequency(beta);
        int harmonic = Math.max(1, lattice.harmonicNumber(beta));
        double requiredFrequency = revolution * harmonic / 1.0e6;
        double frequencyError = requiredFrequency > 0
                ? Math.abs(machine.rfFrequencyMHz - requiredFrequency) / requiredFrequency : 0.0;
        machine.rfFault = frequencyError > 2.0e-3;
        if (machine.rfFault) {
            machine.rfVoltageMV = 0.0;
            if (lastFailure.isEmpty()) {
                lastFailure = "RF desynchronised: set "
                        + String.format(Locale.ROOT, "%.4f MHz", requiredFrequency);
            }
        }

        // --- field matching ---------------------------------------------------------------------
        double rho = lattice.bendingRadius();
        double requiredField = Double.isFinite(rho) && rho > 0.05 ? rigidity / rho : 0.0;
        double fieldError = requiredField > 1.0e-9
                ? (machine.effectiveDipoleField() - requiredField) / requiredField : 0.0;
        machine.dipoleCalibrationError = fieldError;
        machine.quadrupoleCalibrationError = 0.0;
        machine.orbitDistortionMm = Math.abs(fieldError) * 1000.0 * Math.max(1.0, rho / 10.0);

        BeamSimulator.StepResult result = BeamSimulator.step(beam, lattice, machine, optics, dt,
                config.simulationQuality(), RNG);
        beam.current = beam.computeCurrent(revolution);
        peakEnergyMeV = Math.max(peakEnergyMeV, beam.energy);

        if (result.beamLost) {
            beamWasLost = true;
            log("Beam lost: " + result.lossReason);
            // beam loss deposits its energy in the pipe: this is the dominant radiation source
            double energyJoules = beam.storedEnergyJoules();
            if (energyJoules > 1.0) {
                BlockPos lossPos = lossPosition(worldPosOfLoss(result));
                ModState.radiation().addPromptSource(lossPos, energyJoules,
                        RadiationType.GAMMA, ModConfig.get().radiationScale);
                ModState.radiation().addPromptSource(lossPos, energyJoules * 0.3,
                        RadiationType.NEUTRON, ModConfig.get().radiationScale);
            }
            beam.clear();
            beam.intensity = 0.0;
            site.running = false;
            machine.running = false;
        }
        site.firstBeam = site.firstBeam || beam.intensity > 1.0 && beam.energy > 1.0;
    }

    private double worldPosOfLoss(BeamSimulator.StepResult result) {
        double s = lattice.injectionPointS();
        if (!result.losses.isEmpty()) {
            s = result.losses.get(0).s();
        }
        return s;
    }

    private BlockPos lossPosition(double s) {
        Vec3 point = lattice.pointAt(s);
        return new BlockPos((int) Math.floor(point.x), (int) Math.floor(point.y),
                (int) Math.floor(point.z));
    }

    // ------------------------------------------------------------------------------------------
    // Collisions, detectors, radiation and discovery
    // ------------------------------------------------------------------------------------------

    private void runCollisions(ServerWorld world, double dt, ModConfig config,
                               List<ServerPlayerEntity> nearbyPlayers) {
        collisionRateHz = 0.0;
        if (beam.intensity <= 1.0 || beam.energy <= 0.0) {
            lastEventDecay(dt);
            return;
        }

        double sqrtSGeV;
        ParticleSpecies species = beam.species;
        ParticleSpecies partner = collisionPartner(species);
        if (site.kind == AcceleratorDesign.Kind.RING) {
            // colliding beams: the counter-rotating bunch has the same energy and opposite momentum
            double total = beam.energy + species.mass();
            double momentum = beam.momentum();
            sqrtSGeV = Relativity.centreOfMassEnergy(total, momentum, species.mass(), total,
                    momentum, partner.mass(), -1.0) / 1000.0;
            double sigmaCm2 = Collisions.totalCrossSection(species, partner, sqrtSGeV) * 1.0e-27;
            double fRev = lattice.revolutionFrequency(beam.beta());
            luminosity = Beam.luminosity(fRev, site.bunches, beam.intensity, beam.intensity,
                    beam.typicalSize(optics), beam.typicalSize(optics));
            collisionRateHz = luminosity * sigmaCm2 * config.elementProductionScale;
        } else {
            // fixed target: the beam is stopped in the target station
            Component target = targetStation();
            int z = target == null ? 0 : ItemElements.dominantElement(target.target);
            if (z == 0) {
                z = 26;
            }
            double energy = beam.energy * config.energyScale;
            sqrtSGeV = Math.sqrt(2.0 * species.mass() * energy) / 1000.0;
            Element element = Elements.byZ(z);
            Isotope isotope = element == null ? null : element.primaryIsotope();
            int a = isotope == null ? z * 2 : isotope.massNumber();
            double sigmaCm2 = Collisions.totalCrossSection(species,
                    Collisions.speciesForNucleus(z, a), sqrtSGeV) * 1.0e-27;
            // number of target atoms per cm^2 in a 0.1 mm foil
            collisionRateHz = beam.current / Units.ELEMENTARY_CHARGE * 1.0e-3
                    * 6.0e20 * sigmaCm2 * config.elementProductionScale;
        }

        int events = poisson(collisionRateHz * dt);
        int generated = Math.min(events, 6);
        for (int i = 0; i < generated; i++) {
            Collisions.Event event;
            if (site.kind == AcceleratorDesign.Kind.RING) {
                Vec3 direction = lattice.tangentAt(lattice.interactionPointS());
                event = Collisions.collide(beam.species, beam.energy, direction,
                        partner, beam.energy, direction.scale(-1.0), RNG);
            } else {
                Component target = targetStation();
                int z = target == null ? 0 : ItemElements.dominantElement(target.target);
                if (z == 0) {
                    z = 26;
                }
                Element element = Elements.byZ(z);
                Isotope isotope = element == null ? null : element.primaryIsotope();
                int a = isotope == null ? z * 2 : isotope.massNumber();
                event = Collisions.fixedTarget(beam.species, beam.energy * config.energyScale, z, a,
                        RNG);
            }
            if (event != null) {
                totalCollisions++;
                recordEvent(world, event, config, nearbyPlayers);
            }
        }
        if (events > generated) {
            // the remaining events are counted statistically, their energy still feeds the
            // calorimeters and the radiation field
            double extraEnergyGeV = (events - generated) * sqrtSGeV;
            for (DetectorReading reading : detectors) {
                if (reading.kind == MachineKind.CALORIMETER) {
                    reading.integratedEnergyGeV += extraEnergyGeV;
                    reading.events += events - generated;
                }
            }
            totalCollisions += events - generated;
        }
        lastEventDecay(dt);
    }

    private void recordEvent(ServerWorld world, Collisions.Event event, ModConfig config,
                             List<ServerPlayerEntity> nearbyPlayers) {
        // --- detector response ------------------------------------------------------------------
        int charged = 0;
        int muons = 0;
        int neutrals = 0;
        double emEnergy = 0.0;
        double hadEnergy = 0.0;
        for (Collisions.Product product : event.products) {
            ParticleSpecies productSpecies = product.species();
            if (productSpecies.isCharged()) {
                charged++;
            }
            switch (productSpecies) {
                case PHOTON, ELECTRON, POSITRON -> emEnergy += product.energyMeV();
                case MUON, ANTIMUON -> muons++;
                default -> hadEnergy += product.energyMeV();
            }
            if (!productSpecies.isCharged()) {
                neutrals++;
            }
        }
        for (DetectorReading reading : detectors) {
            reading.rateHz = collisionRateHz;
            reading.multiplicity = event.chargedMultiplicity;
            reading.lastEvent = event.channel + "  sqrt(s)=" + String.format(Locale.ROOT, "%.1f",
                    event.sqrtS) + " GeV  n=" + event.totalMultiplicity
                    + (event.rare ? "  [" + event.rareProcess + "]" : "");
            switch (reading.kind) {
                case TRACKING_DETECTOR -> {
                    reading.chargedTracks += charged;
                    reading.events++;
                    reading.lastEnergyGeV = emEnergy / 1000.0;
                }
                case CALORIMETER -> {
                    reading.integratedEnergyGeV += (emEnergy + hadEnergy) / 1000.0;
                    reading.lastEnergyGeV = (emEnergy + hadEnergy) / 1000.0;
                    reading.events++;
                }
                case SCINTILLATION_DETECTOR -> {
                    reading.chargedTracks += charged;
                    reading.events++;
                }
                case CHERENKOV_DETECTOR -> {
                    int aboveThreshold = 0;
                    for (Collisions.Product product : event.products) {
                        if (product.species().mass() <= 0) {
                            continue;
                        }
                        double total = product.energyMeV() + product.species().mass();
                        double betaProduct = Math.sqrt(Math.max(0.0,
                                1.0 - Math.pow(product.species().mass() / total, 2.0)));
                        if (betaProduct > 0.66) {
                            aboveThreshold++;
                        }
                    }
                    reading.chargedTracks += aboveThreshold;
                    reading.events++;
                }
                case MUON_DETECTOR -> {
                    reading.muons += muons;
                    reading.events++;
                }
                case RADIATION_DETECTOR -> {
                    reading.events++;
                    reading.lastEnergyGeV = radiationPromptSvH;
                }
                case COLLISION_CHAMBER -> {
                    reading.events++;
                    reading.lastEnergyGeV = event.sqrtS;
                }
                default -> {
                    // other components do not read the event out
                }
            }
        }

        // --- radiation --------------------------------------------------------------------------
        double eventEnergyJoules = (emEnergy + hadEnergy) * 1.0e6 * Units.EV_TO_JOULE;
        BlockPos sourcePos = eventPosition();
        RadiationState radiation = ModState.radiation();
        radiation.addPromptSource(sourcePos, eventEnergyJoules * 0.35, RadiationType.GAMMA,
                config.radiationScale);
        radiation.addPromptSource(sourcePos, eventEnergyJoules * 0.25, RadiationType.NEUTRON,
                config.radiationScale);
        radiation.addPromptSource(sourcePos, eventEnergyJoules * 0.10, RadiationType.MUON,
                config.radiationScale);
        radiationPromptSvH = radiation.doseRateMicroSvPerHour(world, centerVec());

        // --- nuclear physics: activation, spallation, fusion, fission ---------------------------
        reactWithTarget(world, event, config);

        // --- research ---------------------------------------------------------------------------
        for (ServerPlayerEntity player : nearbyPlayers) {
            ResearchState.PlayerResearch research = ModState.research(player);
            ResearchState.ExperimentRecord record = new ResearchState.ExperimentRecord();
            record.type = event.channel;
            record.sqrtSGeV = event.sqrtS;
            record.multiplicity = event.totalMultiplicity;
            record.energyGeV = (emEnergy + hadEnergy) / 1000.0;
            record.invariantMassGeV = event.resonanceMass;
            record.rare = event.rare;
            record.gameTime = world.getTime();
            research.recordExperiment(record);
            research.raiseMilestone("max_sqrt_s_gev", event.sqrtS);
            research.raiseMilestone("max_multiplicity", event.totalMultiplicity);
            research.collisionCount++;
            if (event.rare) {
                research.unlock("rare_process_" + event.rareProcess.toLowerCase(Locale.ROOT));
            }
            discoverProducts(research, event);
        }

        // --- collision flash --------------------------------------------------------------------
        if (ModConfig.get().renderEvents && ModConfig.get().renderBeam) {
            BlockPos pos = eventPosition();
            world.spawnParticles(ParticleTypes.FLASH, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, 2, 0.2, 0.2, 0.2, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5,
                    pos.getZ() + 0.5, (int) Math.min(40, 4 + event.totalMultiplicity),
                    0.35, 0.35, 0.35, 0.05);
        }
    }

    private void discoverProducts(ResearchState.PlayerResearch research, Collisions.Event event) {
        for (Collisions.Product product : event.products) {
            ParticleSpecies species = product.species();
            int z = species.atomicNumber();
            if (z < 1 || z > 118) {
                continue;
            }
            Element element = Elements.byZ(z);
            if (element != null) {
                research.discover(element, ResearchState.DiscoverySource.COLLISION);
            }
            int a = species.nucleons();
            Isotope isotope = Isotopes.find(z, a);
            if (isotope != null && isotope.element() != null) {
                research.discoverIsotope(isotope, ResearchState.DiscoverySource.COLLISION);
            }
        }
    }

    /** Nuclear reactions in the target station: activation, spallation, fission, fusion. */
    private void reactWithTarget(ServerWorld world, Collisions.Event event, ModConfig config) {
        Component target = targetStation();
        if (target == null) {
            return;
        }
        int z = ItemElements.dominantElement(target.target);
        if (z == 0) {
            return;
        }
        Element element = Elements.byZ(z);
        if (element == null) {
            return;
        }
        double energy = beam.energy * config.energyScale;
        if (energy < 1.0) {
            return;
        }
        NuclearReactions.Reaction reaction = NuclearReactions.react(beam.species, energy, element,
                RNG);
        if (reaction == null || reaction.products().isEmpty()) {
            return;
        }
        double scale = config.elementProductionScale * 1.0e-3;
        for (Isotope product : reaction.products()) {
            double grams = 1.0e-6 * scale;
            RadiationState.Source source = ModState.radiation()
                    .addIsotopeSource(target.pos, product, grams);
            if (source != null) {
                targetActivationBq = source.activity;
            }
        }
        // discovery for everyone nearby is handled by the caller; here we only produce material
        log("Nuclear reaction in target: " + reaction.description());
    }

    private void lastEventDecay(double dt) {
        for (DetectorReading reading : detectors) {
            reading.rateHz = Math.max(0.0, reading.rateHz);
        }
    }

    private Component targetStation() {
        for (Component component : components) {
            if (component.kind == MachineKind.TARGET_STATION && !component.target.isEmpty()) {
                return component;
            }
        }
        return null;
    }

    private BlockPos eventPosition() {
        for (Component component : components) {
            if (component.kind == MachineKind.COLLISION_CHAMBER) {
                return component.pos;
            }
        }
        for (Component component : components) {
            if (component.kind == MachineKind.TARGET_STATION) {
                return component.pos;
            }
        }
        return site.origin();
    }

    private Vec3 centerVec() {
        BlockPos pos = eventPosition();
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    // ------------------------------------------------------------------------------------------
    // Readouts and visuals
    // ------------------------------------------------------------------------------------------

    private void writeBackReadouts(ServerWorld world) {
        for (Component component : components) {
            if (component.kind == null) {
                continue;
            }
            BlockEntity be = world.getBlockEntity(component.pos);
            if (!(be instanceof AbstractMachineBlockEntity machineBlock)) {
                continue;
            }
            switch (component.kind) {
                case VACUUM_GAUGE -> machineBlock.setMeasuredValue(vacuumPressurePa);
                case BEAM_MONITOR -> machineBlock.setMeasuredValue(beam.current);
                case RADIATION_DETECTOR -> machineBlock.setMeasuredValue(radiationPromptSvH);
                case VACUUM_PUMP -> {
                    machineBlock.setActive(pumpsEnabled());
                    machineBlock.setLevel(pumpSpeedM3S);
                }
                case COOLING_UNIT -> {
                    machineBlock.setLevel(coolantAvailableKW);
                    machineBlock.setActive(coolantAvailableKW > 0);
                }
                case CRYOGENIC_UNIT -> {
                    machineBlock.setLevel(heliumCharge);
                    machineBlock.setActive(cryoTemperatureK < 6.0 || cryoLoadKW > 0.5);
                }
                case POWER_SUPPLY -> machineBlock.setMeasuredValue(powerSupplyKW);
                case PARTICLE_SOURCE, INJECTOR -> machineBlock.setActive(machine.running);
                default -> {
                    for (DetectorReading reading : detectors) {
                        if (reading.pos.equals(component.pos)) {
                            machineBlock.setMeasuredValue(reading.rateHz);
                        }
                    }
                }
            }
        }
        // detector readings into their block entities
        for (DetectorReading reading : detectors) {
            BlockEntity be = world.getBlockEntity(reading.pos);
            if (be instanceof AbstractMachineBlockEntity machineBlock) {
                machineBlock.setMeasuredValue(reading.rateHz);
            }
        }
    }

    private boolean pumpsEnabled() {
        return powerSupplyKW > 1.0 || pumpSpeedM3S == 0.0;
    }

    private void updateVisuals(ServerWorld world) {
        ModConfig config = ModConfig.get();
        // warning lights follow the measured dose rate
        double threshold = 2.0;
        boolean alarm = radiationPromptSvH > threshold || !lastFailure.isEmpty();
        for (Component component : components) {
            if (component.kind != MachineKind.WARNING_LIGHT) {
                continue;
            }
            BlockState state = world.getBlockState(component.pos);
            if (state.getBlock() instanceof WarningLightBlock
                    && state.contains(WarningLightBlock.LIT)
                    && state.get(WarningLightBlock.LIT) != alarm) {
                world.setBlockState(component.pos, state.with(WarningLightBlock.LIT, alarm), 3);
            }
        }
        if (!config.renderBeam || !config.renderFields) {
            return;
        }
        // a faint glow along the beam path where the beam is circulating
        if (beam.intensity > 1.0 && ticks % 5 == 0) {
            int samples = Math.min(12, components.size());
            for (int i = 0; i < samples; i++) {
                Component component = components.get((int) (Math.random() * components.size()));
                if (component.kind == null) {
                    continue;
                }
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                        component.pos.getX() + 0.5, component.pos.getY() + 0.5,
                        component.pos.getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
        if (lastFailure.isEmpty() || ticks % 40 != 0) {
            return;
        }
        // failure smoke at the machine that failed
        BlockPos pos = eventPosition();
        world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0,
                pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.01);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    private MachineKind actualKind(BlockState state) {
        if (state == null || !(state.getBlock() instanceof MachineBlock machine)) {
            return null;
        }
        return machine.kind();
    }

    private boolean orientationMatches(BlockState state, Direction required) {
        if (required == null) {
            return true;
        }
        if (state.getBlock() instanceof AxisMachineBlock) {
            Direction.Axis axis = AxisMachineBlock.axisOf(state);
            return axis == required.getAxis();
        }
        if (state.getBlock() instanceof OrientedMachineBlock) {
            return OrientedMachineBlock.quarterTurns(state.get(OrientedMachineBlock.FACING),
                    required) == 0;
        }
        return true;
    }

    private int rotationError(BlockState state, Direction required) {
        if (required == null) {
            return 0;
        }
        if (state.getBlock() instanceof AxisMachineBlock) {
            Direction.Axis axis = AxisMachineBlock.axisOf(state);
            return axis == required.getAxis() ? 0 : 1;
        }
        if (state.getBlock() instanceof OrientedMachineBlock) {
            return OrientedMachineBlock.quarterTurns(state.get(OrientedMachineBlock.FACING),
                    required);
        }
        return 0;
    }

    private double measuredRadius(List<BlockPos> path) {
        double sum = 0.0;
        int count = 0;
        for (BlockPos pos : path) {
            double dx = pos.getX() - site.originX;
            double dz = pos.getZ() - site.originZ;
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r > 0.5) {
                sum += r;
                count++;
            }
        }
        return count == 0 ? site.size : sum / count;
    }

    private int computeStructureHash() {
        int hash = 7;
        for (Component component : components) {
            int kindHash = component.kind == null ? 0 : component.kind.ordinal() + 1;
            hash = hash * 31 + kindHash;
            hash = hash * 31 + component.rotationError;
            hash = hash * 31 + (component.present ? 1 : 0);
        }
        return hash;
    }

    /** The antiparticle of the counter-circulating bunch, or the particle itself. */
    private static ParticleSpecies collisionPartner(ParticleSpecies species) {
        ParticleSpecies antiparticle = species.antiparticle();
        return antiparticle == null ? species : antiparticle;
    }

    private static double clamp(double value, double limit) {
        if (value > limit) {
            return limit;
        }
        return Math.max(value, -limit);
    }

    private static int poisson(double mean) {
        if (mean <= 0.0) {
            return 0;
        }
        if (mean > 60.0) {
            return (int) Math.round(mean);
        }
        double limit = Math.exp(-mean);
        double product = RNG.nextDouble();
        int count = 0;
        while (product > limit && count < 200) {
            count++;
            product *= RNG.nextDouble();
        }
        return count;
    }

    private String describePosition(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    public void log(String message) {
        log.add(0, message);
        while (log.size() > 12) {
            log.remove(log.size() - 1);
        }
    }

    /** Speed of the beam as a fraction of c, for the HUD. */
    public double beamBeta() {
        return beam.intensity > 1.0 ? beam.beta() : 0.0;
    }

    public long ticks() {
        return ticks;
    }
}
