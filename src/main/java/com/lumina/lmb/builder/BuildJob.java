package com.lumina.lmb.builder;

import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.api.RoleBlocks;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Engine;
import com.lumina.lmb.core.Instance;
import com.lumina.lmb.core.Match;
import com.lumina.lmb.core.Placement;
import com.lumina.lmb.core.Role;
import com.lumina.lmb.core.Transform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One build (or take-down) the builder started. In creative mode it runs at once; otherwise a few blocks
 * a tick, from the controller outwards (taking down: towards the controller), until done or stopped.
 *
 * <p>Which placement: a formed or damaged structure is repaired (or taken down) as it is. For a pending
 * one, the placement closest to complete; when several are equally close (a lone controller), the one
 * reaching away from the clicked face, i.e. behind the controller as seen by the player.
 *
 * <p>Every block counts as placed or broken by the player, so protection mods can refuse it. Blocks in
 * the way are never broken: they are reported.
 */
public final class BuildJob {
    private static final int PER_TICK = 16;
    private static final int REPORT_POSITIONS = 3;
    private static final Map<UUID, BuildJob> JOBS = new HashMap<>();

    /** {@code target} null: take the block down. */
    private record Step(BlockPos pos, BlockState target) {
    }

    private final UUID player;
    private final ResourceKey<Level> dimension;
    private final boolean creative;
    private final GlobalPos link;
    private final List<Step> steps;
    private int next;

    private int placed, removed, refused, noItem;
    private int noRoom;
    private final List<BlockPos> inTheWay = new ArrayList<>();
    private final Map<Item, Integer> missing = new LinkedHashMap<>();
    private boolean warnedLink;

    private BuildJob(ServerPlayer player, GlobalPos link, List<Step> steps) {
        this.player = player.getUUID();
        this.dimension = player.level().dimension();
        this.creative = player.isCreative();
        this.link = link;
        this.steps = steps;
    }

    public static void start(ServerPlayer player, ItemStack builder, ServerLevel level, BlockPos clicked, Direction face) {
        Engine<BlockState> engine = Multiblocks.engine(level);
        Instance<BlockState> structure = engine == null ? null : engine.at(clicked.asLong());
        if (structure == null) {
            player.displayClientMessage(Component.translatable("lumina_multiblock.builder.not_controller"), true);
            return;
        }
        stop(player, false);
        int modes = builder.getOrDefault(BuilderContent.MODES.get(), 0);
        boolean deconstruct = BuilderModes.has(modes, BuilderModes.DECONSTRUCT);
        boolean noHatches = BuilderModes.has(modes, BuilderModes.NO_HATCHES);
        Definition<BlockState> def = structure.definition();
        List<Engine.Candidate<BlockState>> candidates = engine.candidates(structure);
        Placement<BlockState> placement = deconstruct ? candidates.get(0).placement() : choose(candidates, structure.anchor(), face);
        // mirroring only means something for a structure not built yet
        if (!deconstruct && BuilderModes.has(modes, BuilderModes.MIRROR) && structure.state() == Instance.State.PENDING) {
            Transform mirrored = Transform.MIRROR_X.then(placement.transform());
            if (!def.transforms().contains(mirrored)) {
                player.displayClientMessage(Component.translatable("lumina_multiblock.builder.cannot_mirror"), false);
                return;
            }
            placement = new Placement<>(def, placement.field(), mirrored, structure.anchor(), placement.params());
        }

        BlockPos anchor = BlockPos.of(structure.anchor());
        List<Step> steps = new ArrayList<>();
        List<BlockPos> inTheWay = new ArrayList<>();
        int[] noSuggestion = {0};
        // what the structure must have (one port): placed in the first cells that can take it
        Map<String, Integer> required = new HashMap<>();
        if (!noHatches) {
            def.limits().forEach(l -> required.put(l.kind(), l.min()));
            placement.forEachCell((p, role) -> {
                Match m = role.matcher().test(level.getBlockState(BlockPos.of(p)));
                if (m != null) required.computeIfPresent(m.kind(), (k, n) -> n - 1);
            });
        }
        placement.forEachCell((p, role) -> {
            BlockPos pos = BlockPos.of(p);
            if (!level.isLoaded(pos)) return;
            BlockState state = level.getBlockState(pos);
            Match m = role.matcher().test(state);
            if (deconstruct) {
                // the controller stays: it is what a build starts from again
                if (p != structure.anchor() && m != null && !state.isAir() && !(noHatches && def.isHatch(m))) steps.add(new Step(pos, null));
                return;
            }
            if (m != null) return;
            BlockState target = required(role, required);
            if (target == null) target = suggestion(def, role, noHatches);
            if (target == null || target.isAir()) {
                noSuggestion[0]++;
            } else if (!state.canBeReplaced()) {
                inTheWay.add(pos);
            } else {
                steps.add(new Step(pos, target));
            }
        });
        Comparator<Step> byDistance = Comparator.comparingDouble(s -> s.pos.distSqr(anchor));
        steps.sort(deconstruct ? byDistance.reversed() : byDistance);

        BuildJob job = new BuildJob(player, builder.get(BuilderContent.LINK.get()), steps);
        job.inTheWay.addAll(inTheWay);
        if (noSuggestion[0] > 0 && steps.isEmpty() && inTheWay.isEmpty()) {
            player.displayClientMessage(Component.translatable("lumina_multiblock.builder.no_suggestion", noSuggestion[0]), false);
        }
        if (job.creative) {
            job.run(player, Integer.MAX_VALUE);
            job.report(player);
        } else {
            JOBS.put(player.getUUID(), job);
            player.displayClientMessage(Component.translatable(deconstruct ? "lumina_multiblock.builder.started_down"
                    : "lumina_multiblock.builder.started", steps.size()), true);
        }
    }

    /** Ties go to the placement reaching away from the clicked face. */
    private static Placement<BlockState> choose(List<Engine.Candidate<BlockState>> candidates, long anchor, Direction face) {
        int best = candidates.get(0).wrong();
        BlockPos a = BlockPos.of(anchor);
        Direction away = face.getOpposite();
        Placement<BlockState> chosen = null;
        double chosenScore = Double.NEGATIVE_INFINITY;
        for (Engine.Candidate<BlockState> c : candidates) {
            if (c.wrong() != best) break;
            int[] b = c.placement().bounds();
            double score = ((b[0] + b[3]) / 2.0 - a.getX()) * away.getStepX() + ((b[1] + b[4]) / 2.0 - a.getY()) * away.getStepY()
                    + ((b[2] + b[5]) / 2.0 - a.getZ()) * away.getStepZ();
            // of those reaching as far, the controller nearest its middle (it may be anywhere in a wall)
            double x = (b[0] + b[3]) / 2.0 - a.getX(), y = (b[1] + b[4]) / 2.0 - a.getY(), z = (b[2] + b[5]) / 2.0 - a.getZ();
            score -= 0.001 * (x * x + y * y + z * z);
            if (score > chosenScore) {
                chosen = c.placement();
                chosenScore = score;
            }
        }
        return chosen;
    }

    /** A block of this role that a count limit still wants, or null. */
    private static BlockState required(Role<BlockState> role, Map<String, Integer> required) {
        for (BlockState s : RoleBlocks.of(role)) {
            Match m = role.matcher().test(s);
            if (m != null && required.getOrDefault(m.kind(), 0) > 0) {
                required.merge(m.kind(), -1, Integer::sum);
                return s;
            }
        }
        return null;
    }

    /** What to put in a cell: the role's first block that is not a hatch, or else (unless told not to) its first. */
    private static BlockState suggestion(Definition<BlockState> def, Role<BlockState> role, boolean noHatches) {
        List<BlockState> blocks = RoleBlocks.of(role);
        for (BlockState s : blocks) {
            if (!def.isHatch(role.matcher().test(s))) return s;
        }
        return noHatches || blocks.isEmpty() ? null : blocks.get(0);
    }

    public static void stop(ServerPlayer player) {
        stop(player, true);
    }

    private static void stop(ServerPlayer player, boolean report) {
        BuildJob job = JOBS.remove(player.getUUID());
        if (job != null && report) {
            player.displayClientMessage(Component.translatable("lumina_multiblock.builder.stopped"), false);
            job.report(player);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        for (Iterator<BuildJob> it = JOBS.values().iterator(); it.hasNext(); ) {
            BuildJob job = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(job.player);
            if (p == null || p.level().dimension() != job.dimension) {
                it.remove();
                continue;
            }
            job.run(p, PER_TICK);
            if (job.next >= job.steps.size()) {
                it.remove();
                job.report(p);
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        JOBS.clear();
    }

    private void run(ServerPlayer p, int budget) {
        ServerLevel level = p.serverLevel();
        MaterialSource.Chain materials = creative ? null : MaterialSource.of(p, link);
        if (materials != null && materials.linkUnreachable() && !warnedLink) {
            warnedLink = true;
            p.displayClientMessage(Component.translatable("lumina_multiblock.builder.link_unreachable"), false);
        }
        while (next < steps.size() && budget-- > 0) {
            Step s = steps.get(next++);
            if (!level.isLoaded(s.pos)) continue;
            if (s.target == null) {
                takeDown(p, level, s.pos, materials);
            } else {
                place(p, level, s.pos, s.target, materials);
            }
        }
    }

    private void place(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState target, MaterialSource materials) {
        if (!level.getBlockState(pos).canBeReplaced()) {
            inTheWay.add(pos);
            return;
        }
        if (!level.mayInteract(p, pos)) {
            refused++;
            return;
        }
        Item item = target.getBlock().asItem();
        if (materials != null) {
            if (item == Items.AIR) {
                noItem++;
                return;
            }
            if (materials.take(item, 1) < 1) {
                missing.merge(item, 1, Integer::sum);
                return;
            }
        }
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        level.setBlock(pos, target, Block.UPDATE_ALL);
        if (EventHooks.onBlockPlace(p, snapshot, Direction.UP)) {
            snapshot.restore(Block.UPDATE_ALL);
            refused++;
            if (materials != null) giveBack(p, materials, new ItemStack(item));
            return;
        }
        placed++;
    }

    private void takeDown(ServerPlayer p, ServerLevel level, BlockPos pos, MaterialSource materials) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        if (!level.mayInteract(p, pos) || CommonHooks.fireBlockBreak(level, p.gameMode.getGameModeForPlayer(), p, pos, state).isCanceled()) {
            refused++;
            return;
        }
        // into the AE network, then the inventory; a block with nowhere to go stays up
        Item item = state.getBlock().asItem();
        if (materials != null && item != Items.AIR && !materials.give(new ItemStack(item)).isEmpty()) {
            noRoom++;
            return;
        }
        // containers (hatches) spill their contents as when broken by hand
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        removed++;
    }

    private static void giveBack(ServerPlayer p, MaterialSource materials, ItemStack stack) {
        ItemStack left = materials.give(stack);
        if (!left.isEmpty()) p.drop(left, false);
    }

    private void report(ServerPlayer p) {
        p.displayClientMessage(Component.translatable("lumina_multiblock.builder.done", placed, removed), false);
        if (!inTheWay.isEmpty()) {
            MutableComponent where = Component.empty();
            for (int i = 0; i < Math.min(REPORT_POSITIONS, inTheWay.size()); i++) {
                where.append(i == 0 ? "" : ", ").append(inTheWay.get(i).toShortString());
            }
            p.displayClientMessage(Component.translatable("lumina_multiblock.builder.in_the_way", inTheWay.size(), where)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        if (refused > 0) {
            p.displayClientMessage(Component.translatable("lumina_multiblock.builder.refused", refused).withStyle(ChatFormatting.YELLOW), false);
        }
        if (noRoom > 0) {
            p.displayClientMessage(Component.translatable("lumina_multiblock.builder.no_room", noRoom).withStyle(ChatFormatting.YELLOW), false);
        }
        if (noItem > 0) {
            p.displayClientMessage(Component.translatable("lumina_multiblock.builder.no_item", noItem).withStyle(ChatFormatting.YELLOW), false);
        }
        if (!missing.isEmpty()) {
            MutableComponent list = Component.empty();
            boolean first = true;
            for (Map.Entry<Item, Integer> e : missing.entrySet()) {
                list.append(first ? "" : ", ").append(e.getKey().getDescription()).append(" ×" + e.getValue());
                first = false;
            }
            p.displayClientMessage(Component.translatable("lumina_multiblock.builder.missing", list).withStyle(ChatFormatting.RED), false);
        }
    }
}
