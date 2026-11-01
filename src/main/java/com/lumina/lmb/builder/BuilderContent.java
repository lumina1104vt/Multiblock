package com.lumina.lmb.builder;

import com.lumina.lmb.LuminaMultiBlock;
import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The multiblock builder item and the data it carries. */
public final class BuilderContent {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LuminaMultiBlock.MODID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, LuminaMultiBlock.MODID);

    /** {@link BuilderModes} flags. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MODES = COMPONENTS
            .registerComponentType("builder_modes", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** The wireless access point it was linked at, as AE2 links wireless terminals. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> LINK = COMPONENTS
            .registerComponentType("builder_link", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    public static final DeferredItem<BuilderItem> BUILDER = ITEMS.register("builder",
            () -> new BuilderItem(new Item.Properties().stacksTo(1)));

    private BuilderContent() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(BuilderContent::addToTab);
    }

    private static void addToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(BUILDER.get());
    }
}
