package com.lumina.lmb.world;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.core.Engine;
import com.lumina.lmb.core.Instance;
import com.lumina.lmb.core.Pos;
import com.lumina.lmb.core.Record;
import com.lumina.lmb.core.WorldView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * A level's structures: its engine, and the engine's records in the level's save. Saved per dimension
 * as {@code data/lumina_multiblock.dat}; only placements are saved, never cells.
 */
public final class MultiblockData extends SavedData {
    private static final Map<ServerLevel, MultiblockData> LOADED = new ConcurrentHashMap<>();

    private final Engine<BlockState> engine;
    /** Block changes made off the server thread (some mods do), and chunk loads: applied at the next tick. */
    private final Queue<Long> offThread = new ConcurrentLinkedQueue<>();
    private final Queue<Long> loadedChunks = new ConcurrentLinkedQueue<>();

    private MultiblockData(ServerLevel level) {
        this.engine = new Engine<>(new View(level), Multiblocks.definitions(), this::setDirty);
        engine.addObserver(new FormedSync(level));
    }

    public static MultiblockData get(ServerLevel level) {
        return LOADED.get(level);
    }

    public Engine<BlockState> engine() {
        return engine;
    }

    static void attach(ServerLevel level) {
        MultiblockData data = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> new MultiblockData(level), (tag, registries) -> load(level, tag)), LuminaMultiBlock.MODID);
        LOADED.put(level, data);
    }

    static void detach(ServerLevel level) {
        LOADED.remove(level);
    }

    /** From every {@code LevelChunk#setBlockState} of a server level. */
    public static void blockChanged(ServerLevel level, BlockPos pos, BlockState state) {
        MultiblockData data = LOADED.get(level);
        if (data == null) return;
        if (level.getServer().isSameThread()) {
            data.engine.blockChanged(pos.asLong(), state);
        } else {
            data.offThread.add(pos.asLong());
        }
    }

    void chunkLoaded(ChunkPos pos) {
        loadedChunks.add(pos.toLong());
    }

    void tick() {
        Long p;
        while ((p = offThread.poll()) != null) engine.markDirty(p);
        while ((p = loadedChunks.poll()) != null) engine.chunkLoaded(Pos.chunkX(p), Pos.chunkZ(p));
        engine.tick();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Record r : engine.save()) {
            CompoundTag t = new CompoundTag();
            t.putString("definition", r.definition());
            t.putLong("anchor", r.anchor());
            t.putString("state", r.state().name());
            t.putInt("transform", r.transform());
            t.putIntArray("params", r.params());
            t.putLong("fingerprint", r.fingerprint());
            CompoundTag vars = new CompoundTag();
            r.vars().forEach(vars::putString);
            t.put("vars", vars);
            list.add(t);
        }
        tag.put("structures", list);
        return tag;
    }

    private static MultiblockData load(ServerLevel level, CompoundTag tag) {
        MultiblockData data = new MultiblockData(level);
        List<Record> records = new ArrayList<>();
        for (Tag e : tag.getList("structures", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            Instance.State state;
            try {
                state = Instance.State.valueOf(t.getString("state"));
            } catch (IllegalArgumentException ex) {
                state = Instance.State.PENDING;
            }
            CompoundTag v = t.getCompound("vars");
            Map<String, String> vars = new LinkedHashMap<>();
            for (String k : v.getAllKeys()) vars.put(k, v.getString(k));
            records.add(new Record(t.getString("definition"), t.getLong("anchor"), state, t.getInt("transform"),
                    t.getIntArray("params"), t.getLong("fingerprint"), vars));
        }
        data.engine.restore(records);
        return data;
    }

    private record View(ServerLevel level) implements WorldView<BlockState> {
        @Override
        public BlockState get(long pos) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(Pos.x(pos) >> 4, Pos.z(pos) >> 4);
            return chunk == null ? null : chunk.getBlockState(BlockPos.of(pos));
        }

        @Override
        public boolean isLoaded(int chunkX, int chunkZ) {
            return level.getChunkSource().getChunkNow(chunkX, chunkZ) != null;
        }

        @Override
        public boolean exclusive(long pos, BlockState state) {
            return Multiblocks.exclusive(level, BlockPos.of(pos), state);
        }
    }
}
