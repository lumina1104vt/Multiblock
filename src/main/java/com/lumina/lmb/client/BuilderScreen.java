package com.lumina.lmb.client;

import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.builder.BuilderModes;
import com.lumina.lmb.network.BuilderModesPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** The builder's modes, each switched on or off on its own. */
public class BuilderScreen extends Screen {
    private final InteractionHand hand;
    private int modes;

    private BuilderScreen(InteractionHand hand, int modes) {
        super(Component.translatable("lumina_multiblock.builder.modes"));
        this.hand = hand;
        this.modes = modes;
    }

    public static void open(InteractionHand hand) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int modes = player.getItemInHand(hand).getOrDefault(BuilderContent.MODES.get(), 0);
        Minecraft.getInstance().setScreen(new BuilderScreen(hand, modes));
    }

    @Override
    protected void init() {
        int y = height / 2 - 40;
        for (int mode : BuilderModes.ALL) {
            addRenderableWidget(Button.builder(label(mode), b -> {
                modes ^= mode;
                b.setMessage(label(mode));
                PacketDistributor.sendToServer(new BuilderModesPacket(hand == InteractionHand.MAIN_HAND, modes));
            }).bounds(width / 2 - 100, y, 200, 20).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, y + 12, 200, 20).build());
    }

    private Component label(int mode) {
        return CommonComponents.optionNameValue(Component.translatable(BuilderModes.key(mode)),
                CommonComponents.optionStatus(BuilderModes.has(modes, mode)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
