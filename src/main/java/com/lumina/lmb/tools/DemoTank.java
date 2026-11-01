package com.lumina.lmb.tools;

import com.lumina.lmb.LuminaMultiBlock;
import com.lumina.lmb.api.BlockMatchers;
import com.lumina.lmb.api.FormedLooks;
import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Instance;
import com.lumina.lmb.core.Matcher;
import com.lumina.lmb.core.Pos;
import com.lumina.lmb.core.Role;
import com.lumina.lmb.core.RoleField;
import com.lumina.lmb.core.Shape;
import com.lumina.lmb.core.StructureListener;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * A test structure in vanilla blocks, registered in the dev client only. A hollow cube, 3 to 9 wide
 * (odd), with a lodestone in the middle of one face:
 * <ul>
 *   <li>edges: iron, gold or diamond blocks, all the same ("tier" variable);</li>
 *   <li>faces: iron blocks or glass, hoppers (input hatches, at most 4) or a barrel (maintenance hatch,
 *       at most 1); up to 2 face blocks may go missing before it counts as damaged;</li>
 *   <li>inside: air.</li>
 * </ul>
 * The builder puts iron everywhere, the lodestone, and a hopper in the middle of the back face. Check it
 * with {@code /lmb info <lodestone>}.
 */
public final class DemoTank {
    private static final Role<BlockState> CONTROLLER = Role.of("controller", BlockMatchers.blocks(Blocks.LODESTONE))
            .places(Blocks.LODESTONE.defaultBlockState());
    private static final Role<BlockState> EDGE = Role.of("edge",
                    BlockMatchers.blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK).withVar("tier", BlockMatchers::id))
            .places(Blocks.IRON_BLOCK.defaultBlockState());
    private static final Matcher<BlockState> FACE_BLOCKS = Matcher.anyOf(
            BlockMatchers.kind("casing", Blocks.IRON_BLOCK, Blocks.GLASS),
            BlockMatchers.kind("input_hatch", Blocks.HOPPER),
            BlockMatchers.kind("maintenance_hatch", Blocks.BARREL));
    private static final Role<BlockState> FACE = Role.of("face", FACE_BLOCKS).tolerance(2).places(Blocks.IRON_BLOCK.defaultBlockState());
    /** The middle of the back face: where the builder puts an input hatch (casing with "no hatches"). */
    private static final Role<BlockState> PORT = Role.of("port", FACE_BLOCKS)
            .places(Blocks.HOPPER.defaultBlockState(), Blocks.IRON_BLOCK.defaultBlockState());
    private static final Role<BlockState> INSIDE = Role.of("inside", BlockMatchers.air());

    private DemoTank() {}

    public static final String ID = LuminaMultiBlock.MODID + ":demo_tank";

    public static void register() {
        // formed: the casing faces turn light blue (hatches keep their look)
        BlockState formedFace = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
        for (String role : new String[]{"face", "port"}) {
            FormedLooks.set(ID, role, s -> s.is(Blocks.IRON_BLOCK) || s.is(Blocks.GLASS) ? formedFace : s);
        }
        Multiblocks.register(Definition.builder(ID,
                        Shape.of(List.of(new Shape.Param("size", 3, 9, 2)), v -> box(v[0])))
                .hatchKinds("input_hatch", "maintenance_hatch")
                .limit("input_hatch", 0, 4)
                .limit("maintenance_hatch", 0, 1)
                .listener(new StructureListener<>() {
                    @Override
                    public void formed(Instance<BlockState> s) {
                        log("formed", s);
                    }

                    @Override
                    public void damaged(Instance<BlockState> s) {
                        log("damaged", s);
                    }

                    @Override
                    public void recovered(Instance<BlockState> s) {
                        log("recovered", s);
                    }

                    @Override
                    public void removed(Instance<BlockState> s) {
                        log("removed", s);
                    }

                    @Override
                    public void membersChanged(Instance<BlockState> s) {
                        log("members changed " + s.counts() + " in", s);
                    }
                })
                .build());
    }

    private static void log(String what, Instance<BlockState> s) {
        LuminaMultiBlock.LOGGER.info("demo tank {} at {} (tier {})", what, Pos.str(s.anchor()), s.variable("tier"));
    }

    private static RoleField<BlockState> box(int n) {
        RoleField.Builder<BlockState> b = RoleField.builder(n, n, n);
        for (int y = 0; y < n; y++) {
            for (int z = 0; z < n; z++) {
                for (int x = 0; x < n; x++) {
                    int sides = (x == 0 || x == n - 1 ? 1 : 0) + (y == 0 || y == n - 1 ? 1 : 0) + (z == 0 || z == n - 1 ? 1 : 0);
                    b.set(x, y, z, sides >= 2 ? EDGE : sides == 1 ? FACE : INSIDE);
                }
            }
        }
        int c = n / 2;
        return b.set(c, c, n - 1, PORT).set(c, c, 0, CONTROLLER).anchor(c, c, 0).build();
    }
}
