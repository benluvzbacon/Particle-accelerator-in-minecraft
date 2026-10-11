package com.particlephysics.client.screen;

import java.util.List;

import com.particlephysics.client.ClientState;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * The research journal: the eight chapters of the progression, the element collection (all 118
 * elements with their discovery state) and the experiment log.
 *
 * <p>The layout is built so that it cannot overlap itself: every line is {@link #TEXT_STEP} apart,
 * long lines are clipped or wrapped to the panel, and the chapter text scrolls with the mouse wheel
 * inside a scissor rectangle.
 */
public class JournalScreen extends ModScreen {
    private static final int LEFT_WIDTH = 196;
    private static final int CELL = 15;

    private int chapter;

    public JournalScreen() {
        super(Text.literal("Research Journal"));
    }

    @Override
    protected void init() {
        List<NbtCompound> chapters = ClientState.compounds(ClientState.journal, "chapters");
        int x = 10;
        int y = 26;
        int count = Math.max(8, chapters.size());
        for (int i = 0; i < count; i++) {
            final int index = i;
            String title = i < chapters.size() ? chapters.get(i).getString("title")
                    : "Chapter " + (i + 1);
            double progress = i < chapters.size() ? chapters.get(i).getDouble("progress") : 0.0;
            String label = (i + 1) + ". " + clip(title, LEFT_WIDTH - 60)
                    + (progress >= 1.0 ? "  [done]" : "");
            button(label, x, y, LEFT_WIDTH, 16, () -> {
                chapter = index;
                scroll = 0;
                sendChapter(index);
            });
            y += 18;
        }
        button("Close", width - 74, height - 26, 64, 20, this::close);
    }

    private void sendChapter(int index) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("route", "quests");
        nbt.putString("action", "chapter");
        nbt.putInt("value", index);
        com.particlephysics.net.ModNetworking.sendAction(nbt);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        drawBackgroundOnce(context, mouseX, mouseY, delta);
        panel(context, 8, 20, LEFT_WIDTH + 6, height - 46);
        panel(context, LEFT_WIDTH + 22, 20, width - LEFT_WIDTH - 30, height - 46);

        NbtCompound data = ClientState.journal;
        if (data == null) {
            text(context, "The journal is empty - use the journal item again", 14, 28, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        List<NbtCompound> chapters = ClientState.compounds(data, "chapters");
        if (chapters.isEmpty()) {
            text(context, "No chapters yet", 14, 28, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        if (chapter >= chapters.size()) {
            chapter = 0;
        }
        NbtCompound current = chapters.get(chapter);

        int x = LEFT_WIDTH + 28;
        int panelRight = width - 14;
        int textWidth = panelRight - x;

        int y = 28;
        textClipped(context, current.getString("title"), x, y, textWidth, ACCENT);
        y += TEXT_STEP + 2;
        for (String line : wrap(current.getString("description"), textWidth)) {
            textClipped(context, line, x, y, textWidth, DIM);
            y += TEXT_STEP;
        }
        y += 4;
        double progress = current.getDouble("progress");
        textClipped(context, String.format("Chapter progress: %.0f%%", progress * 100.0), x, y,
                textWidth, TEXT);
        y += 13;
        progress(context, x, y, textWidth, 6, progress, GOOD);
        y += 12;

        // --- scrollable body ------------------------------------------------------------------
        contentTop = y;
        beginContent(context);
        int body = y;
        int cursor = body;
        List<NbtCompound> objectives = ClientState.compounds(current, "objectives");
        for (NbtCompound objective : objectives) {
            boolean done = objective.getBoolean("done");
            String label = (done ? "[x] " : "[ ] ") + objective.getString("text");
            int target = (int) objective.getDouble("target");
            String counts = "";
            if (target > 1) {
                counts = String.format("%.4g / %.4g %s", objective.getDouble("value"),
                        objective.getDouble("target"), objective.getString("unit"));
            }
            int countsWidth = counts.isEmpty() ? 0 : textRenderer.getWidth(counts) + 8;
            textClipped(context, label, x, cursor, textWidth - countsWidth, done ? GOOD : TEXT);
            if (!counts.isEmpty()) {
                if (textRenderer.getWidth(label) + countsWidth <= textWidth) {
                    context.drawTextWithShadow(textRenderer, Text.literal(counts),
                            panelRight - textRenderer.getWidth(counts), cursor, WARN);
                } else {
                    cursor += TEXT_STEP;
                    context.drawTextWithShadow(textRenderer, Text.literal(counts), x, cursor, WARN);
                }
            }
            cursor += TEXT_STEP;
        }
        cursor += 6;

        if (chapter == 6) {
            cursor = renderElementGrid(context, data, x, textWidth, cursor);
        } else {
            for (String line : wrap(data.getString("lastEvent"), textWidth)) {
                textClipped(context, line, x, cursor, textWidth, DIM);
                cursor += TEXT_STEP;
            }
        }
        contentHeight = cursor - body;
        endContent(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private int renderElementGrid(DrawContext context, NbtCompound data, int x, int textWidth,
                                  int y) {
        List<NbtCompound> elements = ClientState.compounds(data, "elements");
        int discovered = data.getInt("discoveredElements");
        text(context, "Element collection: " + discovered + " / " + elements.size(), x, y, ACCENT);
        y += TEXT_STEP + 2;
        int perRow = Math.max(1, (textWidth + 2) / (CELL + 2));
        int rows = Math.max(1, (elements.size() + perRow - 1) / perRow);
        for (int i = 0; i < elements.size(); i++) {
            NbtCompound element = elements.get(i);
            int ex = x + (i % perRow) * (CELL + 2);
            int ey = y + (i / perRow) * (CELL + 2);
            boolean found = element.getBoolean("discovered");
            context.fill(ex, ey, ex + CELL, ey + CELL, found ? 0xFF205040 : 0xFF202028);
            context.drawBorder(ex, ey, CELL, CELL, found ? GOOD : 0xFF505050);
            context.drawTextWithShadow(textRenderer, Text.literal(element.getString("symbol")),
                    ex + 2, ey + 1, found ? 0xFFFFFF : 0x707070);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(Integer.toString(element.getInt("z"))), ex + 2, ey + 8,
                    found ? 0xA0FFA0 : 0x606060);
        }
        y += rows * (CELL + 2) + 6;
        text(context, "Symbol and atomic number, " + perRow + " per row, sorted by Z. Scroll with "
                + "the mouse wheel.", x, y, DIM);
        y += TEXT_STEP + 6;
        text(context, "Nuclides identified: " + data.getInt("discoveredIsotopes")
                + "   collisions recorded: " + data.getInt("collisions"), x, y, TEXT);
        y += TEXT_STEP;
        text(context, "Lifetime dose: " + com.particlephysics.radiation.RadiationEffects.describe(
                data.getDouble("dose")), x, y, DIM);
        y += TEXT_STEP;
        return y;
    }
}
