package com.particlephysics.client.render;

import java.util.ArrayList;
import java.util.List;

import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.client.ClientState;
import com.particlephysics.config.ModConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Draws the machine in the world.
 *
 * <p>Ghost blocks are the real block models, rendered as a translucent hologram: the texture of a
 * ghost is the texture of the block that has to be placed there, so the player can see *what* to
 * build without reading a list. The wireframe around a ghost is coloured by subsystem, and turns
 * amber when the block that is there is the wrong one or is rotated the wrong way. Everything shown
 * here comes from the server's validation of the machine that really exists in the world.
 */
public final class BeamRenderer {
    /** Above this many ghosts per frame the preview is cut off, to keep the frame time sane. */
    private static final int MAX_GHOSTS = 320;

    private BeamRenderer() {
    }

    public static void render(WorldRenderContext context) {
        ModConfig config = ModConfig.get();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();

        NbtCompound blueprint = ClientState.blueprint;
        NbtCompound status = ClientState.computer != null ? ClientState.computer : blueprint;

        // --- beam path ------------------------------------------------------------------------
        if (config.renderBeam && status != null && status.getBoolean("running")) {
            drawBeam(matrices, consumers, camera, status);
        }

        // --- ghost blocks ---------------------------------------------------------------------
        if (!config.renderGhosts || blueprint == null) {
            ClientState.nearestGhostName = "";
            ClientState.nearestGhostDistance = -1.0;
            return;
        }
        drawGhosts(client, matrices, consumers, camera, blueprint, config);
    }

    private static void drawGhosts(MinecraftClient client, MatrixStack matrices,
                                   VertexConsumerProvider consumers, Vec3d camera,
                                   NbtCompound blueprint, ModConfig config) {
        int[] px = blueprint.getIntArray("px");
        int[] py = blueprint.getIntArray("py");
        int[] pz = blueprint.getIntArray("pz");
        int[] pk = blueprint.getIntArray("pk");
        int[] state = blueprint.getIntArray("pstate");
        List<String> blocks = ClientState.strings(blueprint, "blocks");
        if (px.length == 0) {
            ClientState.nearestGhostName = "";
            ClientState.nearestGhostDistance = -1.0;
            return;
        }

        BlockRenderManager blockRenderer = client.getBlockRenderManager();
        VertexConsumer hologram = consumers.getBuffer(RenderLayer.getTranslucent());
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        Random random = Random.create(1234L);
        double rangeSquared = config.ghostRenderDistance * config.ghostRenderDistance;
        Vec3d player = client.player.getPos();

        String nearestName = "";
        double nearest = -1.0;
        int drawn = 0;
        for (int i = 0; i < px.length && i < blocks.size(); i++) {
            int stateHere = i < state.length ? state[i] : 0;
            if (stateHere >= 2) {
                // already built correctly: nothing to show
                continue;
            }
            double dx = px[i] + 0.5 - camera.x;
            double dy = py[i] + 0.5 - camera.y;
            double dz = pz[i] + 0.5 - camera.z;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > rangeSquared) {
                continue;
            }
            Block block = resolve(blocks.get(i));
            if (block == null) {
                continue;
            }
            if (drawn < MAX_GHOSTS) {
                drawHologram(blockRenderer, matrices, random, hologram, block, dx, dy, dz,
                        stateHere);
                drawn++;
            }
            // wireframe: subsystem colour when the block is missing, amber when it is wrong
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
            Box box = new Box(dx - 0.5, dy - 0.5, dz - 0.5, dx + 0.5, dy + 0.5, dz + 0.5);
            WorldRenderer.drawBox(matrices, lines, box, r, g, b, stateHere == 1 ? 0.9f : 0.55f);

            double playerDistance = Math.sqrt((px[i] + 0.5 - player.x) * (px[i] + 0.5 - player.x)
                    + (py[i] + 0.5 - player.y) * (py[i] + 0.5 - player.y)
                    + (pz[i] + 0.5 - player.z) * (pz[i] + 0.5 - player.z));
            if (nearest < 0 || playerDistance < nearest) {
                nearest = playerDistance;
                nearestName = block.getName().getString();
            }
        }
        ClientState.nearestGhostName = nearestName;
        ClientState.nearestGhostDistance = nearest;
    }

    /**
     * Renders one block model as a translucent hologram: the real geometry with the real texture,
     * lit at full brightness so it is readable at night and inside a tunnel.
     */
    private static void drawHologram(BlockRenderManager blockRenderer, MatrixStack matrices,
                                     Random random, VertexConsumer buffer, Block block,
                                     double dx, double dy, double dz, int state) {
        BlockState state_ = block.getDefaultState();
        BakedModel model = blockRenderer.getModel(state_);
        float alpha = state == 1 ? 0.28f : 0.55f;
        matrices.push();
        matrices.translate(dx - 0.5, dy - 0.5, dz - 0.5);
        MatrixStack.Entry entry = matrices.peek();
        for (Direction direction : Direction.values()) {
            for (var quad : model.getQuads(state_, direction, random)) {
                buffer.quad(entry, quad, 0.72f, 0.88f, 1.0f, alpha,
                        LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
            }
        }
        for (var quad : model.getQuads(state_, null, random)) {
            buffer.quad(entry, quad, 0.72f, 0.88f, 1.0f, alpha,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        }
        matrices.pop();
    }

    private static Block resolve(String id) {
        Identifier identifier = Identifier.tryParse(id);
        if (identifier == null) {
            return null;
        }
        return Registries.BLOCK.getOrEmpty(identifier).orElse(null);
    }

    private static void drawBeam(MatrixStack matrices, VertexConsumerProvider consumers,
                                 Vec3d camera, NbtCompound status) {
        double energyMeV = status.getDouble("energy");
        Vec3d origin = new Vec3d(status.getInt("x") + 0.5, status.getInt("y") + 0.5,
                status.getInt("z") + 0.5);
        int radius = Math.max(4, status.getInt("size"));
        boolean ring = status.getBoolean("ring");
        List<Vec3d> path = ring ? circle(origin, radius) : line(origin, radius);
        if (path.size() < 2) {
            return;
        }
        // colour: red at low energy, blue-white at high energy (the beam gets "hotter")
        double t = Math.min(1.0, Math.log10(Math.max(1.0, energyMeV)) / 8.0);
        float r = (float) (1.0 - 0.7 * t);
        float g = (float) (0.4 + 0.5 * t);
        float b = (float) (0.9 - 0.2 * t);
        float pulse = (float) (0.45 + 0.35 * Math.sin(System.currentTimeMillis() / 120.0));
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        for (int i = 0; i < path.size(); i++) {
            Vec3d a = path.get(i);
            Vec3d next = path.get((i + 1) % path.size());
            Vec3d from = a.subtract(camera);
            Vec3d to = next.subtract(camera);
            WorldRenderer.drawBox(matrices, lines,
                    new Box(from.x, from.y, from.z, to.x, to.y, to.z)
                            .expand(0.06, 0.06, 0.06),
                    r, g, b, pulse);
        }
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
