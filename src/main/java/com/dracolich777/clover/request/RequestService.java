package com.dracolich777.clover.request;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.backend.PermissionBackend;
import com.dracolich777.clover.catalog.CatalogEntry;
import com.dracolich777.clover.catalog.CatalogManager;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.discord.Discord;
import com.dracolich777.clover.util.Compat;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.Text;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class RequestService {
  private static final Map<UUID, Map<String, Long>> expiryWarnings = new HashMap<>();
  private static final Map<UUID, Map<String, Long>> knownTemporaryPermissions = new HashMap<>();

  private RequestService() {}

  public static boolean isStaff(ServerPlayer p) {
    PermissionBackend b = Backends.get();
    return b != null && b.hasPermission(p, PermissionBackend.REQUEST_PERMISSION);
  }

  public static boolean isAdmin(ServerPlayer p) {
    PermissionBackend b = Backends.get();
    return b != null && b.hasPermission(p, PermissionBackend.ADMIN_PERMISSION);
  }

  /** Strips control characters and the section sign, collapses whitespace. */
  public static String clean(String s) {
    if (s == null) return "";
    return s
      .replace('\u00a7', ' ')
      .replaceAll("\\p{Cntrl}", " ")
      .replaceAll("\\s+", " ")
      .trim();
  }

  private static String note(String n) {
    return (n == null || n.isEmpty())
      ? ""
      : Text.fill(Config.NOTE_FORMAT.get(), "note", Text.esc(n));
  }

  /** All placeholders; player-supplied text is escaped. */
  private static String[] kv(
    PermissionRequest r,
    String admin,
    String noteText
  ) {
    return new String[] {
      "id",
      r.code(),
      "player",
      r.playerName(),
      "permission",
      r.node(),
      "duration",
      Durations.formatLong(r.duration()),
      "reason",
      Text.esc(r.reason()),
      "admin",
      Text.esc(admin),
      "note",
      note(noteText),
    };
  }

  // ------------------------------------------------------------------ submit

  public static boolean submit(
    ServerPlayer p,
    String nodeInput,
    String durationStr,
    String rawReason
  ) {
    PermissionBackend b = Backends.get();
    if (b == null) {
      p.sendSystemMessage(Text.msg(Config.MSG_BACKEND_DOWN));
      return false;
    }
    if (!isStaff(p)) {
      p.sendSystemMessage(Text.msg(Config.MSG_NOT_STAFF));
      return false;
    }

    CatalogEntry e = CatalogManager.get(nodeInput);
    if (e == null) {
      p.sendSystemMessage(Text.msg(Config.MSG_UNKNOWN_PERMISSION));
      return false;
    }
    if (!e.allowedGroups.isEmpty() && !b.isStaff(p, e.allowedGroups)) {
      p.sendSystemMessage(Text.msg(Config.MSG_NOT_ALLOWED));
      return false;
    }
    Duration d = e.allowedDuration(durationStr);
    if (d == null) {
      p.sendSystemMessage(
        Text.msg(Config.MSG_INVALID_DURATION, "max", Durations.format(e.max()))
      );
      return false;
    }
    String reason = clean(rawReason);
    if (reason.isEmpty() && Config.REQUIRE_REASON.get()) {
      p.sendSystemMessage(Text.msg(Config.MSG_REASON_REQUIRED));
      return false;
    }
    int maxLen = Config.MAX_REASON_LENGTH.get();
    if (reason.length() > maxLen) {
      p.sendSystemMessage(
        Text.msg(Config.MSG_REASON_TOO_LONG, "max", String.valueOf(maxLen))
      );
      return false;
    }

    long now = System.currentTimeMillis();
    RequestManager.Outcome o = RequestManager.get().submit(
      p.getUUID(),
      p.getName().getString(),
      e.node,
      d,
      reason,
      Config.MAX_PENDING.get(),
      Config.COOLDOWN_SECONDS.get() * 1000L,
      now
    );
    if (o.result() == RequestManager.Result.COOLDOWN) {
      p.sendSystemMessage(Text.msg(Config.MSG_COOLDOWN));
      return false;
    }
    if (o.result() == RequestManager.Result.TOO_MANY) {
      p.sendSystemMessage(Text.msg(Config.MSG_TOO_MANY));
      return false;
    }

    PermissionRequest r = o.request();
    p.sendSystemMessage(Text.msg(Config.MSG_REQUEST_SENT, kv(r, "", "")));
    clover.LOGGER.info(
      "[Clover] {} requested '{}' for {} because \"{}\" - ID {}",
      r.playerName(),
      r.node(),
      Durations.format(d),
      r.reason(),
      r.code()
    );
    notifyAdmins(p.getServer(), r, b);
    Discord.notifyRequest(r);
    return true;
  }

  private static void notifyAdmins(
    MinecraftServer server,
    PermissionRequest r,
    PermissionBackend b
  ) {
    if (!Config.NOTIFY_ADMINS.get() || server == null) return;
    String code = r.code();
    MutableComponent line = Text.msg(Config.MSG_ADMIN_NOTIFY, kv(r, "", ""));
    line
      .append(Component.literal(" "))
      .append(
        Text.color("&a[Accept]").withStyle(s ->
          Compat.run(s, "/acperm accept " + code)
        )
      )
      .append(Component.literal(" "))
      .append(
        Text.color("&c[Deny]").withStyle(s ->
          Compat.suggest(s, "/acperm deny " + code + " ")
        )
      )
      .append(Component.literal(" "))
      .append(
        Text.color("&8[&e" + code + "&8]").withStyle(s -> Compat.copy(s, code))
      );
    for (ServerPlayer a : server.getPlayerList().getPlayers()) {
      if (
        !Config.ALLOW_SELF_APPROVAL.get() && a.getUUID().equals(r.player())
      ) continue;
      if (
        b.hasPermission(a, PermissionBackend.ADMIN_PERMISSION)
      ) a.sendSystemMessage(line);
    }
  }

  // ------------------------------------------------------------------ approve / deny

  private record Job(
    PermissionBackend backend,
    PermissionRequest req,
    Config.MergeMode merge,
    int max,
    long delayMs,
    MinecraftServer server,
    Runnable ok,
    Consumer<Throwable> fail
  ) {}

  /** @param approverId null for console/RCON/Discord console. */
  public static void approve(
    MinecraftServer server,
    String code,
    UUID approverId,
    String approver,
    String noteText,
    Consumer<Component> feedback
  ) {
    RequestManager rm = RequestManager.get();
    String id = code.toUpperCase(Locale.ROOT);
    PermissionRequest peek = rm.get(id);
    if (peek == null) {
      feedback.accept(Text.msg(Config.MSG_NO_PENDING, "id", id));
      return;
    }
    if (!Config.ALLOW_SELF_APPROVAL.get() && peek.player().equals(approverId)) {
      feedback.accept(Text.msg(Config.MSG_SELF_APPROVAL));
      return;
    }
    PermissionBackend b = Backends.get();
    if (b == null) {
      feedback.accept(Text.msg(Config.MSG_BACKEND_DOWN));
      return;
    }
    PermissionRequest r = rm.take(id); // removed first so a double-approve can't double-grant
    if (r == null) {
      feedback.accept(Text.msg(Config.MSG_NO_PENDING, "id", id));
      return;
    }

    String n = clean(noteText);
    String[] args = kv(r, approver, n);
    Job job = new Job(
      b,
      r,
      Config.MERGE_MODE.get(),
      Config.LP_RETRY_ATTEMPTS.get(),
      Config.LP_RETRY_DELAY_MS.get(),
      server,
      () -> {
        // success, server thread
        feedback.accept(Text.msg(Config.MSG_APPROVED_ADMIN, args));
        ServerPlayer tp = server.getPlayerList().getPlayer(r.player());
        if (tp != null) tp.sendSystemMessage(
          Text.msg(Config.MSG_APPROVED_PLAYER, args)
        );
        clover.LOGGER.info(
          "[Clover] {} approved {} ('{}' for {} -> {})",
          approver,
          r.code(),
          r.node(),
          Durations.format(r.duration()),
          r.playerName()
        );
        Discord.notifyOutcome(r, "approved", approver, n);
      },
      t -> {
        // final failure, server thread
        rm.restore(r);
        clover.LOGGER.error(
          "[Clover] Granting '{}' to {} failed permanently; request {} restored",
          r.node(),
          r.playerName(),
          r.code(),
          t
        );
        feedback.accept(Text.msg(Config.MSG_GRANT_FAILED, args));
      }
    );
    grant(job, 0);
  }

  private static void grant(Job j, int attempt) {
    try {
      j
        .backend()
        .grant(j.req().player(), j.req().node(), j.req().duration(), j.merge())
        .whenComplete((v, t) -> {
          if (t == null) j.server().execute(j.ok());
          else retry(j, attempt, t);
        });
    } catch (Exception | LinkageError t) {
      retry(j, attempt, t);
    }
  }

  private static void retry(Job j, int attempt, Throwable t) {
    if (attempt < j.max()) {
      clover.LOGGER.warn(
        "[Clover] Grant attempt {}/{} failed ({}); retrying",
        attempt + 1,
        j.max() + 1,
        t.toString()
      );
      CompletableFuture.delayedExecutor(
        j.delayMs(),
        TimeUnit.MILLISECONDS
      ).execute(() -> grant(j, attempt + 1));
    } else {
      j.server().execute(() -> j.fail().accept(t));
    }
  }

  public static void deny(
    MinecraftServer server,
    String code,
    String admin,
    String noteText,
    Consumer<Component> feedback
  ) {
    String id = code.toUpperCase(Locale.ROOT);
    PermissionRequest r = RequestManager.get().take(id);
    if (r == null) {
      feedback.accept(Text.msg(Config.MSG_NO_PENDING, "id", id));
      return;
    }
    String n = clean(noteText);
    String[] args = kv(r, admin, n);
    feedback.accept(Text.msg(Config.MSG_DENIED_ADMIN, args));
    ServerPlayer tp = server.getPlayerList().getPlayer(r.player());
    if (tp != null) tp.sendSystemMessage(
      Text.msg(Config.MSG_DENIED_PLAYER, args)
    );
    clover.LOGGER.info(
      "[Clover] {} denied {} ('{}' -> {}){}",
      admin,
      r.code(),
      r.node(),
      r.playerName(),
      n.isEmpty() ? "" : ": " + n
    );
    Discord.notifyOutcome(r, "denied", admin, n);
  }

  public static void sweep(MinecraftServer server) {
    RequestManager rm = RequestManager.get();
    if (!rm.isEmpty()) {
      long cutoff =
        System.currentTimeMillis() - Config.REQUEST_TIMEOUT_SECONDS.get() * 1000L;
      for (PermissionRequest r : rm.expire(cutoff)) {
        ServerPlayer p = server.getPlayerList().getPlayer(r.player());
        if (p != null) p.sendSystemMessage(
          Text.msg(Config.MSG_EXPIRED, kv(r, "", ""))
        );
        Discord.notifyOutcome(r, "expired", "", "");
      }
    }
    warnAboutExpiringPermissions(server);
  }

  private static void warnAboutExpiringPermissions(MinecraftServer server) {
    int warningSeconds = Config.PERMISSION_EXPIRY_WARNING_SECONDS.get();
    if (warningSeconds <= 0) {
      expiryWarnings.clear();
    }
    PermissionBackend backend = Backends.get();
    if (backend == null) return;
    long now = System.currentTimeMillis();
    long warningMs = warningSeconds * 1000L;
    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
      Map<String, Long> warned = expiryWarnings.computeIfAbsent(
        player.getUUID(), ignored -> new HashMap<>()
      );
      Map<String, Long> current = new HashMap<>();
      for (PermissionBackend.TemporaryPermission permission : backend.temporaryPermissions(player)) {
        current.put(permission.node(), permission.expiresAtMs());
      }
      Map<String, Long> known = knownTemporaryPermissions.computeIfAbsent(
        player.getUUID(), ignored -> new HashMap<>()
      );
      for (String node : known.keySet()) {
        if (!current.containsKey(node)) {
          player.sendSystemMessage(Text.msg(
            Config.MSG_PERMISSION_LOST,
            "permission", node
          ));
          warned.remove(node);
        }
      }
      known.clear();
      known.putAll(current);
      for (Map.Entry<String, Long> entry : current.entrySet()) {
        String node = entry.getKey();
        long expiry = entry.getValue();
        long remaining = expiry - now;
        if (remaining > 0 && remaining <= warningMs &&
            !Long.valueOf(expiry).equals(warned.get(node))) {
          warned.put(node, expiry);
          player.sendSystemMessage(Text.msg(
            Config.MSG_PERMISSION_EXPIRING,
            "permission", node,
            "time", Durations.format(Duration.ofMillis(remaining))
          ));
        }
      }
      warned.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
    Iterator<UUID> ids = expiryWarnings.keySet().iterator();
    while (ids.hasNext()) {
      UUID id = ids.next();
      if (server.getPlayerList().getPlayer(id) == null) {
        ids.remove();
        knownTemporaryPermissions.remove(id);
      }
    }
    knownTemporaryPermissions.keySet().removeIf(
      id -> server.getPlayerList().getPlayer(id) == null
    );
  }
}
