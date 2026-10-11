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
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BufferRenderer;
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
 * <p>Two rules are respected here, and they matter:
 *
 * <ol>
 *   <li><b>Own buffers only.</b> Every quad goes into a {@link BufferBuilder} this class creates and
 *       draws in the same callback. The shared vertex consumer pool is never touched, because a
 *       buffer that the world renderer has already flushed cannot be appended to ("Not building!").
 *   <li><b>Real models.</b> A ghost is the actual model of the block that has to be placed there,
 *       drawn translucent with the block atlas, so the player sees which block is wanted instead of
 *       guessing from a colour.
 * </ol>
 *
 * <p>A wireframe coloured by subsystem surrounds each ghost; it turns amber when the block that is
 * there is the wrong one or is rotated the wrong way. Anything already correct is not drawn at all.
 * If rendering ever throws, the error is logged once and the frame is skipped rather than taken
 * down.
 */
public final class BeamRenderer {
    /** Above this many ghosts per frame the preview is cut off, to keep the frame time sane. */
    private static final int MAX_GHOSTS = 320;
    private static boolean warned;

    private BeamRenderer() {
    }

    public static void render(WorldRenderContext context) {
        try {
            renderInternal(context);
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                ParticleAcceleratorMod.LOGGER.error(
                        "Particle Accelerator: world rendering failed and was disabled: {}", t.toString());
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

        if (!config.renderGhosts) {
            ClientState.nearestGhostName = "";
            ClientState.nearestGhostDistance = -1.0;
            if (!config.renderBeam) {
                return;
            }
        }

        Tessellator tessellator = Tessellator.getInstance();

        // --- ghost blocks: the real block models, translucent ---------------------------------
        if (config.renderGhosts && blueprint != null) {
            drawGhosts(client, tessellator, matrices, camera, blueprint, config);
        } else if (blueprint == null) {
            ClientState.nearestGhostName = "";
            ClientState.nearestGhostDistance = -1.0;
        }

        // --- wireframes and the beam: one line buffer -----------------------------------------
        BufferBuilder lines = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES,
                VertexFormats.POSITION_COLOR);
        int lineCount = 0;
        if (config.renderGhosts && blueprint != null) {
            lineCount += addGhostOutlines(matrices, lines, camera, blueprint, config);
        }
        if (config.renderBeam && status != null && status.getBoolean("running")) {
            lineCount += addBeam(matrices, lines, camera, status);
        }
        BuiltBuffer built = lines.endNullable();
        if (built != null && lineCount > 0) {
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

    // --------------------------------------------------------------------------------------------
    // Ghost blocks
    // --------------------------------------------------------------------------------------------

    private static void drawGhosts(MinecraftClient client, Tessellator tessellator,
                                   MatrixStack matrices, Vec3d camera, NbtCompound blueprint,
                                   ModConfig config) {
        int[] px = blueprint.getIntArray("px");
        int[] py = blueprint.getIntArray("py");
        int[] pz = blueprint.getIntArray("pz");
        int[] state = blueprint.getIntArray("pstate");
        List<String> blocks = ClientState.strings(blueprint, "blocks");
        if (px.length == 0 || blocks.isEmpty()) {
            return;
        }
        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        Random random = Random.create(1234L);
        double rangeSquared = config.ghostRenderDistance * config.ghostRenderDistance;
        int drawn = 0;
        for (int i = 0; i < px.length && i < blocks.size(); i++) {
            int stateHere = i < state.length ? state[i] : 0;
            if (stateHere >= 2) {
                // already built correctly: there is nothing left to show
                continue;
            }
            double vx = px[i] + 0.5 - camera.x;
            double vy = py[i] + 0.5 - camera.y;
            double vz = pz[i] + 0.5 - camera.z;
            if (vx * vx + vy * vy + vz * vz > rangeSquared) {
                continue;
            }
            if (drawn >= MAX_GHOSTS) {
                break;
            }
            Block block = resolve(blocks.get(i));
            if (block == null) {
                continue;
            }
            // dim, cold hologram; brighter for a block that is missing than for a wrong one
            float shade = stateHere == 1 ? 0.42f : 0.62f;
            addModel(buffer, matrices, random, block, vx - 0.5, vy - 0.5, vz - 0.5, shade,
                    stateHere == 1 ? 0.30f : 0.55f);
            drawn++;
        }
        BuiltBuffer built = buffer.endNullable();
        if (built == null || drawn == 0) {
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

    /** Appends one block model, in view space, to the quad buffer. */
    private static void addModel(BufferBuilder buffer, MatrixStack matrices, Random random,
                                 Block block, double x, double y, double z, float shade,
                                 float alpha) {
        BlockState state = block.getDefaultState();
        BakedModel model = MinecraftClient.getInstance().getBlockRenderManager().getModel(state);
        matrices.push();
        matrices.translate(x, y, z);
        MatrixStack.Entry entry = matrices.peek();
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(state, direction, random)) {
                buffer.quad(entry, quad, shade, shade * 1.05f, 1.0f, alpha,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            }
        }
        for (var quad : model.getQuads(state, null, random)) {
            buffer.quad(entry, quad, shade, shade * 1.05f, 1.0f, alpha,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();
    }

    /** Appends the subsystem-coloured outlines, and returns how many were added. */
    private static int addGhostOutlines(MatrixStack matrices, VertexConsumer lines, Vec3d camera,
                                        NbtCompound blueprint, ModConfig config) {
        int[] px = blueprint.getIntArray("px");
        int[] py = blueprint.getIntArray("py");
        int[] pz = blueprint.getIntArray("pz");
        int[] pk = blueprint.getIntArray("pk");
        int[] state = blueprint.getIntArray("pstate");
        int count = 0;
        double rangeSquared = config.ghostRenderDistance * config.ghostRenderDistance;
        String nearestName = "";
        double nearest = -1.0;
        Vec3d player = MinecraftClient.getInstance().player.getPos();
        List<String> blocks = ClientState.strings(blueprint, "blocks");
        for (int i = 0; i < px.length; i++) {
            int stateHere = i < state.length ? state[i] : 0;
            if (stateHere >= 2) {
                continue;
            }
            double vx = px[i] + 0.5 - camera.x;
            double vy = py[i] + 0.5 - camera.y;
            double vz = pz[i] + 0.5 - camera.z;
            if (vx * vx + vy * vy + vz * vz > rangeSquared) {
                continue;
            }
            float r;
            float g;
            float b;
            if (stateHere == 1) {
                r = 1.0f;
                g = 0.65f;
                b = 0.15f;
            } else {
                int colour = pk.length > i && pk[i] >= 0
                        ? MachineKind.values()[Math.min(pk[i], MachineKind.values().length - 1)]
                                .system().colour()
                        : 0xBFBFB8;
                r = ((colour >> 16) & 0xFF) / 255.0f;
                g = ((colour >> 8) & 0xFF) / 255.0f;
                b = (colour & 0xFF) / 255.0f;
            }
            WorldRenderer.drawBox(matrices, lines, vx - 0.5, vy - 0.5, vz - 0.5, vx + 0.5, vy + 0.5,
                    vz + 0.5, r, g, b, stateHere == 1 ? 0.95f : 0.6f);
            count++;

            if (i < blocks.size()) {
                double dx = px[i] + 0.5 - player.x;
                double dy = py[i] + 0.5 - player.y;
                double dz = pz[i] + 0.5 - player.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (nearest < 0 || distance < nearest) {
                    nearest = distance;
                    Block block = resolve(blocks.get(i));
                    nearestName = block == null ? "" : block.getName().getString();
                }
            }
        }
        ClientState.nearestGhostName = nearestName;
        ClientState.nearestGhostDistance = nearest;
        return count;
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

    private static int addBeam(MatrixStack matrices, VertexConsumer lines, Vec3d camera,
                               NbtCompound status) {
        double energyMeV = status.getDouble("energy");
        Vec3d origin = new Vec3d(status.getInt("x") + 0.5, status.getInt("y") + 0.5,
                status.getInt("z") + 0.5);
        int radius = Math.max(4, status.getInt("size"));
        boolean ring = status.getBoolean("ring");
        List<Vec3d> path = ring ? circle(origin, radius) : line(origin, radius);
        if (path.size() < 2) {
            return 0;
        }
        // colour: red at low energy, blue-white at high energy (the beam gets "hotter")
        double t = Math.min(1.0, Math.log10(Math.max(1.0, energyMeV)) / 8.0);
        float r = (float) (1.0 - 0.7 * t);
        float g = (float) (0.4 + 0.5 * t);
        float b = (float) (0.9 - 0.2 * t);
        float pulse = (float) (0.45 + 0.35 * Math.sin(System.currentTimeMillis() / 120.0));
        for (int i = 0; i < path.size(); i++) {
            Vec3d from = path.get(i).subtract(camera);
            Vec3d to = path.get((i + 1) % path.size()).subtract(camera);
            WorldRenderer.drawBox(matrices, lines, from.x - 0.06, from.y - 0.06, from.z - 0.06,
                    to.x + 0.06, to.y + 0.06, to.z + 0.06, r, g, b, pulse);
        }
        return path.size();
    }

    private static List<Vec3d> circle(Vec3d origin, int radius) {
        List<Vec3d> path = new ArrayList<>();
        int steps = Math.max(32, radius * 8);
        for (int i = 0; i < steps; i++) {
            double angle = 2.0 * Math.PI * i / steps;
            path.add(new Vec3d(origin.x + radius * Math.cos(angle), origin.y + 0.5,
                    origin.z + radius * Math.sin(angle)));
        }
        return path;
    }

    private static List<Vec3d> line(Vec3d origin, int length) {
        List<Vec3d> path = new ArrayList<>();
        for (int i = -length; i <= length; i += 2) {
            path.add(new Vec3d(origin.x + i, origin.y + 0.5, origin.z));
        }
        return path;
    }
}
