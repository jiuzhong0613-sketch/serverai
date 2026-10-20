package com.shanshan.serverai;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class AiCommand {
    private AiCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ai")
                .executes(ctx -> help(ctx.getSource()))
                .then(Commands.literal("help")
                        .executes(ctx -> help(ctx.getSource())))
                .then(Commands.argument("question", StringArgumentType.greedyString())
                        .executes(ctx -> ask(ctx.getSource(), StringArgumentType.getString(ctx, "question")))));
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("§6[AI] §7用法：§f/ai <问题>"), false);
        source.sendSuccess(() -> Component.literal("§7例：§f/ai 匠魂怎么开始？"), false);
        source.sendSuccess(() -> Component.literal("§7回答仅你可见；问题会发送到 DeepSeek 并记录日志。"), false);
        return 1;
    }

    private static int ask(CommandSourceStack source, String question) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("只有玩家可以使用 /ai"));
            return 0;
        }
        AiService.ask(player, question);
        return 1;
    }
}
