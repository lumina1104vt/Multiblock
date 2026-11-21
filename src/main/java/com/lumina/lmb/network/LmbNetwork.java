package com.lumina.lmb.network;

import com.lumina.lmb.LuminaMultiBlock;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/** The mod's packets (1.20.1: one SimpleChannel; each packet writes and reads itself). */
public final class LmbNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LuminaMultiBlock.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private LmbNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, FormedPacket.class, FormedPacket::write, FormedPacket::read, FormedPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, FormedPacket.Reset.class, FormedPacket.Reset::write, FormedPacket.Reset::read, FormedPacket.Reset::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, BuilderModesPacket.class, BuilderModesPacket::write, BuilderModesPacket::read, BuilderModesPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
