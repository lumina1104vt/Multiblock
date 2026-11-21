package com.lumina.lmb.compat;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.client.StructureMesh;
import com.lumina.lmb.core.Definition;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Every registered structure in EMI, as in JEI: a 3D view (turning by itself, as EMI has no dragging) and what it takes. */
@EmiEntrypoint
public class StructuresEmi implements EmiPlugin {
    private static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            new ResourceLocation(LuminaMultiBlock.MODID, "structures"), EmiStack.of(Blocks.IRON_BLOCK));

    @Override
    public void register(EmiRegistry registry) {
        registry.addCategory(CATEGORY);
        Multiblocks.definitions().values().forEach(d -> registry.addRecipe(new Recipe(d)));
    }

    private static final class Recipe extends BasicEmiRecipe {
        private static final int SLOT = 18, COLUMNS = 8, ROWS = 2, VIEW_Y = 20, VIEW_H = 90, GRID_Y = VIEW_Y + VIEW_H + 2;
        private final Definition<BlockState> def;

        Recipe(Definition<BlockState> def) {
            super(CATEGORY, new ResourceLocation(def.id()).withPrefix("/structure/"), COLUMNS * SLOT, GRID_Y + ROWS * SLOT);
            this.def = def;
            StructureInfo.materials(def).forEach(s -> inputs.add(stack(s)));
            StructureInfo.parts(def).forEach(s -> catalysts.add(stack(s)));
            StructureInfo.controllers(def).forEach(s -> {
                outputs.add(stack(s));
                inputs.add(stack(s));
            });
        }

        private static EmiStack stack(ItemStack s) {
            return EmiStack.of(s, s.getCount());
        }

        @Override
        public void addWidgets(WidgetHolder widgets) {
            List<ItemStack> controllers = StructureInfo.controllers(def);
            if (!controllers.isEmpty())
                widgets.addSlot(stack(controllers.get(0)), 0, 0).recipeContext(this);
            widgets.addText(StructureInfo.name(def), 22, 5, 0xFF404040, false);
            widgets.addDrawable(0, VIEW_Y, width, VIEW_H, (g, mouseX, mouseY, delta) -> {
                g.fill(0, 0, width, VIEW_H, 0xFF2A2A33);
                StructureMesh.of(def).drawView(g, width, VIEW_H, Util.getMillis() / 1500f % ((float) Math.PI * 2), 0.5f, 1, Integer.MAX_VALUE);
            });
            List<ItemStack> materials = StructureInfo.materials(def);
            for (int i = 0; i < materials.size() && i < COLUMNS * ROWS; i++)
                widgets.add(new CountedEmiSlot(stack(materials.get(i)), (i % COLUMNS) * SLOT, GRID_Y + (i / COLUMNS) * SLOT));
        }
    }
}
