package com.particlephysics.world;

import java.nio.file.Files;
import java.nio.file.Path;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/**
 * A single NBT file inside the world folder.
 *
 * <p>All of the mod's long lived data (discovered elements, research progress, quest state,
 * accelerator configuration, radiation inventory and blueprint construction sites) is stored in
 * {@code <world>/particleaccelerator/data.nbt}, which survives world saves, server restarts and
 * dimension changes. Writing happens on a timer, on world shutdown and whenever the player asks
 * for it explicitly.
 */
public abstract class PersistentStore {
    private final String fileName;
    private boolean dirty;
    private long lastSaveMillis;

    protected PersistentStore(String fileName) {
        this.fileName = fileName;
    }

    /** Serialises this store into NBT. */
    public abstract NbtCompound toNbt();

    /** Restores this store from NBT. Must tolerate missing keys. */
    public abstract void fromNbt(NbtCompound nbt);

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    private Path file(MinecraftServer server) {
        Path root = server.getSavePath(WorldSavePath.ROOT);
        return root.resolve("particleaccelerator").resolve(fileName);
    }

    public void load(MinecraftServer server) {
        try {
            Path path = file(server);
            if (!Files.exists(path)) {
                return;
            }
            NbtCompound nbt = NbtIo.read(path);
            if (nbt != null) {
                fromNbt(nbt);
            }
        } catch (Exception e) {
            com.particlephysics.ParticleAcceleratorMod.LOGGER.error(
                    "Failed to load {}: {}", fileName, e.toString());
        }
    }

    public void save(MinecraftServer server) {
        if (!dirty && lastSaveMillis != 0) {
            return;
        }
        try {
            Path path = file(server);
            Files.createDirectories(path.getParent());
            NbtIo.write(toNbt(), path);
            dirty = false;
            lastSaveMillis = System.currentTimeMillis();
        } catch (Exception e) {
            com.particlephysics.ParticleAcceleratorMod.LOGGER.error(
                    "Failed to save {}: {}", fileName, e.toString());
        }
    }

    /** Saves at most every {@code intervalMillis} milliseconds when there is something new. */
    public void saveThrottled(MinecraftServer server, long intervalMillis) {
        if (!dirty) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSaveMillis < intervalMillis) {
            return;
        }
        save(server);
    }
}
