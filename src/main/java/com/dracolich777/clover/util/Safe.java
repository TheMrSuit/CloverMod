package com.dracolich777.clover.util;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.clover;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public final class Safe {
    private Safe() {}

    public interface Body { int run() throws Exception; }

    public static int cmd(CommandContext<CommandSourceStack> ctx, String what, Body b) throws CommandSyntaxException {
        try {
            return b.run();
        } catch (CommandSyntaxException e) {
            throw e; // normal brigadier user errors
        } catch (Exception | LinkageError e) {
            clover.LOGGER.error("[Clover] '{}' failed", what, e);
            ctx.getSource().sendFailure(Text.msg(Config.MSG_ERROR));
            return 0;
        }
    }

    public static void run(String what, ServerPlayer notify, Runnable r) {
        try {
            r.run();
        } catch (Exception | LinkageError e) {
            clover.LOGGER.error("[Clover] '{}' failed", what, e);
            if (notify != null) notify.sendSystemMessage(Text.msg(Config.MSG_ERROR));
        }
    }
}