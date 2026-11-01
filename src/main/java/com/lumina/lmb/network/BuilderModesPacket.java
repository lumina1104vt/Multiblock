package com.lumina.lmb.network;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.builder.BuilderModes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client → server: the modes picked on the builder screen, for the builder in that hand. */
public record BuilderModesPacket(boolean mainHand, int modes) implements CustomPacketPayload {
    public static final Type<BuilderModesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LuminaMultiBlock.MODID, "builder_modes"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuilderModesPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.mainHand);
                buf.writeVarInt(p.modes);
            },
            buf -> new BuilderModesPacket(buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BuilderModesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ItemStack stack = context.player().getItemInHand(packet.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            int all = BuilderModes.DECONSTRUCT | BuilderModes.MIRROR | BuilderModes.NO_HATCHES;
            if (stack.is(BuilderContent.BUILDER.get())) stack.set(BuilderContent.MODES.get(), packet.modes & all);
        });
    }
}
