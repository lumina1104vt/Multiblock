package com.lumina.lmb.compat;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.client.gui.GuiGraphics;

/** An EMI slot with its count through {@link RecipeNumbers} rather than EMI's own. */
public final class CountedEmiSlot extends SlotWidget {
    public CountedEmiSlot(EmiIngredient stack, int x, int y) {
        super(stack, x, y);
        if (stack.getAmount() > 1)
            appendTooltip(RecipeNumbers.exact(stack.getAmount()));
    }

    @Override
    public void drawStack(GuiGraphics g, int mouseX, int mouseY, float delta) {
        Bounds b = getBounds();
        int x = b.x() + (b.width() - 16) / 2, y = b.y() + (b.height() - 16) / 2;
        getStack().render(g, x, y, delta, ~EmiIngredient.RENDER_AMOUNT);
        RecipeNumbers.drawCount(g, getStack().getAmount(), x, y);
    }
}
