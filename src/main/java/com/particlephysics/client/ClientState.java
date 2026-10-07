package com.particlephysics.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * Client side mirror of everything the server sends.
 *
 * <p>The client never simulates physics: it only displays the numbers the server computed and sends
 * back button presses. Every screen and overlay reads from here.
 */
public final class ClientState {
    public static NbtCompound blueprint;
    public static NbtCompound computer;
    public static NbtCompound machine;
    public static NbtCompound detector;
    public static NbtCompound journal;
    public static final List<String> messages = new ArrayList<>();
    public static long lastUpdateMillis;

    /** Name and distance of the closest block the blueprint still wants, computed while rendering. */
    public static String nearestGhostName = "";
    public static double nearestGhostDistance = -1.0;

    private ClientState() {
    }

    public static void handle(NbtCompound nbt) {
        lastUpdateMillis = System.currentTimeMillis();
        String type = nbt.getString("type");
        switch (type) {
            case "blueprint" -> blueprint = nbt;
            case "computer" -> computer = nbt;
            case "machine" -> machine = nbt;
            case "detector" -> detector = nbt;
            case "journal" -> journal = nbt;
            case "message" -> {
                messages.add(nbt.getString("text"));
                while (messages.size() > 6) {
                    messages.remove(0);
                }
            }
            default -> {
                // unknown payload: ignore
            }
        }
    }

    public static boolean hasBlueprint() {
        return blueprint != null;
    }

    public static double get(NbtCompound nbt, String key) {
        return nbt == null ? 0.0 : nbt.getDouble(key);
    }

    public static List<NbtCompound> compounds(NbtCompound parent, String key) {
        List<NbtCompound> list = new ArrayList<>();
        if (parent == null) {
            return list;
        }
        NbtList nbtList = parent.getList(key, NbtCompound.COMPOUND_TYPE);
        for (int i = 0; i < nbtList.size(); i++) {
            list.add(nbtList.getCompound(i));
        }
        return list;
    }

    /** Reads a list of strings (used for the block each ghost wants). */
    public static List<String> strings(NbtCompound parent, String key) {
        List<String> list = new ArrayList<>();
        if (parent == null) {
            return list;
        }
        NbtList nbtList = parent.getList(key, NbtElement.STRING_TYPE);
        for (int i = 0; i < nbtList.size(); i++) {
            list.add(nbtList.getString(i));
        }
        return list;
    }

    public static double[] doubles(NbtCompound parent, String key) {
        if (parent == null || !parent.contains(key)) {
            return new double[0];
        }
        long[] bits = parent.getLongArray(key);
        double[] out = new double[bits.length];
        for (int i = 0; i < bits.length; i++) {
            out[i] = Double.longBitsToDouble(bits[i]);
        }
        return out;
    }
}
