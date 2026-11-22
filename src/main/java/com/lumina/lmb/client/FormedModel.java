package com.lumina.lmb.client;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.api.FormedLooks;
import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Role;
import com.lumina.lmb.core.RoleField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Draws a block of a formed structure as its look. Wraps the models of only the block states some
 * {@link FormedLooks} entry changes; when chunk meshing asks for a block's model data, the wrapper looks
 * the position up in {@link ClientStructures} and, inside a formed structure, carries the look along to
 * the quad and render-type queries.
 */
public final class FormedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockState> LOOK = new ModelProperty<>();
    /** The look model's own data for this position (a connected look reads its neighbours there). */
    private static final ModelProperty<ModelData> LOOK_DATA = new ModelProperty<>();

    private FormedModel(BakedModel original) {
        super(original);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData data = super.getModelData(level, pos, state, modelData);
        BlockState look = ClientStructures.lookAt(pos, state);
        if (look == null) return data;
        ModelData lookData = look.isAir() ? ModelData.EMPTY : model(look).getModelData(level, pos, look, ModelData.EMPTY);
        return data.derive().with(LOOK, look).with(LOOK_DATA, lookData).build();
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData data, RenderType renderType) {
        BlockState look = data.get(LOOK);
        if (look == null) return super.getQuads(state, side, rand, data, renderType);
        if (look.isAir()) return List.of();
        return model(look).getQuads(look, side, rand, lookData(data), renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        BlockState look = data.get(LOOK);
        if (look == null) return super.getRenderTypes(state, rand, data);
        if (look.isAir()) return ChunkRenderTypeSet.none();
        return model(look).getRenderTypes(look, rand, lookData(data));
    }

    private static ModelData lookData(ModelData data) {
        ModelData d = data.get(LOOK_DATA);
        return d == null ? ModelData.EMPTY : d;
    }

    private static BakedModel model(BlockState look) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(look);
    }

    /** Wraps the model of every block state that a registered look changes. */
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<Role<BlockState>, UnaryOperator<BlockState>> looks = new LinkedHashMap<>();
        FormedLooks.all().forEach((id, roles) -> {
            Definition<BlockState> def = Multiblocks.definitions().get(id);
            if (def == null) return;
            for (int[] params : def.paramCombos()) {
                RoleField<BlockState> f = def.field(params);
                for (int r = 1; r < f.paletteSize(); r++) {
                    Role<BlockState> role = f.paletteRole(r);
                    UnaryOperator<BlockState> look = roles.get(role.name());
                    if (look != null) looks.putIfAbsent(role, look);
                }
            }
        });
        if (looks.isEmpty()) {
            LuminaMultiBlock.LOGGER.info("formed looks: none registered when the models were baked");
            return;
        }
        Map<ResourceLocation, BakedModel> models = event.getModels();
        Set<ResourceLocation> wrapped = new HashSet<>();
        List<String> failed = new ArrayList<>();
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            for (Map.Entry<Role<BlockState>, UnaryOperator<BlockState>> e : looks.entrySet()) {
                boolean changes;
                try {
                    changes = e.getKey().matcher().test(state) != null && e.getValue().apply(state) != state;
                } catch (RuntimeException ex) {
                    failed.add(e.getKey().name());
                    continue;
                }
                if (!changes) continue;
                ResourceLocation loc = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(loc);
                if (model != null && !(model instanceof FormedModel) && wrapped.add(loc)) models.put(loc, new FormedModel(model));
                break;
            }
        }
        LuminaMultiBlock.LOGGER.info("formed looks: {} role looks, {} block models wrapped", looks.size(), wrapped.size());
        if (!failed.isEmpty()) LuminaMultiBlock.LOGGER.warn("formed looks: matchers of roles {} threw while picking models", Set.copyOf(failed));
    }
}
