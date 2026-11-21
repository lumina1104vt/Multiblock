package com.lumina.lmb.network;

import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.builder.BuilderModes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the modes picked on the builder screen, for the builder in that hand. */
public record BuilderModesPacket(boolean mainHand, int modes) {
    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(mainHand);
        buf.writeVarInt(modes);
    }

    public static BuilderModesPacket read(FriendlyByteBuf buf) {
        return new BuilderModesPacket(buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(BuilderModesPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ItemStack stack = player.getItemInHand(packet.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            int all = BuilderModes.DECONSTRUCT | BuilderModes.MIRROR | BuilderModes.NO_HATCHES;
            if (stack.is(BuilderContent.BUILDER.get())) BuilderContent.setModes(stack, packet.modes & all);
        });
        ctx.setPacketHandled(true);
    }
}
