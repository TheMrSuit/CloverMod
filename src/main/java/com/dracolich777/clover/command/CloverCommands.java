package com.dracolich777.clover.command;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.ConfigService;
import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.backend.PermissionBackend;
import com.dracolich777.clover.catalog.CatalogEntry;
import com.dracolich777.clover.catalog.CatalogManager;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.discord.Discord;
import com.dracolich777.clover.gui.GuiService;
import com.dracolich777.clover.request.PermissionRequest;
import com.dracolich777.clover.request.RequestManager;
import com.dracolich777.clover.request.RequestService;
import com.dracolich777.clover.util.Compat;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class CloverCommands {

  private CloverCommands() {}

  public static void register(CommandDispatcher<CommandSourceStack> d) {
    d.register(requestRoot()); // /rqperm  (text only)
    d.register(adminRoot()); // /acperm  (text only)
    d.register(cloverRoot()); // /clover  (menu + admin config)
  }

  // ------------------------------------------------------------ permission predicates

  private static boolean isStaff(CommandSourceStack s) {
    try {
      if (!(s.getEntity() instanceof ServerPlayer p)) return false;
      return RequestService.isStaff(p);
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

  private static boolean visible(ServerPlayer p, CatalogEntry e) {
    if (e.allowedGroups.isEmpty()) return true;
    PermissionBackend b = Backends.get();
    return b != null && b.isStaff(p, e.allowedGroups);
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
        // typing the permission: best 50 matches, with the plain name as the tooltip
        for (CatalogEntry e : CatalogManager.search(p, rem, 50).entries()) {
          b.suggest(e.node, Component.literal(Text.strip(e.title())));
        }
        return b.buildFuture();
      }
      String node = rem.substring(0, sp);
      if (rem.indexOf(' ', sp + 1) < 0) {
        // typing the duration
        CatalogEntry e = CatalogManager.get(node);
        if (e != null && visible(p, e)) {
          SuggestionsBuilder at = b.createOffset(b.getStart() + sp + 1);
          return SharedSuggestionProvider.suggest(e.durations, at);
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

  private static final SuggestionProvider<CommandSourceStack> CONFIG_KEYS = (
    c,
    b
  ) -> SharedSuggestionProvider.suggest(Config.keys().keySet(), b);

  private static final SuggestionProvider<CommandSourceStack> CONFIG_SECTIONS =
    (c, b) -> {
      Set<String> sections = new HashSet<>();
      for (String path : Config.keys().keySet()) {
        int dot = path.indexOf('.');
        if (dot > 0) sections.add(path.substring(0, dot));
      }
      return SharedSuggestionProvider.suggest(sections, b);
    };

  private static final SuggestionProvider<CommandSourceStack> CONFIG_VALUES = (
    c,
    b
  ) -> {
    try {
      Config.Key k = Config.keys().get(StringArgumentType.getString(c, "key"));
      if (k != null && !k.secret()) {
        if (k.value() instanceof ModConfigSpec.BooleanValue) {
          return SharedSuggestionProvider.suggest(List.of("true", "false"), b);
        }
        if (k.value() instanceof ModConfigSpec.EnumValue<?> ev) {
          for (Object o : (
            (Enum<?>) ev.getDefault()
          ).getDeclaringClass().getEnumConstants())
            b.suggest(o.toString());
        } else {
          String cur = ConfigService.display(k);
          if (cur.length() < 60) b.suggest(cur);
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
          .requires(s -> isStaff(s) || isAdmin(s))
          .executes(c ->
            Safe.cmd(c, "list permissions", () -> showResults(c, ""))
          )
      )
      .then(
        Commands.literal("search")
          .requires(s -> isStaff(s) || isAdmin(s))
          .then(
            Commands.argument(
              "query",
              StringArgumentType.greedyString()
            ).executes(c ->
              Safe.cmd(c, "search permissions", () ->
                showResults(c, StringArgumentType.getString(c, "query"))
              )
            )
          )
      )
      .then(
        Commands.argument("request", StringArgumentType.greedyString())
          .requires(s -> isStaff(s) || isAdmin(s))
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

  private static int showResults(
    CommandContext<CommandSourceStack> c,
    String query
  ) throws CommandSyntaxException {
    ServerPlayer p = c.getSource().getPlayerOrException();
    CatalogManager.SearchResult r = CatalogManager.search(p, query, 10);
    if (r.entries().isEmpty()) {
      c.getSource().sendFailure(Text.msg(Config.MSG_NONE_AVAILABLE));
      return 0;
    }
    for (CatalogEntry e : r.entries()) {
      String node = e.node;
      c
        .getSource()
        .sendSuccess(
          () ->
            Text.color(
              "&f" +
                e.title() +
                " &8- &7" +
                node +
                " &8(max " +
                Durations.format(e.max()) +
                ")"
            ).withStyle(s -> Compat.suggest(s, "/rqperm " + node + " ")),
          false
        ); // click to start a request
    }
    if (r.total() > r.entries().size()) {
      int shown = r.entries().size();
      c
        .getSource()
        .sendSuccess(
          () ->
            Text.color(
              "&7Showing " +
                shown +
                " of " +
                r.total() +
                ". Narrow it down with &f/rqperm search <text>&7."
            ),
          false
        );
    }
    return r.entries().size();
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
      )
      .then(
        Commands.literal("discord").then(
          Commands.literal("test").executes(c ->
            Safe.cmd(c, "discord test", () -> {
              CommandSourceStack src = c.getSource();
              Discord.sendTest(src.getServer(), m ->
                src.sendSuccess(() -> m, false)
              );
              return 1;
            })
          )
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
          .requires(s -> isStaff(s) || isAdmin(s))
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
        Commands.literal("info")
          .requires(s -> isStaff(s) || isAdmin(s))
          .executes(c ->
            Safe.cmd(c, "open permission info", () -> {
              GuiService.openInfo(c.getSource().getPlayerOrException());
              return 1;
            })
          )
      )
      .then(
        Commands.literal("reload")
          .requires(CloverCommands::isAdmin)
          .executes(c -> Safe.cmd(c, "reload", () -> reload(c)))
      )
      .then(
        Commands.literal("config")
          .requires(CloverCommands::isAdmin)
          .executes(c -> {
            c
              .getSource()
              .sendSuccess(
                () ->
                  Text.color(
                    "&7Server config: &f/clover config list [section]&7, " +
                      "&f/clover config get <key>&7, &f/clover config set <key> <value>"
                  ),
                false
              );
            return 1;
          })
          .then(
            Commands.literal("list")
              .executes(c ->
                Safe.cmd(c, "config list", () -> listConfig(c, ""))
              )
              .then(
                Commands.argument("section", StringArgumentType.word())
                  .suggests(CONFIG_SECTIONS)
                  .executes(c ->
                    Safe.cmd(c, "config list", () ->
                      listConfig(c, StringArgumentType.getString(c, "section"))
                    )
                  )
              )
          )
          .then(
            Commands.literal("get").then(
              Commands.argument("key", StringArgumentType.word())
                .suggests(CONFIG_KEYS)
                .executes(c ->
                  Safe.cmd(c, "config get", () -> {
                    Config.Key k = Config.keys().get(
                      StringArgumentType.getString(c, "key")
                    );
                    if (k == null) {
                      c
                        .getSource()
                        .sendFailure(
                          Text.color(
                            "&cUnknown setting. Try /clover config list"
                          )
                        );
                      return 0;
                    }
                    c
                      .getSource()
                      .sendSuccess(
                        () ->
                          Text.color(
                            "&e" +
                              k.path() +
                              " &7= &f" +
                              Text.esc(ConfigService.display(k))
                          ),
                        false
                      );
                    return 1;
                  })
                )
            )
          )
          .then(
            Commands.literal("set").then(
              Commands.argument("key", StringArgumentType.word())
                .suggests(CONFIG_KEYS)
                .then(
                  Commands.argument("value", StringArgumentType.greedyString())
                    .suggests(CONFIG_VALUES)
                    .executes(c ->
                      Safe.cmd(c, "config set", () -> {
                        String key = StringArgumentType.getString(c, "key");
                        String value = StringArgumentType.getString(c, "value");
                        try {
                          String shown = ConfigService.set(key, value);
                          c
                            .getSource()
                            .sendSuccess(
                              () ->
                                Text.color(
                                  "&aSet &e" +
                                    key +
                                    " &ato &f" +
                                    Text.esc(shown)
                                ),
                              false
                            );
                          return 1;
                        } catch (IllegalArgumentException e) {
                          c
                            .getSource()
                            .sendFailure(
                              Text.color(
                                "&c" + Text.esc(String.valueOf(e.getMessage()))
                              )
                            );
                          return 0;
                        }
                      })
                    )
                )
            )
          )
      );
  }

  private static int listConfig(
    CommandContext<CommandSourceStack> c,
    String prefix
  ) {
    int n = 0;
    for (Config.Key k : Config.keys().values()) {
      if (!prefix.isEmpty() && !k.path().startsWith(prefix)) continue;
      if (++n > 40) {
        c
          .getSource()
          .sendSuccess(
            () -> Text.color("&7...more; narrow it with a section name"),
            false
          );
        break;
      }
      c
        .getSource()
        .sendSuccess(
          () ->
            Text.color(
              "&e" + k.path() + " &7= &f" + Text.esc(ConfigService.display(k))
            ),
          false
        );
    }
    return n;
  }

  private static int reload(CommandContext<CommandSourceStack> c) {
    int n = ConfigService.apply();
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
