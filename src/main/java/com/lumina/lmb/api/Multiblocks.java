package com.lumina.lmb.api;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Engine;
import com.lumina.lmb.core.Instance;
import com.lumina.lmb.world.MultiblockData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The lib's entry point for mods: register definitions, then ask about structures in a level.
 * Definition ids are "modid:name". Register during mod construction or common setup; when two
 * definitions accept the same controller block, the one registered first wins.
 */
public final class Multiblocks {
    /** Blocks of other mods that keep state for one machine only (see {@link MultiblockMember}). */
    public static final TagKey<Block> EXCLUSIVE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(LuminaMultiBlock.MODID, "exclusive"));

    private static final Map<String, Definition<BlockState>> DEFINITIONS = new LinkedHashMap<>();

    private Multiblocks() {}

    public static synchronized void register(Definition<BlockState> definition) {
        if (DEFINITIONS.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("multiblock " + definition.id() + " registered twice");
        }
    }

    /** Live view, read by every level's engine. */
    public static Map<String, Definition<BlockState>> definitions() {
        return Collections.unmodifiableMap(DEFINITIONS);
    }

    /** Null while the level is not loaded. */
    public static Engine<BlockState> engine(ServerLevel level) {
        MultiblockData data = MultiblockData.get(level);
        return data == null ? null : data.engine();
    }

    /** The structure whose controller is at {@code controller}. */
    public static Instance<BlockState> at(ServerLevel level, BlockPos controller) {
        Engine<BlockState> e = engine(level);
        return e == null ? null : e.at(controller.asLong());
    }

    /** Formed or damaged structures holding the block at {@code pos}. */
    public static List<Instance<BlockState>> owners(ServerLevel level, BlockPos pos) {
        Engine<BlockState> e = engine(level);
        return e == null ? List.of() : e.owners(pos.asLong());
    }

    /** For definitions that form by hand (a wrench): forms the structure if it is complete. */
    public static boolean tryForm(ServerLevel level, BlockPos controller) {
        Engine<BlockState> e = engine(level);
        return e != null && e.tryForm(controller.asLong());
    }

    public static boolean exclusive(Level level, BlockPos pos, BlockState state) {
        if (state.is(EXCLUSIVE)) return true;
        if (state.getBlock() instanceof MultiblockMember m && !m.canShare(level, pos, state)) return true;
        return state.hasBlockEntity() && level.getBlockEntity(pos) instanceof MultiblockMember m && !m.canShare(level, pos, state);
    }
}
