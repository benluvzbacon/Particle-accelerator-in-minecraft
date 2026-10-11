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
 * Common look and helpers of the mod's panels.
 *
 * <p>Two things every screen in this mod has to get right, both of them learned the hard way:
 *
 * <ul>
 *   <li><b>The background is drawn exactly once.</b> The vanilla screen background blurs whatever is
 *       already in the frame buffer, so drawing it twice would blur the text drawn in between.
 *       {@link #drawBackgroundOnce} does it once; {@link #renderBackground} is deliberately empty.
 *   <li><b>Content can be longer than the window.</b> Text uses {@link #TEXT_STEP} (the line height
 *       plus a gap), long lines are clipped to the panel, and content areas scroll with the mouse
 *       wheel inside a scissor rectangle so lines can never overlap or spill out of the panel.
 * </ul>
 */
public abstract class ModScreen extends Screen {
    protected static final int PANEL = 0xF0101018;
    protected static final int BORDER = 0xFF3A7BD5;
    protected static final int TEXT = 0xE0E0E0;
    protected static final int GOOD = 0x80FF80;
    protected static final int WARN = 0xFFD166;
    protected static final int BAD = 0xFF6060;
    protected static final int DIM = 0x909090;
    protected static final int ACCENT = 0x7FD8FF;

    /** Height of one text line including the gap to the next one. */
    protected static final int TEXT_STEP = 12;

    /** Mouse wheel scroll of the content area, in pixels. */
    protected int scroll;
    /** Total height of the scrollable content, set by the screens while they draw. */
    protected int contentHeight;
    /** Top of the scrollable content area, set by the screens while they draw. */
    protected int contentTop = 60;

    protected ModScreen(Text title) {
        super(title);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Draws the vanilla screen background (world blur + darkening) exactly once.
     *
     * <p>This is deliberately not {@code Screen#renderBackground}: the blur is a post process applied
     * to whatever is already in the frame buffer, so applying it a second time - which happens when
     * both the screen and the framework ask for a background - would blur the text drawn in between.
     * The panels therefore ask for the background once, then draw their text, then let
     * {@code super.render} draw the buttons on top.
     */
    protected void drawBackgroundOnce(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Intentionally empty; see drawBackgroundOnce. Widgets and the framework would otherwise
        // blur the text that the screens draw on top of the background.
    }

    // ------------------------------------------------------------------------------------------
    // Scrolling
    // ------------------------------------------------------------------------------------------

    /** Bottom of the scrollable content area. */
    protected int contentBottom() {
        return height - 30;
    }

    protected int visibleContentHeight() {
        return Math.max(1, contentBottom() - contentTop);
    }

    protected int maxScroll() {
        return Math.max(0, contentHeight - visibleContentHeight());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
                                 double verticalAmount) {
        if (maxScroll() <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        scroll = (int) Math.max(0, Math.min(maxScroll(),
                scroll - Math.round(verticalAmount * 24.0)));
        return true;
    }

    /** Clips to the content rectangle and shifts by the scroll offset; pair with endContent. */
    protected void beginContent(DrawContext context) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll));
        context.enableScissor(0, contentTop, width, contentBottom());
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, -scroll, 0.0f);
    }

    protected void endContent(DrawContext context) {
        context.getMatrices().pop();
        context.disableScissor();
        drawScrollbar(context);
    }

    private void drawScrollbar(DrawContext context) {
        if (maxScroll() <= 0) {
            return;
        }
        int top = contentTop;
        int bottom = contentBottom();
        int barHeight = Math.max(12, (int) ((bottom - top) * (double) visibleContentHeight()
                / Math.max(1, contentHeight)));
        int barY = top + (int) ((bottom - top - barHeight) * (double) scroll / maxScroll());
        context.fill(width - 5, top, width - 3, bottom, 0x40FFFFFF);
        context.fill(width - 5, barY, width - 3, barY + barHeight, 0xFF7FD8FF);
    }

    // ------------------------------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------------------------------

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

    /** Draws a line, shortened with an ellipsis if it would run past the given width. */
    protected void textClipped(DrawContext context, String line, int x, int y, int maxWidth,
                               int colour) {
        text(context, clip(line, maxWidth), x, y, colour);
    }

    protected void row(DrawContext context, String label, String value, int x, int y, int width,
                       int colour) {
        String clipped = clip(label, Math.max(0, width - textRenderer.getWidth(value) - 6));
        text(context, clipped, x, y, TEXT);
        context.drawTextWithShadow(textRenderer, Text.literal(value),
                x + width - textRenderer.getWidth(value), y, colour);
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

    /** Shortens a string so that it fits into maxWidth pixels. */
    protected String clip(String text, int maxWidth) {
        if (maxWidth <= 0 || textRenderer.getWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int limit = maxWidth - textRenderer.getWidth(suffix);
        StringBuilder builder = new StringBuilder();
        int used = 0;
        for (int i = 0; i < text.length(); i++) {
            int charWidth = textRenderer.getWidth(String.valueOf(text.charAt(i)));
            if (used + charWidth > limit) {
                break;
            }
            builder.append(text.charAt(i));
            used += charWidth;
        }
        return builder + suffix;
    }

    /** Wraps a paragraph to the given width, honouring the explicit line breaks in the text. */
    protected java.util.List<String> wrap(String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.length() == 0 ? word : current + " " + word;
                if (textRenderer.getWidth(candidate) > maxWidth && current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
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
