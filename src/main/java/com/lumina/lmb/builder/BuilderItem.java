package com.lumina.lmb.builder;

import com.lumina.lmb.client.BuilderScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The multiblock builder. Sneak-right-click a structure's controller to build it (or take it down, see
 * {@link BuilderModes}): sneaking, as a plain right-click on a controller opens the controller's own
 * screen first. Sneak-right-click the air to pick modes; right-click the air to stop a build. Materials
 * come from the inventory, then from the AE network it was linked to in a wireless access point. In
 * creative mode it takes nothing and places everything at once.
 */
public class BuilderItem extends Item {
    public BuilderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (!player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            BuildJob.start(sp, context.getItemInHand(), server, context.getClickedPos(), context.getClickedFace());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) return new InteractionResultHolder<>(openModes(level, hand), stack);
        if (player instanceof ServerPlayer sp) BuildJob.stop(sp);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static InteractionResult openModes(Level level, InteractionHand hand) {
        if (level.isClientSide) BuilderScreen.open(hand);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int modes = BuilderContent.modes(stack);
        for (int mode : BuilderModes.ALL) {
            if (BuilderModes.has(modes, mode)) tooltip.add(Component.translatable(BuilderModes.key(mode)).withStyle(ChatFormatting.AQUA));
        }
        GlobalPos link = BuilderContent.link(stack);
        tooltip.add(link == null
                ? Component.translatable("lumina_multiblock.builder.unlinked").withStyle(ChatFormatting.GRAY)
                : Component.translatable("lumina_multiblock.builder.linked", link.pos().toShortString()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("lumina_multiblock.builder.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
