package com.lumina.lmb.network;

import com.lumina.lmb.client.ClientStructures;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a structure started or stopped looking formed. Only its placement is sent; the client
 * works out the cells from the same definition.
 */
public record FormedPacket(String definition, long anchor, int transform, int[] params, boolean formed) {
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(definition);
        buf.writeLong(anchor);
        buf.writeVarInt(transform);
        buf.writeVarIntArray(params);
        buf.writeBoolean(formed);
    }

    public static FormedPacket read(FriendlyByteBuf buf) {
        return new FormedPacket(buf.readUtf(), buf.readLong(), buf.readVarInt(), buf.readVarIntArray(), buf.readBoolean());
    }

    public static void handle(FormedPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientStructures.apply(packet));
        ctx.setPacketHandled(true);
    }

    /** Server → client: forget every structure (joining, or moving to another dimension); the formed ones follow. */
    public record Reset() {
        public static final Reset INSTANCE = new Reset();

        public void write(FriendlyByteBuf buf) {
        }

        public static Reset read(FriendlyByteBuf buf) {
            return INSTANCE;
        }

        public static void handle(Reset packet, Supplier<NetworkEvent.Context> context) {
            NetworkEvent.Context ctx = context.get();
            ctx.enqueueWork(ClientStructures::clear);
            ctx.setPacketHandled(true);
        }
    }
}
