package com.particlephysics.client.screen;

import java.util.List;

import com.particlephysics.client.ClientState;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * The research journal: the eight chapters of the progression, the element collection (all 118
 * elements with their discovery state) and the experiment log.
 */
public class JournalScreen extends ModScreen {
    private int chapter;
    private int page;

    public JournalScreen() {
        super(Text.literal("Research Journal"));
    }

    @Override
    protected void init() {
        NbtCompound data = ClientState.journal;
        int chapters = data == null ? 8 : data.getInt("chapterCount");
        int x = 10;
        int y = 24;
        for (int i = 0; i < chapters; i++) {
            final int index = i;
            String title = data == null ? "Chapter " + (i + 1)
                    : ClientState.compounds(data, "chapters").get(i).getString("title");
            button((i + 1) + ". " + title, x, y, 170, 18, () -> {
                chapter = index;
                sendChapter(index);
            });
            y += 20;
        }
        button("Prev page", x, height - 26, 80, 20, () -> page = Math.max(0, page - 1));
        button("Next page", x + 84, height - 26, 80, 20, () -> page++);
        button("Close", width - 70, height - 26, 60, 20, this::close);
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
        NbtCompound data = ClientState.journal;
        panel(context, 186, 22, width - 194, height - 52);
        if (data == null) {
            text(context, "The journal is empty - use the journal item again", 194, 30, WARN);
            super.render(context, mouseX, mouseY, delta);
            return;
        }
        List<NbtCompound> chapters = ClientState.compounds(data, "chapters");
        if (chapter >= chapters.size()) {
            chapter = 0;
        }
        NbtCompound current = chapters.isEmpty() ? new NbtCompound() : chapters.get(chapter);

        int x = 194;
        int y = 30;
        text(context, current.getString("title"), x, y, 0x7FD8FF);
        y += 13;
        for (String line : current.getString("description").split("\n")) {
            text(context, line, x, y, DIM);
            y += 10;
        }
        y += 6;
        double progress = current.getDouble("progress");
        text(context, "Chapter progress: " + String.format("%.0f%%", progress * 100.0), x, y, TEXT);
        y += 14;
        progress(context, x, y, width - 210, 6, progress, GOOD);
        y += 14;

        for (NbtCompound objective : ClientState.compounds(current, "objectives")) {
            boolean done = objective.getBoolean("done");
            String label = (done ? "[x] " : "[ ] ") + objective.getString("text");
            text(context, label, x, y, done ? GOOD : TEXT);
            if (!done && objective.getDouble("target") > 1.0) {
                String counts = String.format("%.4g / %.4g %s", objective.getDouble("value"),
                        objective.getDouble("target"), objective.getString("unit"));
                int width = textRenderer.getWidth(counts);
                context.drawTextWithShadow(textRenderer, Text.literal(counts),
                        this.width - 16 - width, y, WARN);
            }
            y += 11;
        }

        y += 8;
        if (chapter == 6) {
            renderElementGrid(context, data, x, y);
        } else {
            text(context, data.getString("lastEvent"), x, y, DIM);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderElementGrid(DrawContext context, NbtCompound data, int x, int y) {
        List<NbtCompound> elements = ClientState.compounds(data, "elements");
        text(context, "Element collection: " + data.getInt("discoveredElements") + " / "
                + elements.size(), x, y, 0x7FD8FF);
        y += 13;
        int perRow = 18;
        int cell = 16;
        for (int i = page * perRow * 7; i < elements.size()
                && i < (page + 1) * perRow * 7; i++) {
            NbtCompound element = elements.get(i);
            int index = i - page * perRow * 7;
            int column = index % perRow;
            int row = index / perRow;
            int ex = x + column * (cell + 2);
            int ey = y + row * (cell + 2);
            boolean discovered = element.getBoolean("discovered");
            context.fill(ex, ey, ex + cell, ey + cell,
                    discovered ? 0xFF205040 : 0xFF202028);
            context.drawBorder(ex, ey, cell, cell, discovered ? GOOD : 0xFF505050);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(element.getString("symbol")), ex + 2, ey + 1,
                    discovered ? 0xFFFFFF : 0x707070);
            context.drawTextWithShadow(textRenderer,
                    Text.literal(Integer.toString(element.getInt("z"))), ex + 1, ey + 8,
                    discovered ? 0xA0FFA0 : 0x606060);
        }
    }
}
