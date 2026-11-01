package com.lumina.lmb.builder;

import com.lumina.lmb.compat.AeMaterials;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/** Where the builder takes blocks from and puts them back. Other storage mods can add their own later. */
public interface MaterialSource {
    /** Takes up to {@code count}; returns how many it took. */
    int take(Item item, int count);

    /** Stores what it can; returns what is left (possibly the same, shrunk stack). */
    ItemStack give(ItemStack stack);

    /**
     * The player's sources: taking tries the inventory first, then the linked AE network; giving back tries
     * the AE network first, so a big structure taken down does not flood the inventory.
     */
    static Chain of(ServerPlayer player, GlobalPos link) {
        MaterialSource inventory = new MaterialSource() {
            @Override
            public int take(Item item, int count) {
                int taken = 0;
                for (ItemStack s : player.getInventory().items) {
                    if (taken == count) break;
                    if (!s.is(item)) continue;
                    int n = Math.min(count - taken, s.getCount());
                    s.shrink(n);
                    taken += n;
                }
                return taken;
            }

            @Override
            public ItemStack give(ItemStack stack) {
                player.getInventory().add(stack);
                return stack;
            }
        };
        MaterialSource ae = link != null && ModList.get().isLoaded("ae2") ? AeMaterials.of(player, link) : null;
        List<MaterialSource> takeOrder = new ArrayList<>(List.of(inventory));
        List<MaterialSource> giveOrder = new ArrayList<>();
        if (ae != null) {
            takeOrder.add(ae);
            giveOrder.add(ae);
        }
        giveOrder.add(inventory);
        return new Chain(takeOrder, giveOrder, link != null && ae == null);
    }

    /** @param linkUnreachable linked to an AE network that cannot be reached now (out of range, unloaded, gone) */
    record Chain(List<MaterialSource> takeOrder, List<MaterialSource> giveOrder, boolean linkUnreachable) implements MaterialSource {
        @Override
        public int take(Item item, int count) {
            int taken = 0;
            for (MaterialSource s : takeOrder) {
                if (taken == count) break;
                taken += s.take(item, count - taken);
            }
            return taken;
        }

        @Override
        public ItemStack give(ItemStack stack) {
            for (MaterialSource s : giveOrder) {
                if (stack.isEmpty()) break;
                stack = s.give(stack);
            }
            return stack;
        }
    }
}
