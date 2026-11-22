package com.lumina.lmb.client;

import com.lumina.lmb.api.FormedLooks;
import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Placement;
import com.lumina.lmb.core.Role;
import com.lumina.lmb.network.FormedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The formed structures of the client's dimension, as the server reports them. Read by chunk meshing on
 * worker threads: changes happen on the client thread and publish a fresh chunk index.
 */
public final class ClientStructures {
    private static final Map<Long, Placement<BlockState>> BY_ANCHOR = new LinkedHashMap<>();
    private static volatile Map<Long, List<Placement<BlockState>>> byChunk = Map.of();

    private ClientStructures() {}

    public static void apply(FormedPacket packet) {
        com.lumina.lmb.LuminaMultiBlock.LOGGER.info("formed packet: {} at {} {} (definition {})", packet.definition(), net.minecraft.core.BlockPos.of(packet.anchor()).toShortString(),
                packet.formed() ? "formed" : "not formed", Multiblocks.definitions().containsKey(packet.definition()) ? "known" : "UNKNOWN on this client");
        Placement<BlockState> old = BY_ANCHOR.remove(packet.anchor());
        if (old != null) redraw(old);
        if (packet.formed()) {
            Definition<BlockState> def = Multiblocks.definitions().get(packet.definition());
            Placement<BlockState> p = def == null ? null : Placement.of(def, packet.transform(), packet.anchor(), packet.params());
            if (p != null) {
                BY_ANCHOR.put(packet.anchor(), p);
                redraw(p);
            }
        }
        reindex();
    }

    public static void clear() {
        BY_ANCHOR.values().forEach(ClientStructures::redraw);
        BY_ANCHOR.clear();
        reindex();
    }

    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BY_ANCHOR.clear();
        reindex();
    }

    /** How the block at {@code pos} is drawn; null when it looks as it is. Any thread. */
    public static BlockState lookAt(BlockPos pos, BlockState state) {
        Map<Long, List<Placement<BlockState>>> index = byChunk;
        if (index.isEmpty()) return null;
        List<Placement<BlockState>> list = index.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (list == null) return null;
        long p = pos.asLong();
        for (Placement<BlockState> placement : list) {
            Role<BlockState> role = placement.roleAt(p);
            // a wrong block in a cell (within tolerance) keeps its own look
            if (role == null || role.matcher().test(state) == null) continue;
            BlockState look = FormedLooks.lookOf(placement.definition().id(), role, state);
            if (look != null) return look;
        }
        return null;
    }

    private static void reindex() {
        Map<Long, List<Placement<BlockState>>> index = new HashMap<>();
        for (Placement<BlockState> p : BY_ANCHOR.values()) {
            int[] b = p.bounds();
            for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++) {
                for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++) {
                    index.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(p);
                }
            }
        }
        byChunk = index;
    }

    /** Rebuilds the chunk meshes the structure is in. */
    private static void redraw(Placement<BlockState> p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int[] b = p.bounds();
        mc.levelRenderer.setBlocksDirty(b[0], b[1], b[2], b[3], b[4], b[5]);
    }
}
