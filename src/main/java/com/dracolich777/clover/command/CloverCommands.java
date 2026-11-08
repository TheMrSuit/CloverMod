package com.dracolich777.clover.command;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.backend.PermissionBackend;
import com.dracolich777.clover.catalog.CatalogEntry;
import com.dracolich777.clover.catalog.CatalogManager;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.gui.GuiService;
import com.dracolich777.clover.request.PermissionRequest;
import com.dracolich777.clover.request.RequestManager;
import com.dracolich777.clover.request.RequestService;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.Safe;
import com.dracolich777.clover.util.Text;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class CloverCommands {

  private CloverCommands() {}

  public static void register(CommandDispatcher<CommandSourceStack> d) {
    d.register(requestRoot()); // /rqperm  (text only)
    d.register(adminRoot()); // /acperm  (text only)
    d.register(cloverRoot()); // /clover  (opens the GUI that fits the player)
  }

  // ------------------------------------------------------------ permission predicates

  private static boolean isStaff(CommandSourceStack s) {
    try {
      if (!(s.getEntity() instanceof ServerPlayer p)) return false;
      PermissionBackend b = Backends.get();
      return b != null && b.isStaff(p, Config.STAFF_GROUPS.get());
    } catch (Exception | LinkageError e) {
      return false;
    }
  }

  /** Console/RCON/Discord console (no entity) counts as admin; players need the LuckPerms admin node. */
  private static boolean isAdmin(CommandSourceStack s) {
    try {
      if (s.getEntity() == null) return true;
      return (
        s.getEntity() instanceof ServerPlayer p && RequestService.isAdmin(p)
      );
    } catch (Exception | LinkageError e) {
      return false;
    }
  }

  // ------------------------------------------------------------ suggestions

  /** One greedy argument: "<permission> <duration> <reason...>". Suggest per position. */
  private static final SuggestionProvider<
    CommandSourceStack
  > REQUEST_SUGGESTIONS = (c, b) -> {
    try {
      if (
        !(c.getSource().getEntity() instanceof ServerPlayer p)
      ) return b.buildFuture();
      String rem = b.getRemaining();
      int sp = rem.indexOf(' ');
      if (sp < 0) {
        return SharedSuggestionProvider.suggest(
          CatalogManager.visibleTo(p)
            .stream()
            .map(e -> e.node),
          b
        );
      }
      String node = rem.substring(0, sp);
      if (rem.indexOf(' ', sp + 1) < 0) {
        // typing the duration
        for (CatalogEntry e : CatalogManager.visibleTo(p)) {
          if (e.node.equalsIgnoreCase(node)) {
            SuggestionsBuilder at = b.createOffset(b.getStart() + sp + 1);
            return SharedSuggestionProvider.suggest(e.durations, at);
          }
        }
      }
    } catch (Exception | LinkageError e) {
      clover.LOGGER.debug("[Clover] suggestion failed", e);
    }
    return b.buildFuture();
  };

  private static final SuggestionProvider<CommandSourceStack> PENDING_IDS = (
    c,
    b
  ) -> {
    try {
      String rem = b.getRemainingLowerCase();
      for (PermissionRequest r : RequestManager.get().all()) {
        if (r.code().toLowerCase().startsWith(rem)) {
          b.suggest(
            r.code(),
            Component.literal(
              r.playerName() +
                " -> " +
                r.node() +
                " (" +
                Durations.format(r.duration()) +
                ")"
            )
          );
        }
      }
    } catch (Exception | LinkageError e) {
      clover.LOGGER.debug("[Clover] suggestion failed", e);
    }
    return b.buildFuture();
  };

  // ------------------------------------------------------------ /rqperm

  private static LiteralArgumentBuilder<CommandSourceStack> requestRoot() {
    return Commands.literal("rqperm")
      .requires(s -> isStaff(s) || isAdmin(s))
      .executes(c -> {
        c.getSource().sendSuccess(() -> Text.msg(Config.MSG_USAGE), false);
        return 1;
      })
      .then(
        Commands.literal("list")
          .requires(CloverCommands::isStaff)
          .executes(c ->
            Safe.cmd(c, "list permissions", () -> {
              ServerPlayer p = c.getSource().getPlayerOrException();
              List<CatalogEntry> list = CatalogManager.visibleTo(p);
              if (list.isEmpty()) {
                c.getSource().sendFailure(Text.msg(Config.MSG_NONE_AVAILABLE));
                return 0;
              }
              for (CatalogEntry e : list) {
                String line =
                  "&f" +
                  e.node +
                  " &7(max " +
                  Durations.format(e.max()) +
                  ")" +
                  (e.description.isBlank() ? "" : " &8- &7" + e.description);
                c.getSource().sendSuccess(() -> Text.color(line), false);
              }
              return list.size();
            })
          )
      )
      .then(
        Commands.argument("request", StringArgumentType.greedyString())
          .requires(CloverCommands::isStaff)
          .suggests(REQUEST_SUGGESTIONS)
          .executes(c ->
            Safe.cmd(c, "request", () -> {
              ServerPlayer p = c.getSource().getPlayerOrException();
              String[] t = StringArgumentType.getString(c, "request")
                .trim()
                .split("\\s+", 3);
              if (t.length < 2) {
                c.getSource().sendFailure(Text.msg(Config.MSG_USAGE));
                return 0;
              }
              return RequestService.submit(
                  p,
                  t[0],
                  t[1],
                  t.length > 2 ? t[2] : ""
                )
                ? 1
                : 0;
            })
          )
      );
  }

  // ------------------------------------------------------------ /acperm

  private static LiteralArgumentBuilder<CommandSourceStack> adminRoot() {
    return Commands.literal("acperm")
      .requires(CloverCommands::isAdmin)
      .executes(c -> Safe.cmd(c, "list pending", () -> listPending(c)))
      .then(decision("accept", true))
      .then(decision("deny", false))
      .then(
        Commands.literal("list").executes(c ->
          Safe.cmd(c, "list pending", () -> listPending(c))
        )
      )
      .then(
        Commands.literal("reload").executes(c ->
          Safe.cmd(c, "reload", () -> reload(c))
        )
      );
  }

  private static LiteralArgumentBuilder<CommandSourceStack> decision(
    String literal,
    boolean approve
  ) {
    return Commands.literal(literal).then(
      Commands.argument("id", StringArgumentType.word())
        .suggests(PENDING_IDS)
        .executes(c -> decide(c, approve, ""))
        .then(
          Commands.argument("note", StringArgumentType.greedyString()).executes(
            c -> decide(c, approve, StringArgumentType.getString(c, "note"))
          )
        )
    );
  }

  private static int decide(
    CommandContext<CommandSourceStack> c,
    boolean approve,
    String note
  ) throws CommandSyntaxException {
    return Safe.cmd(c, approve ? "accept" : "deny", () -> {
      CommandSourceStack src = c.getSource();
      String id = StringArgumentType.getString(c, "id");
      UUID approverId =
        src.getEntity() != null ? src.getEntity().getUUID() : null;
      Consumer<Component> fb = m -> src.sendSuccess(() -> m, false);
      if (approve) RequestService.approve(
        src.getServer(),
        id,
        approverId,
        src.getTextName(),
        note,
        fb
      );
      else RequestService.deny(
        src.getServer(),
        id,
        src.getTextName(),
        note,
        fb
      );
      return 1;
    });
  }

  private static int listPending(CommandContext<CommandSourceStack> c) {
    List<PermissionRequest> all = RequestManager.get().all();
    if (all.isEmpty()) {
      c
        .getSource()
        .sendSuccess(() -> Text.color("&7No pending requests."), false);
      return 0;
    }
    for (PermissionRequest r : all) {
      String line =
        "&e" +
        r.code() +
        " &8- &f" +
        r.playerName() +
        " &7-> &f" +
        r.node() +
        " &7(" +
        Durations.format(r.duration()) +
        ") &8- &7" +
        Text.esc(r.reason());
      c.getSource().sendSuccess(() -> Text.color(line), false);
    }
    return all.size();
  }

  // ------------------------------------------------------------ /clover

  private static LiteralArgumentBuilder<CommandSourceStack> cloverRoot() {
    return Commands.literal("clover")
      .requires(s -> isStaff(s) || isAdmin(s))
      .executes(c ->
        Safe.cmd(c, "open menu", () -> {
          GuiService.openAuto(c.getSource().getPlayerOrException());
          return 1;
        })
      )
      .then(
        Commands.literal("request")
          .requires(CloverCommands::isStaff)
          .executes(c ->
            Safe.cmd(c, "open request menu", () -> {
              GuiService.openRequest(c.getSource().getPlayerOrException());
              return 1;
            })
          )
      )
      .then(
        Commands.literal("admin")
          .requires(CloverCommands::isAdmin)
          .executes(c ->
            Safe.cmd(c, "open admin menu", () -> {
              GuiService.openAdmin(c.getSource().getPlayerOrException());
              return 1;
            })
          )
      )
      .then(
        Commands.literal("reload")
          .requires(CloverCommands::isAdmin)
          .executes(c -> Safe.cmd(c, "reload", () -> reload(c)))
      );
  }

  private static int reload(CommandContext<CommandSourceStack> c) {
    int n = CatalogManager.load();
    if (n < 0) {
      c.getSource().sendFailure(Text.msg(Config.MSG_ERROR));
      return 0;
    }
    c
      .getSource()
      .sendSuccess(
        () -> Text.msg(Config.MSG_RELOADED, "max", String.valueOf(n)),
        false
      );
    return n;
  }
}
