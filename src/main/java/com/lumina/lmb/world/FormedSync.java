package com.lumina.lmb.world;

import com.lumina.lmb.core.Instance;
import com.lumina.lmb.core.Placement;
import com.lumina.lmb.core.StructureListener;
import com.lumina.lmb.network.FormedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells clients which structures look formed. Every player in the dimension hears every change (there are
 * few structures, and one can span many chunks); a player arriving in a dimension gets the full list.
 */
public final class FormedSync implements StructureListener<BlockState> {
    private final ServerLevel level;

    FormedSync(ServerLevel level) {
        this.level = level;
    }

    private static FormedPacket packet(Instance<BlockState> s, boolean formed) {
        Placement<BlockState> p = s.placement();
        return new FormedPacket(s.definition().id(), s.anchor(), p.transform().id(), p.params(), formed);
    }

    private void send(Instance<BlockState> s, boolean formed) {
        PacketDistributor.sendToPlayersInDimension(level, packet(s, formed));
    }

    @Override
    public void formed(Instance<BlockState> s) {
        send(s, true);
    }

    @Override
    public void recovered(Instance<BlockState> s) {
        send(s, true);
    }

    @Override
    public void damaged(Instance<BlockState> s) {
        send(s, false);
    }

    @Override
    public void removed(Instance<BlockState> s) {
        send(s, false);
    }

    static void sendAll(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, FormedPacket.Reset.INSTANCE);
        MultiblockData data = MultiblockData.get(player.serverLevel());
        if (data == null) return;
        for (Instance<BlockState> s : data.engine().all()) {
            if (s.isFormed()) PacketDistributor.sendToPlayer(player, packet(s, true));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
    }

    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sendAll(player);
    }
}
