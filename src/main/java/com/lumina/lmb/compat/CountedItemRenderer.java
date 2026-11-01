package com.lumina.lmb.compat;

import java.util.ArrayList;
import java.util.List;

import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** An item in a recipe slot, with its count through {@link RecipeNumbers} rather than the game's plain number. */
public final class CountedItemRenderer implements IIngredientRenderer<ItemStack> {
    public static final CountedItemRenderer INSTANCE = new CountedItemRenderer();

    private CountedItemRenderer() {
    }

    @Override
    public void render(GuiGraphics g, ItemStack stack) {
        g.renderItem(stack, 0, 0);
        g.renderItemDecorations(Minecraft.getInstance().font, stack.copyWithCount(1), 0, 0);
        RecipeNumbers.drawCount(g, stack.getCount(), 0, 0);
    }

    @Override
    public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag));
        if (stack.getCount() > 1)
            lines.add(RecipeNumbers.exact(stack.getCount()));
        return lines;
    }
}
