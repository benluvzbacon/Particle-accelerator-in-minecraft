package com.particlephysics.accelerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.particlephysics.net.ModNetworking;
import com.particlephysics.world.ModState;
import com.particlephysics.world.SiteState;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Server side of the construction guide.
 *
 * <p>The blueprint is not a static image: the server generates the real design for the requested
 * size and shape, validates it against the blocks that are actually placed, and sends the client the
 * positions that still have to be built - which is what makes the ghost preview and the "Missing:
 * Dipole Magnet at Beamline Section 14" messages truthful.
 */
public final class BlueprintActions {
    private static final Map<UUID, Ui> STATES = new HashMap<>();

    private BlueprintActions() {
    }

    /** Per player view state of the guide. */
    private static final class Ui {
        AcceleratorDesign.Kind kind = AcceleratorDesign.Kind.RING;
        int size = AcceleratorDesign.Kind.RING.defaultSize;
        int cells = 16;
        int mode;              // 0 overview, 1 construction, 2 materials, 3 layers, 4 systems, 5 diagnostics
        int layer;
        int cycle;
        /** Preview the whole design, including shielding and the service ring. */
        boolean showAll;
        UUID siteId;
    }

    private static Ui ui(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), id -> new Ui());
    }

    // ------------------------------------------------------------------------------------------

    public static void sendBlueprintScreen(ServerPlayerEntity player) {
        Ui ui = ui(player);
        AcceleratorNetwork network = ui.siteId == null ? null
                : networkById(player, ui.siteId);
        SiteState.Site site = network != null ? network.site : null;
        AcceleratorDesign design = site != null ? site.design()
                : new AcceleratorDesign(ui.kind, provisionalOrigin(player), ui.size, ui.cells,
                        player.getHorizontalFacing());

        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", "blueprint");
        nbt.putBoolean("deployed", site != null);
        nbt.putString("kind", design.kind().name());
        nbt.putInt("size", ui.size);
        nbt.putInt("cells", ui.cells);
        nbt.putInt("mode", ui.mode);
        nbt.putInt("layer", ui.layer);
        nbt.putInt("cycle", ui.cycle);
        nbt.putInt("sections", design.sectionCount());
        nbt.putDouble("circumference", design.circumference());
        nbt.putInt("components", design.componentCount());
        nbt.putInt("slots", design.slotCount());
        nbt.putString("rotation", design.rotation().asString());
        BlockPos origin = design.origin();
        nbt.putInt("x", origin.getX());
        nbt.putInt("y", origin.getY());
        nbt.putInt("z", origin.getZ());

        // --- materials ------------------------------------------------------------------------
        NbtList materials = new NbtList();
        Map<String, Integer> placed = network == null ? new HashMap<>() : network.placedMaterials;
        for (Map.Entry<String, Integer> entry : design.materials().entrySet()) {
            NbtCompound material = new NbtCompound();
            material.putString("key", entry.getKey());
            material.putString("name", displayName(entry.getKey()));
            material.putInt("required", entry.getValue());
            material.putInt("placed", placed.getOrDefault(entry.getKey(), 0));
            materials.add(material);
        }
        nbt.put("materials", materials);

        // --- validation -----------------------------------------------------------------------
        NbtList issues = new NbtList();
        int ok = 0;
        int errors = 0;
        if (network != null) {
            for (AcceleratorNetwork.Issue issue : network.issues) {
                NbtCompound entry = new NbtCompound();
                entry.putString("text", issue.message());
                entry.putInt("x", issue.pos().getX());
                entry.putInt("y", issue.pos().getY());
                entry.putInt("z", issue.pos().getZ());
                entry.putBoolean("error", issue.error());
                issues.add(entry);
                if (issues.size() >= 128) {
                    break;
                }
            }
            for (AcceleratorNetwork.Component component : network.components) {
                if (component.present && component.correctKind && component.correctOrientation) {
                    ok++;
                } else {
                    errors++;
                }
            }
            NbtList log = new NbtList();
            for (String line : network.log) {
                NbtCompound entry = new NbtCompound();
                entry.putString("text", line);
                log.add(entry);
            }
            nbt.put("log", log);
            nbt.putString("failure", network.lastFailure);
            nbt.putDouble("energy", network.beam.energy);
            nbt.putDouble("vacuum", network.vacuumPressurePa);
            nbt.putDouble("field", network.machine.dipoleField);
            nbt.putDouble("dose", network.radiationPromptSvH);
            nbt.putBoolean("running", network.machine.running);
        }
        nbt.putInt("builtOk", ok);
        nbt.putInt("errors", errors);
        nbt.put("issues", issues);

        // --- ghost positions, with the block each one wants and how far along it is -----------
        ServerWorld world = (ServerWorld) player.getWorld();
        List<AcceleratorDesign.Slot> selected = selectSlots(design, network, ui);
        int[] px = new int[selected.size()];
        int[] py = new int[selected.size()];
        int[] pz = new int[selected.size()];
        int[] pk = new int[selected.size()];
        int[] pstate = new int[selected.size()];
        NbtList blocks = new NbtList();
        for (int i = 0; i < selected.size(); i++) {
            AcceleratorDesign.Slot slot = selected.get(i);
            BlockPos pos = design.worldPos(slot);
            px[i] = pos.getX();
            py[i] = pos.getY();
            pz[i] = pos.getZ();
            pk[i] = slot.kind == null ? -1 : slot.kind.ordinal();
            pstate[i] = stateOf(world, slot, pos, network);
            blocks.add(net.minecraft.nbt.NbtString.of(blockIdOf(slot)));
        }
        nbt.putIntArray("px", px);
        nbt.putIntArray("py", py);
        nbt.putIntArray("pz", pz);
        nbt.putIntArray("pk", pk);
        nbt.putIntArray("pstate", pstate);
        nbt.put("blocks", blocks);
        nbt.putBoolean("all", ui.showAll);

        ModNetworking.send(player, nbt);
    }

    /** The slots the current mode and cycle want to preview. */
    private static List<AcceleratorDesign.Slot> selectSlots(AcceleratorDesign design,
                                                            AcceleratorNetwork network, Ui ui) {
        List<AcceleratorDesign.Slot> source = new ArrayList<>();
        for (AcceleratorDesign.Slot slot : design.slots()) {
            if (ui.showAll || (!slot.isShielding() && !slot.isInfrastructure())) {
                source.add(slot);
            }
        }
        if (source.isEmpty()) {
            return source;
        }
        // Eight chunks through the design, so the player always sees an amount of ghosts that can
        // actually be built in one go.
        int perCycle = Math.max(8, source.size() / 8);
        int start = Math.max(0, ui.cycle) * perCycle;
        if (start >= source.size()) {
            ui.cycle = 0;
            start = 0;
        }
        List<AcceleratorDesign.Slot> out = new ArrayList<>(
                source.subList(start, Math.min(source.size(), start + perCycle)));
        if (design.kind() == AcceleratorDesign.Kind.LINAC && ui.mode == 3) {
            List<Integer> layers = design.layers();
            int layer = layers.get(Math.max(0, Math.min(layers.size() - 1, ui.layer)));
            out.removeIf(slot -> slot.offset.getY() != layer);
        }
        return out;
    }

    /** The block a slot wants, as a registry id the client can resolve. */
    private static String blockIdOf(AcceleratorDesign.Slot slot) {
        if (slot.kind != null) {
            net.minecraft.block.Block block = com.particlephysics.registry.ModBlocks.forKind(slot.kind);
            if (block != null) {
                return net.minecraft.registry.Registries.BLOCK.getId(block).toString();
            }
        }
        net.minecraft.block.Block block = shieldingBlock(slot.materialKey);
        return net.minecraft.registry.Registries.BLOCK.getId(block).toString();
    }

    private static net.minecraft.block.Block shieldingBlock(String materialKey) {
        return switch (materialKey == null ? "" : materialKey) {
            case "lead_shielding" -> com.particlephysics.registry.ModBlocks.LEAD_SHIELDING;
            case "lead_block" -> com.particlephysics.registry.ModBlocks.LEAD_BLOCK;
            case "water_shielding" -> com.particlephysics.registry.ModBlocks.WATER_SHIELDING;
            case "borated_polyethylene" ->
                    com.particlephysics.registry.ModBlocks.BORATED_POLYETHYLENE;
            case "machine_casing" -> com.particlephysics.registry.ModBlocks.MACHINE_CASING;
            case "cryostat_wall" -> com.particlephysics.registry.ModBlocks.CRYOSTAT_WALL;
            default -> com.particlephysics.registry.ModBlocks.CONCRETE_SHIELDING;
        };
    }

    /** 0 = missing, 1 = wrong block or orientation, 2 = built correctly. */
    private static int stateOf(ServerWorld world, AcceleratorDesign.Slot slot, BlockPos pos,
                               AcceleratorNetwork network) {
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        if (slot.kind != null && !slot.isInfrastructure()) {
            if (network == null) {
                return 0;
            }
            for (AcceleratorNetwork.Component component : network.components) {
                if (!component.pos.equals(pos)) {
                    continue;
                }
                if (!component.present) {
                    return 0;
                }
                return component.correctKind && component.correctOrientation ? 2 : 1;
            }
            return 0;
        }
        if (slot.kind != null) {
            // service ring component
            if (state.getBlock() instanceof com.particlephysics.block.MachineBlock machine) {
                return machine.kind() == slot.kind ? 2 : 1;
            }
            return 0;
        }
        net.minecraft.block.Block expected = shieldingBlock(slot.materialKey);
        if (state.isAir()) {
            return 0;
        }
        return state.isOf(expected) ? 2 : 1;
    }

    private static String displayName(String key) {
        AcceleratorDesign.Kind ignored = AcceleratorDesign.Kind.RING;
        MachineKind kind = MachineKind.byId(key);
        if (kind != null) {
            return kind.displayName();
        }
        return switch (key) {
            case "lead_block" -> "Block of Lead";
            case "lead_shielding" -> "Lead Shielding";
            case "concrete_shielding" -> "Concrete Shielding";
            case "water_shielding" -> "Water Shielding";
            case "borated_polyethylene" -> "Borated Polyethylene";
            case "machine_casing" -> "Machine Casing";
            case "cryostat_wall" -> "Cryostat Wall";
            default -> key;
        };
    }

    // ------------------------------------------------------------------------------------------

    public static void handle(ServerPlayerEntity player, NbtCompound data) {
        Ui ui = ui(player);
        String action = data.getString("action");
        switch (action) {
            case "mode" -> ui.mode = (int) data.getInt("value");
            case "cycle" -> ui.cycle = Math.max(0, data.getInt("value"));
            case "layer" -> ui.layer = Math.max(0, data.getInt("value"));
            case "all" -> {
                ui.showAll = !ui.showAll;
                ui.cycle = 0;
            }
            case "kind" -> {
                AcceleratorDesign.Kind kind = "LINAC".equals(data.getString("value"))
                        ? AcceleratorDesign.Kind.LINAC : AcceleratorDesign.Kind.RING;
                ui.kind = kind;
                ui.size = kind.defaultSize;
                ui.cells = kind == AcceleratorDesign.Kind.RING ? 16 : 8;
                ui.siteId = null;
            }
            case "size" -> ui.size = (int) Math.max(4, Math.min(120, data.getInt("value")));
            case "cells" -> ui.cells = (int) Math.max(4, Math.min(64, data.getInt("value")));
            case "deploy" -> deploy(player, ui, data);
            case "forget" -> {
                SiteState.Site site = ui.siteId == null ? null : ModState.sites().byId(ui.siteId);
                if (site != null) {
                    AcceleratorController.removeSite(site);
                    player.sendMessage(Text.literal("Blueprint removed").formatted(Formatting.GRAY),
                            true);
                }
                ui.siteId = null;
            }
            case "select" -> {
                if (data.contains("site")) {
                    try {
                        ui.siteId = UUID.fromString(data.getString("site"));
                    } catch (IllegalArgumentException e) {
                        ui.siteId = null;
                    }
                }
            }
            case "teleport" -> {
                SiteState.Site site = ui.siteId == null ? null : ModState.sites().byId(ui.siteId);
                if (site != null && site.world.equals(
                        player.getWorld().getRegistryKey().getValue().toString())) {
                    BlockPos target = site.origin().up(2);
                    player.teleport((ServerWorld) player.getWorld(), target.getX() + 0.5,
                            target.getY(), target.getZ() + 0.5, player.getYaw(), player.getPitch());
                }
            }
            default -> {
                // nothing to do: unknown action
            }
        }
        sendBlueprintScreen(player);
    }

    private static void deploy(ServerPlayerEntity player, Ui ui, NbtCompound data) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        AcceleratorDesign.Kind kind = ui.kind;
        int size = (int) Math.max(kind.minSize, Math.min(kind.maxSize, ui.size));
        int cells = Math.max(4, ui.cells - (ui.cells % 4));
        BlockPos origin = provisionalOrigin(player);
        Direction rotation = player.getHorizontalFacing();
        SiteState.Site site = AcceleratorController.createSite(world, kind, origin, size, cells,
                rotation);
        ui.siteId = site.id;
        site.targetEnergyMeV = 100.0;
        site.dipoleField = 0.5;
        site.rfVoltageMV = 0.5;
        ModState.sites().markDirty();
        player.sendMessage(Text.literal("Blueprint deployed: " + kind.displayName()
                + ", radius " + size + " blocks. Follow the ghost blocks.").formatted(
                        Formatting.AQUA), false);
    }

    /** Where the machine is centred before it exists: where the player is standing. */
    private static BlockPos provisionalOrigin(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }

    private static AcceleratorNetwork networkById(ServerPlayerEntity player, UUID id) {
        SiteState.Site site = ModState.sites().byId(id);
        return site == null ? null : AcceleratorController.networkOf(site);
    }

    /** The blueprint help text shown in the journal. */
    public static String describeModes() {
        return String.format(Locale.ROOT,
                "Overview / Construction / Materials / Layer / Systems / Diagnostics");
    }
}
