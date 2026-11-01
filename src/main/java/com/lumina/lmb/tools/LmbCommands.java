package com.lumina.lmb.tools;

import com.lumina.lmb.api.Multiblocks;
import com.lumina.lmb.core.Engine;
import com.lumina.lmb.core.Instance;
import com.lumina.lmb.core.Pos;
import com.lumina.lmb.core.Problem;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Debug commands until the diagnostics screen exists: {@code /lmb list}, {@code /lmb info <controller>},
 * {@code /lmb form <controller>}.
 */
public final class LmbCommands {
    private LmbCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lmb").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("info").then(Commands.argument("controller", BlockPosArgument.blockPos())
                        .executes(c -> withStructure(c, LmbCommands::info))))
                .then(Commands.literal("form").then(Commands.argument("controller", BlockPosArgument.blockPos())
                        .executes(c -> withStructure(c, LmbCommands::form)))));
    }

    private interface Action {
        int run(CommandSourceStack source, Engine<BlockState> engine, Instance<BlockState> structure);
    }

    private static int withStructure(CommandContext<CommandSourceStack> c, Action action) {
        CommandSourceStack source = c.getSource();
        BlockPos pos = BlockPosArgument.getBlockPos(c, "controller");
        Engine<BlockState> engine = Multiblocks.engine(source.getLevel());
        Instance<BlockState> s = engine == null ? null : engine.at(pos.asLong());
        if (s == null) {
            source.sendFailure(Component.translatable("lumina_multiblock.command.no_controller", pos.toShortString()));
            return 0;
        }
        return action.run(source, engine, s);
    }

    private static int list(CommandSourceStack source) {
        Engine<BlockState> engine = Multiblocks.engine(source.getLevel());
        if (engine == null || engine.all().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lumina_multiblock.command.no_structures"), false);
            return 0;
        }
        for (Instance<BlockState> s : engine.all()) {
            send(source, s.definition().id() + " @ " + Pos.str(s.anchor()) + ": " + s.state());
        }
        return engine.all().size();
    }

    private static int info(CommandSourceStack source, Engine<BlockState> engine, Instance<BlockState> s) {
        StringBuilder head = new StringBuilder(s.definition().id() + " @ " + Pos.str(s.anchor()) + ": " + s.state());
        if (s.transform() != null) head.append("  turn ").append(s.transform()).append("  params ").append(Arrays.toString(s.params()));
        if (s.readyToForm()) head.append("  (complete, forms by hand)");
        send(source, head.toString());
        if (!s.counts().isEmpty()) send(source, "blocks: " + join(s.counts(), Object::toString));
        Map<String, Integer> missing = engine.missing(s);
        if (!missing.isEmpty()) send(source, "wrong: " + join(missing, Object::toString));
        if (s.unseenCells() > 0) send(source, "not checked yet (chunk not loaded since restart): " + s.unseenCells());
        List<Problem> problems = engine.problems(s, 8);
        for (Problem p : problems) {
            send(source, "  " + p.type() + " " + p.role() + " @ " + Pos.str(p.pos()) + (p.detail().isEmpty() ? "" : " (" + p.detail() + ")"));
        }
        return 1;
    }

    private static int form(CommandSourceStack source, Engine<BlockState> engine, Instance<BlockState> s) {
        if (s.state() != Instance.State.PENDING) {
            send(source, "Already " + s.state());
            return 0;
        }
        if (engine.tryForm(s.anchor())) {
            send(source, "Formed");
            return 1;
        }
        send(source, "Not complete; /lmb info shows why");
        return 0;
    }

    private static <V> String join(Map<String, V> map, Function<V, String> value) {
        StringBuilder b = new StringBuilder();
        map.forEach((k, v) -> b.append(b.isEmpty() ? "" : ", ").append(k).append(' ').append(value.apply(v)));
        return b.toString();
    }

    private static void send(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }
}
