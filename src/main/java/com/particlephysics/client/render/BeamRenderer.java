package com.particlephysics.client.render;

import java.util.ArrayList;
import java.util.List;

import com.particlephysics.accelerator.AcceleratorDesign;
import com.particlephysics.accelerator.MachineKind;
import com.particlephysics.client.ClientState;
import com.particlephysics.config.ModConfig;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Draws the machine in the world: ghost blocks for everything the blueprint still wants, and the
 * beam path (whose colour follows the beam energy and whose animation speed follows the beam
 * velocity) once a machine is running.
 *
 * <p>The ghosts are taken straight from the server's validation payload, so a green outline is a
 * block that is correct, an amber one is wrong orientation and a red one is missing.
 */
public final class BeamRenderer {
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

        // --- beam path ------------------------------------------------------------------------
        NbtCompound status = ClientState.computer != null ? ClientState.computer
                : ClientState.blueprint;
        if (config.renderBeam && status != null && status.getBoolean("running")) {
            drawBeam(context, matrices, consumers, camera, status);
        }

        // --- ghost blocks ---------------------------------------------------------------------
        NbtCompound blueprint = ClientState.blueprint;
        if (!config.renderGhosts || blueprint == null) {
            return;
        }
        int[] px = blueprint.getIntArray("px");
        int[] py = blueprint.getIntArray("py");
        int[] pz = blueprint.getIntArray("pz");
        int[] pk = blueprint.getIntArray("pk");
        int limit = Math.min(px.length, config.ghostRenderDistance * 2);
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());
        double distanceSquared = config.ghostRenderDistance * config.ghostRenderDistance;
        for (int i = 0; i < limit; i++) {
            double dx = px[i] + 0.5 - camera.x;
            double dy = py[i] + 0.5 - camera.y;
            double dz = pz[i] + 0.5 - camera.z;
            if (dx * dx + dy * dy + dz * dz > distanceSquared) {
                continue;
            }
            float r = 0.2f;
            float g = 1.0f;
            float b = 0.4f;
            if (pk[i] < 0) {
                r = 0.7f;
                g = 0.7f;
                b = 0.8f;
            } else {
                MachineKind kind = MachineKind.values()[Math.min(pk[i],
                        MachineKind.values().length - 1)];
                int colour = kind.system().colour();
                r = ((colour >> 16) & 0xFF) / 255.0f;
                g = ((colour >> 8) & 0xFF) / 255.0f;
                b = (colour & 0xFF) / 255.0f;
            }
            Box box = new Box(px[i] - camera.x, py[i] - camera.y, pz[i] - camera.z,
                    px[i] + 1.0 - camera.x, py[i] + 1.0 - camera.y, pz[i] + 1.0 - camera.z);
            WorldRenderer.drawBox(matrices, lines, box, r, g, b, 0.35f);
        }
    }

    private static void drawBeam(WorldRenderContext context, MatrixStack matrices,
                                 VertexConsumerProvider consumers, Vec3d camera,
                                 NbtCompound status) {
        double energyMeV = status.getDouble("energy");
        double charge = 1.0;
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
