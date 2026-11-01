package com.lumina.lmb.client;

import com.lumina.lmb.api.RoleBlocks;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Role;
import com.lumina.lmb.core.RoleField;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A structure as a mesh, for a 3D view of it: one buffer per layer, so the view can show it up to a
 * layer, and faces between solid cells left out, so a structure of tens of thousands of blocks stays
 * cheap to draw. Made on first use and kept; at its smallest size.
 */
public final class StructureMesh {
    private static final Map<String, StructureMesh> CACHE = new HashMap<>();
    private static final RenderType TYPE = Sheets.translucentItemSheet();
    private static final Direction[] SIDES = Direction.values();

    private final int sizeX, sizeY, sizeZ;
    private final @Nullable VertexBuffer[] layers;

    private StructureMesh(RoleField<BlockState> field) {
        sizeX = field.sizeX();
        sizeY = field.sizeY();
        sizeZ = field.sizeZ();
        layers = new VertexBuffer[sizeY];
        Map<Role<BlockState>, BlockState> states = new HashMap<>();
        BlockState[] cells = new BlockState[field.volume()];
        for (int i = 0; i < cells.length; i++) {
            Role<BlockState> role = field.role(i);
            if (role != null)
                cells[i] = states.computeIfAbsent(role, r -> first(RoleBlocks.of(r)));
        }
        RandomSource random = RandomSource.create();
        try (ByteBufferBuilder memory = new ByteBufferBuilder(1 << 20)) {
            for (int y = 0; y < sizeY; y++) {
                BufferBuilder builder = new BufferBuilder(memory, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                PoseStack pose = new PoseStack();
                for (int x = 0; x < sizeX; x++)
                    for (int z = 0; z < sizeZ; z++) {
                        BlockState state = at(field, cells, x, y, z);
                        if (state == null)
                            continue;
                        pose.pushPose();
                        pose.translate(x, y, z);
                        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
                        for (Direction side : SIDES) {
                            BlockState next = at(field, cells, x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
                            if (next == null || !next.canOcclude())
                                quads(builder, pose, model, state, side, random);
                        }
                        quads(builder, pose, model, state, null, random);
                        pose.popPose();
                    }
                MeshData mesh = builder.build();
                if (mesh != null) {
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(mesh);
                    VertexBuffer.unbind();
                    layers[y] = buffer;
                }
            }
        }
    }

    public static StructureMesh of(Definition<BlockState> def) {
        return CACHE.computeIfAbsent(def.id(), id -> new StructureMesh(def.field(def.paramCombos().get(0))));
    }

    private static @Nullable BlockState first(List<BlockState> blocks) {
        return blocks.isEmpty() ? null : blocks.get(0);
    }

    private static @Nullable BlockState at(RoleField<BlockState> field, BlockState[] cells, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= field.sizeX() || y >= field.sizeY() || z >= field.sizeZ())
            return null;
        return cells[field.index(x, y, z)];
    }

    private static void quads(BufferBuilder builder, PoseStack pose, BakedModel model, BlockState state, @Nullable Direction side, RandomSource random) {
        random.setSeed(42);
        for (BakedQuad quad : model.getQuads(state, side, random, ModelData.EMPTY, null))
            builder.putBulkData(pose.last(), quad, 1, 1, 1, 1, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** A view of it filling {@code width} x {@code height} of the GUI from the pose's origin, up to layer {@code top}. */
    public void drawView(GuiGraphics g, int width, int height, float yaw, float pitch, float zoom, int top) {
        g.flush();
        Matrix4f gui = g.pose().last().pose();
        Vector3f origin = gui.transformPosition(new Vector3f());
        g.enableScissor((int) origin.x, (int) origin.y, (int) origin.x + width, (int) origin.y + height);
        float size = Mth.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ);
        float scale = height / size * zoom;
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(gui)
                .translate(width / 2f, height / 2f, 120)
                .scale(scale, -scale, scale)
                .rotateX(pitch)
                .rotateY(yaw)
                .translate(-sizeX / 2f, -sizeY / 2f, -sizeZ / 2f);
        Lighting.setupFor3DItems();
        draw(modelView, top);
        g.disableScissor();
    }

    /** Draws the layers below {@code top}, with {@code modelView} (from its low corner, one unit a block). */
    public void draw(Matrix4f modelView, int top) {
        TYPE.setupRenderState();
        for (int y = 0; y < Math.min(top, layers.length); y++) {
            VertexBuffer buffer = layers[y];
            if (buffer != null) {
                buffer.bind();
                buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
            }
        }
        VertexBuffer.unbind();
        TYPE.clearRenderState();
    }
}
