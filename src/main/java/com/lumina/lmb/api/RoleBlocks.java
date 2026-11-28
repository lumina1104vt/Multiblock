package com.lumina.lmb.api;

import com.lumina.lmb.core.Role;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The blocks a role can be filled with: those it suggests, or failing that all that match it, so a
 * definition need not spell them out; {@link #prefer} says which comes first, the block to draw and to
 * place. None for a role that must stay empty. Kept per role: finding them goes through every block there is.
 */
public final class RoleBlocks {
    private static final Map<Role<BlockState>, List<BlockState>> BLOCKS = new ConcurrentHashMap<>();
    private static final Map<Role<BlockState>, Supplier<? extends Block>> PREFERRED = new ConcurrentHashMap<>();

    private static final Map<Role<BlockState>, Supplier<BlockState>> PLACED = new ConcurrentHashMap<>();

    private RoleBlocks() {}

    /**
     * For a role that wants one exact state (a top slab): what to draw and to place. Like {@link #prefer}, a
     * supplier, so {@link Role#places} is no use for blocks that don't exist yet.
     */
    public static void place(Role<BlockState> role, Supplier<BlockState> state) {
        PLACED.put(role, state);
    }

    /** For a role of several blocks: this one first. A supplier, as blocks don't exist yet when structures are defined. */
    public static void prefer(Role<BlockState> role, Supplier<? extends Block> block) {
        PREFERRED.put(role, block);
    }

    public static List<BlockState> of(Role<BlockState> role) {
        return BLOCKS.computeIfAbsent(role, RoleBlocks::find);
    }

    private static List<BlockState> find(Role<BlockState> role) {
        if (role.matcher().test(Blocks.AIR.defaultBlockState()) != null) return List.of();
        Supplier<BlockState> placed = PLACED.get(role);
        if (placed != null) return List.of(placed.get());
        List<BlockState> blocks = new ArrayList<>();
        for (BlockState state : role.suggestions()) {
            if (state.getBlock().asItem() != Items.AIR) blocks.add(state);
        }
        if (blocks.isEmpty()) {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block.asItem() != Items.AIR && role.matcher().test(block.defaultBlockState()) != null) {
                    blocks.add(block.defaultBlockState());
                }
            }
        }
        Supplier<? extends Block> first = PREFERRED.get(role);
        if (first != null) blocks.sort(Comparator.comparing(s -> !s.is(first.get())));
        return List.copyOf(blocks);
    }
}
