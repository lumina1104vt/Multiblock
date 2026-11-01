package com.lumina.lmb.compat;

import java.text.NumberFormat;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Numbers on the recipe pages of JEI and EMI: big ones go up a unit (1,000,000 K shows as 1MK), never to
 * scientific notation, and what still does not fit its place is drawn smaller.
 */
public final class RecipeNumbers {
    private static final String[] UNITS = {"k", "M", "G", "T", "P", "E"};
    /** From here on a number takes a unit; below it every digit fits. */
    private static final long PLAIN = 10_000;
    private static final int SLOT = 16;

    private RecipeNumbers() {
    }

    public static String compact(long value) {
        if (Math.abs(value) < PLAIN)
            return Long.toString(value);
        double shown = value;
        int unit = -1;
        while (Math.abs(shown) >= 999.95 && unit < UNITS.length - 1) {
            shown /= 1000;
            unit++;
        }
        String text = String.format(Locale.ROOT, Math.abs(shown) < 100 ? "%.1f" : "%.0f", shown);
        return (text.endsWith(".0") ? text.substring(0, text.length() - 2) : text) + UNITS[unit];
    }

    /** The tooltip line with the exact count, which the slot may only show rounded. */
    public static Component exact(long count) {
        return Component.translatable("lumina_multiblock.recipe.count", NumberFormat.getInstance().format(count)).withStyle(ChatFormatting.GRAY);
    }

    /** A stack's count, at the bottom right of the slot whose corner is x, y. One is not shown. */
    public static void drawCount(GuiGraphics g, long count, int x, int y) {
        if (count <= 1)
            return;
        Font font = Minecraft.getInstance().font;
        String text = compact(count);
        float scale = Math.min(1f, (float) SLOT / font.width(text));
        g.pose().pushPose();
        g.pose().translate(x + SLOT + 1 - font.width(text) * scale, y + SLOT + 1 - font.lineHeight * scale, 200);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    /** A line of text, made smaller if it would run past maxWidth. */
    public static void drawLine(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(1f, (float) maxWidth / Math.max(1, font.width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y + (font.lineHeight - font.lineHeight * scale) / 2, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
