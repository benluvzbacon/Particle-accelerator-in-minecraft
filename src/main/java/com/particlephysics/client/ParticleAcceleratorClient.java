package com.particlephysics.client;

import com.particlephysics.client.render.BeamRenderer;
import com.particlephysics.client.screen.BlueprintScreen;
import com.particlephysics.client.screen.ComputerScreen;
import com.particlephysics.client.screen.DetectorScreen;
import com.particlephysics.client.screen.JournalScreen;
import com.particlephysics.client.screen.MachineScreen;
import com.particlephysics.config.ModConfig;
import com.particlephysics.net.ModNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;

/**
 * Client entry point: it receives the state the server sends, opens the right screen when the
 * player asks for one, draws the head up display (beam, dose, warnings) and renders the blueprint
 * ghosts and the beam path in the world.
 */
public class ParticleAcceleratorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ModNetworking.Sync.ID, (payload, context) -> {
            ClientState.handle(payload.data());
            context.client().execute(() -> openIfRequested(payload.data()));
        });

        HudRenderCallback.EVENT.register(new ClientHud());
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> BeamRenderer.render(context));
    }

    private static void openIfRequested(net.minecraft.nbt.NbtCompound data) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        String type = data.getString("type");
        Screen current = client.currentScreen;
        Screen next = switch (type) {
            case "blueprint" -> new BlueprintScreen();
            case "computer" -> new ComputerScreen();
            case "detector" -> new DetectorScreen();
            case "journal" -> new JournalScreen();
            case "machine" -> new MachineScreen();
            default -> null;
        };
        if (next == null) {
            return;
        }
        if (current != null && current.getClass() == next.getClass()) {
            // the screen already shows this data, which ClientState has just updated
            return;
        }
        client.setScreen(next);
    }

    public static ModConfig config() {
        return ModConfig.get();
    }
}
