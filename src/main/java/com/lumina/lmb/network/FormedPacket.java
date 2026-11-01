package com.lumina.lmb.network;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.client.ClientStructures;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client: a structure started or stopped looking formed. Only its placement is sent; the client
 * works out the cells from the same definition.
 */
public record FormedPacket(String definition, long anchor, int transform, int[] params, boolean formed) implements CustomPacketPayload {
    public static final Type<FormedPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LuminaMultiBlock.MODID, "formed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FormedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUtf(p.definition);
                buf.writeLong(p.anchor);
                buf.writeVarInt(p.transform);
                buf.writeVarIntArray(p.params);
                buf.writeBoolean(p.formed);
            },
            buf -> new FormedPacket(buf.readUtf(), buf.readLong(), buf.readVarInt(), buf.readVarIntArray(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FormedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> ClientStructures.apply(packet));
    }

    /** Server → client: forget every structure (joining, or moving to another dimension); the formed ones follow. */
    public record Reset() implements CustomPacketPayload {
        public static final Reset INSTANCE = new Reset();
        public static final Type<Reset> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LuminaMultiBlock.MODID, "formed_reset"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reset> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static void handle(Reset packet, IPayloadContext context) {
            context.enqueueWork(ClientStructures::clear);
        }
    }
}
