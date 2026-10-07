package com.particlephysics.client.screen;

import com.particlephysics.client.ClientState;
import com.particlephysics.net.ModNetworking;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Common look and helpers of the mod's panels: a translucent panel with a border, buttons that
 * send an action to the server, and the small physics readout rows.
 */
public abstract class ModScreen extends Screen {
    protected static final int PANEL = 0xC0101018;
    protected static final int BORDER = 0xFF3A7BD5;
    protected static final int TEXT = 0xE0E0E0;
    protected static final int GOOD = 0x80FF80;
    protected static final int WARN = 0xFFD166;
    protected static final int BAD = 0xFF6060;
    protected static final int DIM = 0x909090;

    protected ModScreen(Text title) {
        super(title);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    protected void panel(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, PANEL);
        context.drawBorder(x, y, width, height, BORDER);
    }

    protected ButtonWidget button(String label, int x, int y, int width, int height,
                                  Runnable action) {
        ButtonWidget widget = ButtonWidget.builder(Text.literal(label), ignored -> action.run())
                .dimensions(x, y, width, height)
                .build();
        addDrawableChild(widget);
        return widget;
    }

    protected void text(DrawContext context, String line, int x, int y, int colour) {
        context.drawTextWithShadow(textRenderer, Text.literal(line), x, y, colour);
    }

    protected void row(DrawContext context, String label, String value, int x, int y, int width,
                       int colour) {
        text(context, label, x, y, TEXT);
        int valueWidth = textRenderer.getWidth(value);
        context.drawTextWithShadow(textRenderer, Text.literal(value), x + width - valueWidth, y,
                colour);
    }

    protected void progress(DrawContext context, int x, int y, int width, int height, double value,
                            int colour) {
        context.fill(x, y, x + width, y + height, 0xFF202020);
        int filled = (int) (width * Math.max(0.0, Math.min(1.0, value)));
        if (filled > 0) {
            context.fill(x, y, x + filled, y + height, colour);
        }
        context.drawBorder(x, y, width, height, 0xFF505050);
    }

    /** Sends an action to one of the server side handlers. */
    protected void send(String route, String act, String key, double value) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("action", route);
        if (act != null) {
            nbt.putString("act", act);
        }
        if (key != null) {
            nbt.putDouble("value", value);
        }
        NbtCompound contextNbt = context();
        if (contextNbt != null) {
            for (String k : contextNbt.getKeys()) {
                nbt.put(k, contextNbt.get(k));
            }
        }
        ModNetworking.sendAction(nbt);
    }

    /** Extra fields (the block position) added to every action of this screen. */
    protected NbtCompound context() {
        return null;
    }

    protected static Text header(String text) {
        return Text.literal(text).formatted(Formatting.AQUA);
    }

    /** The payload this screen displays, or null when nothing has arrived yet. */
    protected NbtCompound payload() {
        return ClientState.machine != null ? ClientState.machine : ClientState.computer;
    }
}
