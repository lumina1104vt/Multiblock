package com.lumina.lmb.compat;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.client.StructureMesh;
import com.lumina.lmb.core.Definition;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Every registered structure in JEI: a 3D view (drag to turn, scroll to zoom, shift-scroll to cut it
 * layer by layer) and what it takes at its smallest size, only the main block of a cell and what the
 * structure must have (one port); the
 * controller counts as both what it makes and what it uses, so looking one up either way finds it.
 */
@JeiPlugin
public class StructuresJei implements IModPlugin {
    private static final ResourceLocation ID = new ResourceLocation(LuminaMultiBlock.MODID, "structures");
    public static final RecipeType<Structure> TYPE = RecipeType.create(LuminaMultiBlock.MODID, "structures", Structure.class);

    /** One structure: its definition and name, the blocks that may be its controller, what it takes, how it is being looked at. */
    public record Structure(Definition<BlockState> def, Component name, List<ItemStack> controllers, List<ItemStack> materials, View view) {
    }

    private static final class View {
        float yaw = 0.7f, pitch = 0.5f, zoom = 1;
        /** Layers shown; 0 until the mesh is known: all. */
        int layers;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new Category(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Structure> structures = new ArrayList<>();
        Multiblocks.definitions().values().forEach(d -> structures.add(structure(d)));
        registration.addRecipes(TYPE, structures);
    }

    private static Structure structure(Definition<BlockState> def) {
        return new Structure(def, StructureInfo.name(def), StructureInfo.controllers(def), StructureInfo.materials(def), new View());
    }

    private static final class Category implements IRecipeCategory<Structure> {
        private static final int SLOT = 18, COLUMNS = 9, ROWS = 2, VIEW_Y = 20, VIEW_H = 100, GRID_Y = VIEW_Y + VIEW_H + 2;
        private static final int WIDTH = COLUMNS * SLOT;
        private final IDrawable icon;

        Category(IGuiHelper gui) {
            this.icon = gui.createDrawableItemLike(net.minecraft.world.level.block.Blocks.IRON_BLOCK);
        }

        @Override
        public RecipeType<Structure> getRecipeType() {
            return TYPE;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("gui.lumina_multiblock.jei.structures");
        }

        @Override
        public int getWidth() {
            return WIDTH;
        }

        @Override
        public int getHeight() {
            return GRID_Y + ROWS * SLOT;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Structure s, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 1, 1).setStandardSlotBackground().addItemStacks(s.controllers());
            // also a use of the controller, so U on it finds the structure as R does
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(s.controllers());
            builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStacks(StructureInfo.parts(s.def()));
            for (int i = 0; i < s.materials().size() && i < COLUMNS * ROWS; i++)
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % COLUMNS) * SLOT, GRID_Y + 1 + (i / COLUMNS) * SLOT)
                        .setStandardSlotBackground()
                        .setCustomRenderer(VanillaTypes.ITEM_STACK, CountedItemRenderer.INSTANCE)
                        .addItemStack(s.materials().get(i));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Structure s, IFocusGroup focuses) {
            View view = s.view();
            builder.addGuiEventListener(new IJeiGuiEventListener() {
                @Override
                public ScreenRectangle getArea() {
                    return new ScreenRectangle(0, VIEW_Y, WIDTH, VIEW_H);
                }

                @Override
                public boolean mouseClicked(double x, double y, int button) {
                    return true;     // the drag that follows turns the view
                }

                @Override
                public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                    view.yaw += (float) dx * 0.02f;
                    view.pitch = Mth.clamp(view.pitch + (float) dy * 0.02f, -1.5f, 1.5f);
                    return true;
                }

                @Override
                public boolean mouseScrolled(double x, double y, double vertical) {
                    if (Screen.hasShiftDown())
                        view.layers = Math.max(1, view.layers + (vertical > 0 ? 1 : -1));
                    else
                        view.zoom = Mth.clamp(view.zoom * (vertical > 0 ? 1.15f : 0.87f), 0.3f, 8);
                    return true;
                }
            });
        }

        @Override
        public void draw(Structure s, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
            var font = Minecraft.getInstance().font;
            g.drawString(font, s.name(), 24, 6, 0xFF404040, false);
            StructureMesh mesh = StructureMesh.of(s.def());
            View view = s.view();
            view.layers = view.layers <= 0 ? mesh.sizeY() : Math.min(view.layers, mesh.sizeY());
            g.fill(0, VIEW_Y, WIDTH, VIEW_Y + VIEW_H, 0xFF2A2A33);
            g.pose().pushPose();
            g.pose().translate(0, VIEW_Y, 0);
            mesh.drawView(g, WIDTH, VIEW_H, view.yaw, view.pitch, view.zoom, view.layers);
            g.pose().popPose();
            if (view.layers < mesh.sizeY())
                g.drawString(font, view.layers + " / " + mesh.sizeY(), 3, VIEW_Y + 3, 0xFFFFFFFF, false);
        }

        @Override
        public void getTooltip(ITooltipBuilder tooltip, Structure s, IRecipeSlotsView slots, double mouseX, double mouseY) {
            if (mouseY >= VIEW_Y && mouseY < VIEW_Y + VIEW_H)
                tooltip.add(Component.translatable("gui.lumina_multiblock.jei.view_hint"));
        }
    }
}
