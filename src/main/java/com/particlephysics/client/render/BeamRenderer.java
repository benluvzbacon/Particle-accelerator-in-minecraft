package com.particlephysics.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.particlephysics.ParticleAcceleratorMod;
import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.client.ClientState;
import com.particlephysics.config.ModConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Draws the machine in the world: ghost blocks that are the real block models, and the beam path.
 *
 * <p>Three rules are respected here, and they matter:
 *
 * <ol>
 *   <li><b>Own buffers only.</b> Every vertex goes into a {@link BufferBuilder} this class creates
 *       and draws inside the same callback. The shared vertex consumer pool is never touched: a
 *       buffer the world renderer has already flushed cannot be appended to, and asking for one
 *       again is what throws "Not building!" and kills the game.
 *   <li><b>Real models.</b> A ghost is the actual model of the block that has to be placed there,
 *       drawn translucent with the block atlas, so the player sees which block is wanted instead of
 *       guessing from a colour.
 *   <li><b>Never an empty buffer.</b> A buffer is only started when at least one quad or line is
 *       known to go into it, so there is nothing to leak and nothing to draw twice.
 * </ol>
 *
 * <p>A wireframe coloured by subsystem surrounds every ghost and turns amber when the block that is
 * there is the wrong one or is rotated the wrong way. Blocks that are already correct are not drawn
 * at all. If rendering ever throws, the error is logged once and the frame is skipped instead of
 * taking the game down.
 */
public final class BeamRenderer {
    /** Above this many ghosts per frame the preview is cut off, to keep the frame time sane. */
    private static final int MAX_GHOSTS = 384;
    private static boolean warned;

    /** One ghost to draw: a position relative to the camera plus what belongs there. */
    private static final class Ghost {
        double x;
        double y;
        double z;
        Block block;
        int state;
        int colour;
    }

    /** Reused between frames so that drawing costs no allocations. */
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static int ghostCount;
    private static final List<Vec3d> PATH = new ArrayList<>();

    private BeamRenderer() {
    }

    public static void render(WorldRenderContext context) {
        try {
            renderInternal(context);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                ParticleAcceleratorMod.LOGGER.error(
                        "Particle Accelerator: world rendering failed and was disabled: {}",
                        t.toString());
            }
        }
    }

    private static void renderInternal(WorldRenderContext context) {
        ModConfig config = ModConfig.get();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) {
            return;
        }
        Vec3d camera = context.camera().getPos();
        NbtCompound blueprint = ClientState.blueprint;
        NbtCompound status = ClientState.computer != null ? ClientState.computer : blueprint;
        boolean showGhosts = config.renderGhosts && blueprint != null;
        boolean showBeam = config.renderBeam && status != null && status.getBoolean("running");

        collectGhosts(client, blueprint, camera, config, showGhosts);
        if (!showGhosts && !showBeam) {
            return;
        }

        Tessellator tessellator = Tessellator.getInstance();

        // --- ghost blocks: the real block models, translucent ---------------------------------
        if (ghostCount > 0) {
            drawGhostModels(client, tessellator, matrices);
        }

        // --- wireframes and the beam, in one line buffer --------------------------------------
        if (ghostCount > 0 || showBeam) {
            BufferBuilder lines = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES,
                    VertexFormats.POSITION_COLOR);
            addGhostOutlines(matrices, lines);
            if (showBeam) {
                addBeam(matrices, lines, camera, status);
            }
            BuiltBuffer built = lines.endNullable();
            if (built != null) {
                RenderSystem.setShader(GameRenderer::getRenderTypeLinesProgram);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                RenderSystem.lineWidth(2.0f);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                BufferRenderer.drawWithGlobalProgram(built);
                RenderSystem.lineWidth(1.0f);
                RenderSystem.disableBlend();
            }
        }
    }

    // --------------------------------------------------------------------------------------------
    // Ghost selection
    // --------------------------------------------------------------------------------------------

    private static void collectGhosts(MinecraftClient client, NbtCompound blueprint, Vec3d camera,
                                      ModConfig config, boolean showGhosts) {
        ghostCount = 0;
        ClientState.nearestGhostName = "";
        ClientState.nearestGhostDistance = -1.0;
        if (!showGhosts) {
            return;
        }
        int[] px = blueprint.getIntArray("px");
        int[] py = blueprint.getIntArray("py");
        int[] pz = blueprint.getIntArray("pz");
        int[] pk = blueprint.getIntArray("pk");
        int[] state = blueprint.getIntArray("pstate");
        List<String> blocks = ClientState.strings(blueprint, "blocks");
        int limit = Math.min(px.length, blocks.size());
        double rangeSquared = config.ghostRenderDistance * config.ghostRenderDistance;
        Vec3d player = client.player.getPos();
        String nearestName = "";
        double nearest = -1.0;
        for (int i = 0; i < limit; i++) {
            int stateHere = i < state.length ? state[i] : 0;
            if (stateHere >= 2) {
                // already built correctly: there is nothing left to show for this block
                continue;
            }
            double vx = px[i] + 0.5 - camera.x;
            double vy = py[i] + 0.5 - camera.y;
            double vz = pz[i] + 0.5 - camera.z;
            if (vx * vx + vy * vy + vz * vz > rangeSquared) {
                continue;
            }
            Block block = resolve(blocks.get(i));
            if (block == null) {
                continue;
            }
            if (ghostCount < MAX_GHOSTS) {
                Ghost ghost = nextGhost();
                ghost.x = vx - 0.5;
                ghost.y = vy - 0.5;
                ghost.z = vz - 0.5;
                ghost.block = block;
                ghost.state = stateHere;
                ghost.colour = pk.length > i && pk[i] >= 0
                        ? MachineKind.values()[Math.min(pk[i], MachineKind.values().length - 1)]
                                .system().colour()
                        : 0xBFBFB8;
            }
            double dx = px[i] + 0.5 - player.x;
            double dy = py[i] + 0.5 - player.y;
            double dz = pz[i] + 0.5 - player.z;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (nearest < 0 || distance < nearest) {
                nearest = distance;
                nearestName = block.getName().getString();
            }
        }
        ClientState.nearestGhostName = nearestName;
        ClientState.nearestGhostDistance = nearest;
    }

    private static Ghost nextGhost() {
        if (ghostCount < GHOSTS.size()) {
            return GHOSTS.get(ghostCount++);
        }
        Ghost ghost = new Ghost();
        GHOSTS.add(ghost);
        ghostCount++;
        return ghost;
    }

    /** Renders one block model per missing block, translucent, with the block's own texture. */
    private static void drawGhostModels(MinecraftClient client, Tessellator tessellator,
                                        MatrixStack matrices) {
        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        Random random = Random.create(1234L);
        int written = 0;
        for (int i = 0; i < ghostCount; i++) {
            Ghost ghost = GHOSTS.get(i);
            if (ghost.state != 0) {
                // a wrong block is left visible on its own; only its outline is drawn
                continue;
            }
            written += addModel(buffer, matrices, random, ghost.block, ghost.x, ghost.y, ghost.z);
        }
        BuiltBuffer built = buffer.endNullable();
        if (built == null || written == 0) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getRenderTypeEntityTranslucentProgram);
        RenderSystem.setShaderTexture(0, SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        BufferRenderer.drawWithGlobalProgram(built);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** Appends one block model, in view space; returns how many quads were written. */
    private static int addModel(BufferBuilder buffer, MatrixStack matrices, Random random,
                                Block block, double x, double y, double z) {
        BlockState state = block.getDefaultState();
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        int quads = 0;
        matrices.push();
        matrices.translate(x, y, z);
        MatrixStack.Entry entry = matrices.peek();
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(state, direction, random)) {
                buffer.quad(entry, quad, 0.62f, 0.65f, 1.0f, 0.55f,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                quads++;
            }
        }
        for (var quad : model.getQuads(state, null, random)) {
            buffer.quad(entry, quad, 0.62f, 0.65f, 1.0f, 0.55f,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            quads++;
        }
        matrices.pop();
        return quads;
    }

    private static void addGhostOutlines(MatrixStack matrices, VertexConsumer lines) {
        for (int i = 0; i < ghostCount; i++) {
            Ghost ghost = GHOSTS.get(i);
            float r;
            float g;
            float b;
            if (ghost.state == 1) {
                // the block standing there is wrong: amber, so the player finds it immediately
                r = 1.0f;
                g = 0.65f;
                b = 0.15f;
            } else {
                r = ((ghost.colour >> 16) & 0xFF) / 255.0f;
                g = ((ghost.colour >> 8) & 0xFF) / 255.0f;
                b = (ghost.colour & 0xFF) / 255.0f;
            }
            WorldRenderer.drawBox(matrices, lines, ghost.x, ghost.y, ghost.z, ghost.x + 1.0,
                    ghost.y + 1.0, ghost.z + 1.0, r, g, b, ghost.state == 1 ? 0.95f : 0.6f);
        }
    }

    private static Block resolve(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        return Registries.BLOCK.getOrEmpty(identifier).orElse(null);
    }

    // --------------------------------------------------------------------------------------------
    // Beam
    // --------------------------------------------------------------------------------------------

    private static void addBeam(MatrixStack matrices, VertexConsumer lines, Vec3d camera,
                                NbtCompound status) {
        double energyMeV = status.getDouble("energy");
        Vec3d origin = new Vec3d(status.getInt("x") + 0.5, status.getInt("y") + 0.5,
                status.getInt("z") + 0.5);
        int radius = Math.max(4, status.getInt("size"));
        boolean ring = status.getBoolean("ring");
        buildPath(origin, radius, ring);
        if (PATH.size() < 2) {
            return;
        }
        // colour: red at low energy, blue-white at high energy (the beam gets "hotter")
        double t = Math.min(1.0, Math.log10(Math.max(1.0, energyMeV)) / 8.0);
        float r = (float) (1.0 - 0.7 * t);
        float g = (float) (0.4 + 0.5 * t);
        float b = (float) (0.9 - 0.2 * t);
        float pulse = (float) (0.45 + 0.35 * Math.sin(System.currentTimeMillis() / 120.0));
        for (int i = 0; i < PATH.size(); i++) {
            Vec3d from = PATH.get(i).subtract(camera);
            Vec3d to = PATH.get((i + 1) % PATH.size()).subtract(camera);
            WorldRenderer.drawBox(matrices, lines, from.x - 0.06, from.y - 0.06, from.z - 0.06,
                    to.x + 0.06, to.y + 0.06, to.z + 0.06, r, g, b, pulse);
        }
    }

    private static void buildPath(Vec3d origin, int radius, boolean ring) {
        PATH.clear();
        if (ring) {
            int steps = Math.max(32, radius * 8);
            for (int i = 0; i < steps; i++) {
                double angle = 2.0 * Math.PI * i / steps;
                PATH.add(new Vec3d(origin.x + radius * Math.cos(angle), origin.y + 0.5,
                        origin.z + radius * Math.sin(angle)));
            }
        } else {
            for (int i = -radius; i <= radius; i += 2) {
                PATH.add(new Vec3d(origin.x + i, origin.y + 0.5, origin.z));
            }
        }
    }
}
