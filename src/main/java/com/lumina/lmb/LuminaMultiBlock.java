package com.lumina.lmb;

import com.lumina.lmb.builder.BuildJob;
import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.compat.AeMaterials;
import com.lumina.lmb.network.BuilderModesPacket;
import com.lumina.lmb.network.FormedPacket;
import com.lumina.lmb.tools.LmbCommands;
import com.lumina.lmb.world.FormedSync;
import com.lumina.lmb.world.MultiblockEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/** lumina-multiblock: multiblock structures for other mods. Design: DESIGN.md beside this module's build. */
@Mod(LuminaMultiBlock.MODID)
public class LuminaMultiBlock {
    public static final String MODID = "lumina_multiblock";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LuminaMultiBlock(IEventBus modEventBus) {
        BuilderContent.register(modEventBus);
        modEventBus.addListener(LuminaMultiBlock::registerPayloads);
        modEventBus.addListener(LuminaMultiBlock::commonSetup);
        NeoForge.EVENT_BUS.addListener(BuildJob::onServerTick);
        NeoForge.EVENT_BUS.addListener(BuildJob::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MultiblockEvents::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(MultiblockEvents::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(MultiblockEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(MultiblockEvents::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(FormedSync::onLogin);
        NeoForge.EVENT_BUS.addListener(FormedSync::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(FormedSync::onRespawn);
        NeoForge.EVENT_BUS.addListener(LmbCommands::register);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(FormedPacket.TYPE, FormedPacket.STREAM_CODEC, FormedPacket::handle);
        registrar.playToClient(FormedPacket.Reset.TYPE, FormedPacket.Reset.STREAM_CODEC, FormedPacket.Reset::handle);
        registrar.playToServer(BuilderModesPacket.TYPE, BuilderModesPacket.STREAM_CODEC, BuilderModesPacket::handle);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // the builder links to AE networks like a wireless terminal (AE2 is optional)
        if (ModList.get().isLoaded("ae2")) event.enqueueWork(AeMaterials::registerLink);
    }
}
