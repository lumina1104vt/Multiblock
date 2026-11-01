package com.lumina.lmb.api;

import com.lumina.lmb.core.Match;
import com.lumina.lmb.core.Matcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;
import java.util.Set;

/** Matchers on block states. Without an explicit kind, a block counts as its own id ("minecraft:glass"). */
public final class BlockMatchers {
    private static final Match AIR = Match.of("minecraft:air");

    private BlockMatchers() {}

    public static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    /** Must stay empty. */
    public static Matcher<BlockState> air() {
        return s -> s.isAir() ? AIR : null;
    }

    public static Matcher<BlockState> blocks(Block... blocks) {
        Set<Block> set = Set.copyOf(Arrays.asList(blocks));
        return s -> set.contains(s.getBlock()) ? Match.of(id(s)) : null;
    }

    /** Any of these blocks, all counted as {@code kind} ("input_hatch"). */
    public static Matcher<BlockState> kind(String kind, Block... blocks) {
        Set<Block> set = Set.copyOf(Arrays.asList(blocks));
        Match m = Match.of(kind);
        return s -> set.contains(s.getBlock()) ? m : null;
    }

    public static Matcher<BlockState> tag(TagKey<Block> tag) {
        return s -> s.is(tag) ? Match.of(id(s)) : null;
    }

    public static Matcher<BlockState> kind(String kind, TagKey<Block> tag) {
        Match m = Match.of(kind);
        return s -> s.is(tag) ? m : null;
    }
}
