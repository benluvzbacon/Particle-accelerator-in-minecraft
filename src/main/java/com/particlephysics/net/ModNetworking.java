package com.particlephysics.net;

import com.particlephysics.ParticleAcceleratorMod;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * All client/server traffic of the mod.
 *
 * <p>Both directions use a single payload that carries an NBT compound, because every message is a
 * small command or a snapshot of a machine. Keeping one codec keeps the protocol compact and makes
 * it easy to add a new screen without touching the networking layer.
 */
public final class ModNetworking {
    public static final Identifier SYNC_ID = Identifier.of(ParticleAcceleratorMod.MOD_ID, "sync");
    public static final Identifier ACTION_ID = Identifier.of(ParticleAcceleratorMod.MOD_ID, "action");

    private ModNetworking() {
    }

    /** Server to client: screen data, machine state, meter readings, messages. */
    public record Sync(NbtCompound data) implements CustomPayload {
        public static final CustomPayload.Id<Sync> ID = new CustomPayload.Id<>(SYNC_ID);
        public static final PacketCodec<RegistryByteBuf, Sync> CODEC = PacketCodec.of(
                (value, buffer) -> buffer.writeNbt(value.data()),
                buffer -> new Sync(readNbt(buffer)));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client to server: a button press, a slider value, a screen request. */
    public record Action(NbtCompound data) implements CustomPayload {
        public static final CustomPayload.Id<Action> ID = new CustomPayload.Id<>(ACTION_ID);
        public static final PacketCodec<RegistryByteBuf, Action> CODEC = PacketCodec.of(
                (value, buffer) -> buffer.writeNbt(value.data()),
                buffer -> new Action(readNbt(buffer)));

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static NbtCompound readNbt(RegistryByteBuf buffer) {
        NbtCompound nbt = buffer.readNbt();
        return nbt == null ? new NbtCompound() : nbt;
    }

    /** Registers the payload types; must run on both sides during initialisation. */
    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(Sync.ID, Sync.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID, Action.CODEC);
    }

    /** Registers the server side receiver. */
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(Action.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            NbtCompound data = payload.data();
            context.server().execute(() -> ServerActions.handle(player, data));
        });
    }

    // ------------------------------------------------------------------------------------------
    // Server -> client
    // ------------------------------------------------------------------------------------------

    public static void send(ServerPlayerEntity player, NbtCompound data) {
        ServerPlayNetworking.send(player, new Sync(data));
    }

    public static void sendMessage(ServerPlayerEntity player, String text, boolean overlay) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("type", "message");
        nbt.putString("text", text);
        nbt.putBoolean("overlay", overlay);
        send(player, nbt);
    }

    // ------------------------------------------------------------------------------------------
    // Client -> server
    // ------------------------------------------------------------------------------------------

    /**
     * Asks the server to open one of the mod's screens ("blueprint", "quests", "machine",
     * "computer", "detector") or to report a meter reading ("geiger"). Only ever called from the
     * client thread.
     */
    public static void requestScreen(PlayerEntity player, String screen) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", "open");
        nbt.putString("screen", screen);
        ClientPlayNetworking.send(new Action(nbt));
    }

    /** Sends a button press or a configuration change. */
    public static void sendAction(NbtCompound nbt) {
        ClientPlayNetworking.send(new Action(nbt));
    }
}
