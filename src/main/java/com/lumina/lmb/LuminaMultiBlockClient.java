package com.lumina.lmb;

import com.lumina.lmb.client.ClientStructures;
import com.lumina.lmb.client.FormedModel;
import com.lumina.lmb.client.Projection;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/** Client side: formed structures change how their blocks are drawn; the builder projects structures. */
public final class LuminaMultiBlockClient {
    private LuminaMultiBlockClient() {}

    static void init() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(FormedModel::onModifyBakingResult);
        MinecraftForge.EVENT_BUS.addListener(ClientStructures::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(Projection::onTick);
        MinecraftForge.EVENT_BUS.addListener(Projection::onRender);
    }
}
