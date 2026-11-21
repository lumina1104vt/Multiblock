package com.lumina.lmb.builder;

import com.lumina.lmb.LuminaMultiBlock;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/** The multiblock builder item and the data it carries (1.20.1 has no data components: the data is NBT on the stack). */
public final class BuilderContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, LuminaMultiBlock.MODID);

    public static final RegistryObject<BuilderItem> BUILDER = ITEMS.register("builder",
            () -> new BuilderItem(new Item.Properties().stacksTo(1)));

    private static final String MODES_TAG = "builder_modes", LINK_TAG = "builder_link";

    private BuilderContent() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(BuilderContent::addToTab);
    }

    private static void addToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(BUILDER.get());
    }

    /** The {@link BuilderModes} flags the builder in {@code stack} was set to (0 when never set). */
    public static int modes(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(MODES_TAG);
    }

    public static void setModes(ItemStack stack, int modes) {
        stack.getOrCreateTag().putInt(MODES_TAG, modes);
    }

    /** The wireless access point it was linked at, as AE2 links wireless terminals; null when not linked. */
    public static @Nullable GlobalPos link(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(LINK_TAG)) return null;
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(LINK_TAG)).result().orElse(null);
    }

    public static void setLink(ItemStack stack, @Nullable GlobalPos pos) {
        if (pos == null) {
            CompoundTag tag = stack.getTag();
            if (tag != null) {
                tag.remove(LINK_TAG);
                if (tag.isEmpty()) stack.setTag(null);
            }
            return;
        }
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result().ifPresent(t -> stack.getOrCreateTag().put(LINK_TAG, t));
    }
}
