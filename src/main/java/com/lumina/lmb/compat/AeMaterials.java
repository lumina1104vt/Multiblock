package com.lumina.lmb.compat;

import appeng.api.config.Actionable;
import appeng.api.features.GridLinkables;
import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.builder.MaterialSource;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The builder and AE2 (only loaded when AE2 is installed). The builder links like a wireless terminal:
 * put it in a wireless access point. It then reaches the network while the player is in range of one of
 * the network's access points, and costs no power.
 */
public final class AeMaterials {
    private AeMaterials() {}

    public static void registerLink() {
        GridLinkables.register(BuilderContent.BUILDER.get(), new IGridLinkableHandler() {
            @Override
            public boolean canLink(ItemStack stack) {
                return stack.is(BuilderContent.BUILDER.get());
            }

            @Override
            public void link(ItemStack stack, GlobalPos pos) {
                stack.set(BuilderContent.LINK.get(), pos);
            }

            @Override
            public void unlink(ItemStack stack) {
                stack.remove(BuilderContent.LINK.get());
            }
        });
    }

    /** The linked network's storage, or null when it cannot be reached now. */
    public static MaterialSource of(ServerPlayer player, GlobalPos link) {
        ServerLevel level = player.server.getLevel(link.dimension());
        if (level == null || !level.isLoaded(link.pos())) return null;
        if (!(level.getBlockEntity(link.pos()) instanceof IWirelessAccessPoint linked)) return null;
        IGrid grid = linked.getGrid();
        if (grid == null || !inRange(grid, player)) return null;
        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofPlayer(player);
        return new MaterialSource() {
            @Override
            public int take(Item item, int count) {
                return (int) storage.extract(AEItemKey.of(item), count, Actionable.MODULATE, source);
            }

            @Override
            public ItemStack give(ItemStack stack) {
                AEItemKey key = AEItemKey.of(stack);
                if (key == null) return stack;
                stack.shrink((int) storage.insert(key, stack.getCount(), Actionable.MODULATE, source));
                return stack;
            }
        };
    }

    private static boolean inRange(IGrid grid, ServerPlayer player) {
        for (WirelessAccessPointBlockEntity ap : grid.getActiveMachines(WirelessAccessPointBlockEntity.class)) {
            DimensionalBlockPos loc = ap.getLocation();
            if (!ap.isActive() || loc.getLevel() != player.level()) continue;
            double r = ap.getRange();
            if (loc.getPos().getCenter().distanceToSqr(player.position()) <= r * r) return true;
        }
        return false;
    }
}
