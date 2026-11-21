package com.lumina.lmb.client;

import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.api.RoleBlocks;
import com.lumina.lmb.builder.BuilderContent;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Placement;
import com.lumina.lmb.core.RoleField;
import com.lumina.lmb.core.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The projection in the world: holding the builder and looking at a controller, the structure shows
 * round it, one layer at a time, the layer the player stands on: what is still to be placed as see-through
 * blocks, what is in the way boxed. All worked out here on the client, from the definition and the loaded
 * blocks; whether it forms stays the server's call. The player's own eye level is the switch of layers.
 */
public final class Projection {
    /** Ghost blocks drawn at most, the nearest ones. */
    private static final int MAX_GHOSTS = 1024;
    private static final int RESCAN_TICKS = 10, RESELECT_TICKS = 100;
    private static final float ALPHA = 0.45f;
    private static final float[] BRIGHT = {1f, 1f, 1f, 1f};
    private static final int[] LIGHTS = {LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT};
    /** Orange: where a block is wrong (not told apart from anything by colour alone: it is a box). */
    private static final float BOX_R = 1, BOX_G = 0.6f, BOX_B = 0.1f;

    private record Ghost(BlockPos pos, BlockState state) {
    }

    private static @Nullable BlockPos controller;
    private static @Nullable Placement<BlockState> placement;
    private static List<Ghost> ghosts = List.of();
    private static List<BlockPos> wrong = List.of();
    private static int ticks;

    private Projection() {
    }

    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null
                || !mc.player.getMainHandItem().is(BuilderContent.BUILDER.get()) && !mc.player.getOffhandItem().is(BuilderContent.BUILDER.get())) {
            clear();
            return;
        }
        // looking at the controller again now and then: the way it is being built may have shown by now
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                && (!hit.getBlockPos().equals(controller) || ticks % RESELECT_TICKS == 0)) {
            Definition<BlockState> def = definitionOf(mc.level.getBlockState(hit.getBlockPos()));
            if (def != null) {
                controller = hit.getBlockPos().immutable();
                placement = best(def, mc.level, controller, mc.player.getLookAngle());
                ticks = 1;
                scan(mc.level, mc.player.blockPosition());
            }
        }
        if (placement != null && !mc.level.getBlockState(controller).isAir() && ticks++ % RESCAN_TICKS == 0)
            scan(mc.level, mc.player.blockPosition());
        else if (placement != null && mc.level.getBlockState(controller).isAir())
            clear();
    }

    private static void clear() {
        controller = null;
        placement = null;
        ghosts = List.of();
        wrong = List.of();
    }

    private static @Nullable Definition<BlockState> definitionOf(BlockState state) {
        for (Definition<BlockState> def : Multiblocks.definitions().values())
            if (def.anchorMatches(state))
                return def;
        return null;
    }

    /**
     * The placement closest to built; of equals, the one with the controller nearest its middle (a lone
     * controller may be anywhere in a wall); then the one reaching furthest away from the player.
     */
    private static @Nullable Placement<BlockState> best(Definition<BlockState> def, Level level, BlockPos anchor, Vec3 look) {
        Placement<BlockState> best = null;
        int bestScore = -1;
        double bestOff = 0, bestAway = 0;
        for (int[] params : def.paramCombos()) {
            RoleField<BlockState> field = def.field(params);
            for (Transform transform : def.orientations(field, level.getBlockState(anchor))) {
                Placement<BlockState> p = new Placement<>(def, field, transform, anchor.asLong(), params);
                int[] score = {0};
                p.forEachCell((pos, role) -> {
                    BlockState there = level.getBlockState(BlockPos.of(pos));
                    if (!there.isAir() && role.matcher().test(there) != null)
                        score[0]++;
                });
                int[] b = p.bounds();
                Vec3 middle = new Vec3((b[0] + b[3]) / 2.0 - anchor.getX(), (b[1] + b[4]) / 2.0 - anchor.getY(), (b[2] + b[5]) / 2.0 - anchor.getZ());
                double off = middle.lengthSqr(), away = middle.dot(look);
                if (score[0] > bestScore || score[0] == bestScore && (off < bestOff || off == bestOff && away > bestAway)) {
                    best = p;
                    bestScore = score[0];
                    bestOff = off;
                    bestAway = away;
                }
            }
        }
        return best;
    }

    private static void scan(Level level, BlockPos player) {
        int[] bounds = placement.bounds();
        int layer = Math.max(bounds[1], Math.min(bounds[4], player.getY()));
        List<Ghost> found = new ArrayList<>();
        List<BlockPos> badly = new ArrayList<>();
        placement.forEachCell((packed, role) -> {
            BlockPos pos = BlockPos.of(packed);
            if (pos.getY() != layer || !level.isLoaded(pos))
                return;
            List<BlockState> blocks = RoleBlocks.of(role);
            if (blocks.isEmpty())
                return;     // a cell that must stay empty is not shown, whatever fills it
            BlockState there = level.getBlockState(pos);
            if (role.matcher().test(there) != null)
                return;
            if (there.isAir()) {
                found.add(new Ghost(pos, blocks.get(0)));
            } else {
                badly.add(pos);
            }
        });
        found.sort(Comparator.comparingDouble(g -> g.pos().distSqr(player)));
        ghosts = found.size() > MAX_GHOSTS ? List.copyOf(found.subList(0, MAX_GHOSTS)) : found;
        wrong = badly;
    }

    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || placement == null)
            return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();     // 1.20.1: the view's turn is in this stack (the model-view matrix is still the identity)
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType sheet = Sheets.translucentCullBlockSheet();
        VertexConsumer vc = buffers.getBuffer(sheet);
        RandomSource random = RandomSource.create();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (Ghost ghost : ghosts) {
            pose.pushPose();
            pose.translate(ghost.pos().getX(), ghost.pos().getY(), ghost.pos().getZ());
            BakedModel model = mc.getBlockRenderer().getBlockModel(ghost.state());
            for (Direction side : Direction.values())
                quads(vc, pose, model, ghost.state(), side, random);
            quads(vc, pose, model, ghost.state(), null, random);
            pose.popPose();
        }
        buffers.endBatch(sheet);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (BlockPos pos : wrong)
            LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.01), BOX_R, BOX_G, BOX_B, 1);
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static void quads(VertexConsumer vc, PoseStack pose, BakedModel model, BlockState state, @Nullable Direction side, RandomSource random) {
        random.setSeed(42);
        for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null))
            vc.putBulkData(pose.last(), quad, BRIGHT, 1f, 1f, 1f, ALPHA, LIGHTS, OverlayTexture.NO_OVERLAY, false);
    }
}
