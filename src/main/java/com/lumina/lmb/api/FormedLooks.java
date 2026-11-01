package com.lumina.lmb.api;

import com.lumina.lmb.core.Role;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * What blocks look like while their structure is formed. Only the look changes: the world keeps the
 * blocks the player placed (collision, outline and mining stay theirs). A damaged structure looks normal
 * again.
 *
 * <p>Per role, a function from the placed block to the block it is drawn as: the same state (no change),
 * another state (its model is drawn instead), or air (hidden; e.g. the controller draws the whole
 * structure). Register on both sides during mod construction or common setup, before models are baked:
 * the client wraps the models of exactly the block states some look changes.
 *
 * <p>Limit: the client decides which models to wrap when models are baked, before any world is joined,
 * so matchers that look at block tags are not seen at that point. Use block matchers for roles with a look.
 */
public final class FormedLooks {
    private static final BlockState HIDDEN = Blocks.AIR.defaultBlockState();
    private static final Map<String, Map<String, UnaryOperator<BlockState>>> LOOKS = new ConcurrentHashMap<>();

    private FormedLooks() {}

    public static void set(String definition, String role, UnaryOperator<BlockState> look) {
        LOOKS.computeIfAbsent(definition, k -> new ConcurrentHashMap<>()).put(role, look);
    }

    /** Every block of the role drawn as {@code drawnAs}. */
    public static void drawAs(String definition, String role, BlockState drawnAs) {
        set(definition, role, s -> drawnAs);
    }

    public static void hide(String definition, String role) {
        set(definition, role, s -> HIDDEN);
    }

    /** Definition id → role name → look. */
    public static Map<String, Map<String, UnaryOperator<BlockState>>> all() {
        return LOOKS;
    }

    /** How a block of {@code role} is drawn in a formed structure; null when it looks as it is. */
    public static BlockState lookOf(String definition, Role<BlockState> role, BlockState placed) {
        Map<String, UnaryOperator<BlockState>> roles = LOOKS.get(definition);
        if (roles == null) return null;
        UnaryOperator<BlockState> look = roles.get(role.name());
        if (look == null) return null;
        BlockState drawn = look.apply(placed);
        return drawn == null || drawn == placed ? null : drawn;
    }
}
