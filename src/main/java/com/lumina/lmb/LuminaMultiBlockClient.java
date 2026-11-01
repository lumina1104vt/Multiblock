package com.lumina.lmb;

import com.lumina.lmb.client.ClientStructures;
import com.lumina.lmb.client.FormedModel;
import com.lumina.lmb.client.Projection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Client side: formed structures change how their blocks are drawn; the builder projects structures. */
@Mod(value = LuminaMultiBlock.MODID, dist = Dist.CLIENT)
public class LuminaMultiBlockClient {
    public LuminaMultiBlockClient(IEventBus modEventBus) {
        modEventBus.addListener(FormedModel::onModifyBakingResult);
        NeoForge.EVENT_BUS.addListener(ClientStructures::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(Projection::onTick);
        NeoForge.EVENT_BUS.addListener(Projection::onRender);
    }
}
