package za.co.neroland.nerofactions.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * The gauge tooltips for the NeroFactions screens: each bar names itself, gives amount / capacity with
 * units, and its rate where there is one. Builders append lines to a list; the screen positions and
 * emits them through {@link #show}. Numbers come from {@link GaugeMath}; every word is an English
 * source lang key. Only the viewer's own standing, already on screen — no other player, no positions.
 */
public final class GaugeTooltips {

    private GaugeTooltips() {
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("nerofactions.gui.gauge." + key, args);
    }

    /**
     * The tier bar: the faction, the standing inside the current tier band ({@code threshold} up to
     * {@code nextThreshold}) with its percent, and how much more reaches {@code nextTier}; at the top
     * tier ({@code top}) only the standing. Standing moves per deed (and by whole days of decay after
     * leaving), so there is no live per-second rate to show.
     */
    public static void standing(List<Component> out, String faction, Component tier, int standing, int threshold,
            int nextThreshold, Component nextTier, boolean top, boolean hostile) {
        out.add(tr("standing", faction));
        out.add(tr("standing.tier", tier));
        if (top) {
            out.add(tr("standing.top", GaugeMath.grouped(standing)));
            return;
        }
        long band = Math.max(1L, (long) nextThreshold - threshold);
        long into = Math.max(0L, (long) standing - threshold);
        out.add(tr("standing.amount", GaugeMath.grouped(standing), GaugeMath.grouped(nextThreshold),
                GaugeMath.percent(into, band)));
        long left = Math.max(0L, (long) nextThreshold - standing);
        out.add(tr("standing.next", GaugeMath.grouped(left), nextTier));
        if (hostile) {
            out.add(tr("standing.hostile"));
        }
    }

    /**
     * Wrap every line to {@code width} pixels so a long faction name never runs off a small window.
     * Wrapped lines lose styling; these tooltips use none.
     */
    public static List<Component> wrap(Font font, List<Component> lines, int width) {
        List<Component> wrapped = new ArrayList<>(lines.size());
        int w = Math.max(60, width);
        for (Component line : lines) {
            if (font.width(line) <= w) {
                wrapped.add(line);
                continue;
            }
            for (FormattedText part : font.getSplitter().splitLines(line, w, Style.EMPTY)) {
                wrapped.add(Component.literal(part.getString()));
            }
        }
        return wrapped;
    }

    /** The wrap width for a screen {@code screenWidth} wide: half the window, 100..220 px. */
    public static int wrapWidth(int screenWidth) {
        return Math.max(100, Math.min(220, screenWidth / 2));
    }

    /** Emit a tooltip at the mouse (deferred on 1.21.6+, drawn immediately before that). */
    public static void show(GuiGraphicsExtractor extractor, Font font, List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        //? if >=1.21.6 {
        extractor.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        //?} else {
        /*extractor.renderComponentTooltip(font, lines, mouseX, mouseY);
        *///?}
    }
}
