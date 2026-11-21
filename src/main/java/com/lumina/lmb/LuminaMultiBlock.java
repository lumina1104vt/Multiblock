package com.lumina.lmb;

import com.lumina.lmb.builder.BuildJob;
import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.compat.AeMaterials;
import com.lumina.lmb.network.LmbNetwork;
import com.lumina.lmb.tools.LmbCommands;
import com.lumina.lmb.world.FormedSync;
import com.lumina.lmb.world.MultiblockEvents;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** lumina-multiblock: multiblock structures for other mods. Design: DESIGN.md beside this module's build. */
@Mod(LuminaMultiBlock.MODID)
public class LuminaMultiBlock {
    public static final String MODID = "lumina_multiblock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LuminaMultiBlock() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        BuilderContent.register(modEventBus);
        modEventBus.addListener(LuminaMultiBlock::commonSetup);
        MinecraftForge.EVENT_BUS.addListener(BuildJob::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(BuildJob::onServerStopping);
        MinecraftForge.EVENT_BUS.addListener(MultiblockEvents::onLevelLoad);
        MinecraftForge.EVENT_BUS.addListener(MultiblockEvents::onLevelUnload);
        MinecraftForge.EVENT_BUS.addListener(MultiblockEvents::onLevelTick);
        MinecraftForge.EVENT_BUS.addListener(MultiblockEvents::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(FormedSync::onLogin);
        MinecraftForge.EVENT_BUS.addListener(FormedSync::onChangedDimension);
        MinecraftForge.EVENT_BUS.addListener(FormedSync::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(LmbCommands::register);
        // the client side (formed models, the projection) is wired from a client-only call
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> LuminaMultiBlockClient::init);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(LmbNetwork::register);
        // the builder links to AE networks like a wireless terminal (AE2 is optional)
        if (ModList.get().isLoaded("ae2")) event.enqueueWork(AeMaterials::registerLink);
    }
}
