package com.lumina.lmb.compat;

import com.lumina.lmb.api.RoleBlocks;
import com.lumina.lmb.core.CountLimit;
import com.lumina.lmb.core.Definition;
import com.lumina.lmb.core.Match;
import com.lumina.lmb.core.Role;
import com.lumina.lmb.core.RoleField;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What a recipe viewer shows of a structure, at its smallest size. */
public final class StructureInfo {
    private StructureInfo() {}

    public static Component name(Definition<BlockState> def) {
        ResourceLocation id = ResourceLocation.parse(def.id());
        return Component.translatableWithFallback("multiblock." + id.getNamespace() + "." + id.getPath(), def.id());
    }

    public static List<ItemStack> controllers(Definition<BlockState> def) {
        return RoleBlocks.of(def.anchorRole()).stream().map(s -> new ItemStack(s.getBlock().asItem())).toList();
    }

    /** Every block that can be part of it, needed or not: U on any of them finds the structure. */
    public static List<ItemStack> parts(Definition<BlockState> def) {
        RoleField<BlockState> field = def.field(def.paramCombos().get(0));
        Set<Role<BlockState>> roles = new LinkedHashSet<>();
        for (int i = 0; i < field.volume(); i++) {
            Role<BlockState> role = field.role(i);
            if (role != null)
                roles.add(role);
        }
        Set<Item> items = new LinkedHashSet<>();
        for (Role<BlockState> role : roles)
            for (BlockState block : RoleBlocks.of(role))
                if (block.getBlock().asItem() != Items.AIR)
                    items.add(block.getBlock().asItem());
        return items.stream().map(ItemStack::new).toList();
    }

    /** Only what is needed: of a role's blocks the main one, and what a count limit asks for apart. */
    public static List<ItemStack> materials(Definition<BlockState> def) {
        RoleField<BlockState> field = def.field(def.paramCombos().get(0));
        Map<Role<BlockState>, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < field.volume(); i++) {
            Role<BlockState> role = field.role(i);
            if (role != null)
                counts.merge(role, 1, Integer::sum);
        }
        List<ItemStack> required = new ArrayList<>();
        for (CountLimit limit : def.limits()) {
            if (limit.min() <= 0)
                continue;
            for (Role<BlockState> role : counts.keySet())
                for (BlockState block : RoleBlocks.of(role)) {
                    Match m = role.matcher().test(block);
                    if (m != null && m.kind().equals(limit.kind()) && required.stream().noneMatch(s -> s.is(block.getBlock().asItem()))) {
                        required.add(new ItemStack(block.getBlock().asItem(), limit.min()));
                        counts.merge(role, -limit.min(), Integer::sum);
                    }
                }
        }
        Role<BlockState> anchor = def.anchorRole();
        List<ItemStack> materials = new ArrayList<>();
        counts.forEach((role, count) -> {
            List<BlockState> blocks = RoleBlocks.of(role);
            if (role != anchor && !blocks.isEmpty() && count > 0)
                materials.add(new ItemStack(blocks.get(0).getBlock().asItem(), count));
        });
        materials.addAll(required);
        return materials;
    }
}
