package com.dracolich777.clover.discord;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.request.PermissionRequest;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.Text;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Fire-and-forget Discord webhook. Never blocks the server thread, never throws, never logs the URL. */
public final class Discord {

  private static final Pattern URL = Pattern.compile(
    "^https://(?:(?:ptb|canary)\\.)?discord(?:app)?\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+/?(?:\\?.*)?$"
  );
  private static final Pattern ROLE = Pattern.compile("^\\d{5,25}$");

  private record Msg(
    String url,
    String json,
    String label,
    int max,
    Consumer<String> done
  ) {}

  private static ScheduledExecutorService pool;
  private static boolean warnedDisabled;

  private Discord() {}

  private static synchronized ScheduledExecutorService pool() {
    if (pool == null || pool.isShutdown()) {
      pool = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Clover-Discord");
        t.setDaemon(true);
        return t;
      });
    }
    return pool;
  }

  public static synchronized void shutdown() {
    if (pool != null) pool.shutdownNow();
    pool = null;
  }

  // ------------------------------------------------------------------ config state

  private static String url() {
    return Config.DISCORD_WEBHOOK_URL.get().trim();
  }

  private static boolean validUrl() {
    return URL.matcher(url()).matches();
  }

  private static String role() {
    String r = Config.DISCORD_PING_ROLE_ID.get().trim();
    return ROLE.matcher(r).matches() ? r : "";
  }

  /** Human-readable state, safe to log (never includes the URL). */
  public static String describe() {
    if (
      !Config.DISCORD_ENABLED.get()
    ) return "disabled (discord.enabled = false)";
    if (
      !validUrl()
    ) return "enabled, but webhookUrl is empty or not a Discord webhook URL";
    String raw = Config.DISCORD_PING_ROLE_ID.get().trim();
    if (raw.isEmpty()) return "enabled, role ping off (no pingRoleId)";
    if (
      role().isEmpty()
    ) return "enabled, role ping OFF (pingRoleId must be digits only, not <@&...>)";
    return "enabled, role ping on";
  }

  public static void logStatus() {
    clover.LOGGER.info("[Clover] Discord webhook: {}", describe());
  }

  private static boolean enabled() {
    if (!Config.DISCORD_ENABLED.get()) {
      if (!warnedDisabled) {
        warnedDisabled = true;
        clover.LOGGER.info(
          "[Clover] Discord webhook is disabled (discord.enabled = false); not posting"
        );
      }
      return false;
    }
    if (!validUrl()) {
      clover.LOGGER.warn("[Clover] Discord: {}", describe());
      return false;
    }
    return true;
  }

  // ------------------------------------------------------------------ messages

  public static void notifyRequest(PermissionRequest r) {
    try {
      if (!enabled()) return;
      JsonObject body = base();
      mentions(body, true, "New permission request");

      JsonObject embed = embed("Permission request", 0xF1C40F);
      JsonArray fields = new JsonArray();
      fields.add(field("Player", r.playerName(), true));
      fields.add(field("Permission", "`" + r.node() + "`", true));
      fields.add(field("Duration", Durations.formatLong(r.duration()), true));
      fields.add(
        field(
          "Reason",
          r.reason().isEmpty() ? "_none given_" : r.reason(),
          false
        )
      );
      fields.add(field("Request ID", "`" + r.code() + "`", false));
      embed.add("fields", fields);
      JsonObject footer = new JsonObject();
      footer.addProperty(
        "text",
        cut(Text.fill(Config.DISCORD_FOOTER.get(), "id", r.code()), 2000)
      );
      embed.add("footer", footer);
      attach(body, embed);
      send(body, "request " + r.code());
    } catch (Exception | LinkageError e) {
      clover.LOGGER.warn("[Clover] Discord notify failed: {}", e.toString());
    }
  }

  public static void notifyOutcome(
    PermissionRequest r,
    String status,
    String admin,
    String note
  ) {
    try {
      if (!Config.DISCORD_POST_OUTCOMES.get() || !enabled()) return;
      JsonObject body = base();
      mentions(body, false, "");

      JsonObject embed = embed(
        "Request " + r.code() + " " + status,
        switch (status) {
          case "approved" -> 0x2ECC71;
          case "denied" -> 0xE74C3C;
          default -> 0x95A5A6;
        }
      );
      JsonArray fields = new JsonArray();
      fields.add(field("Player", r.playerName(), true));
      fields.add(field("Permission", "`" + r.node() + "`", true));
      fields.add(field("Duration", Durations.formatLong(r.duration()), true));
      if (admin != null && !admin.isEmpty()) fields.add(
        field("By", admin, true)
      );
      if (note != null && !note.isEmpty()) fields.add(
        field("Note", note, false)
      );
      embed.add("fields", fields);
      attach(body, embed);
      send(body, "outcome " + r.code() + " (" + status + ")");
    } catch (Exception | LinkageError e) {
      clover.LOGGER.warn("[Clover] Discord notify failed: {}", e.toString());
    }
  }

  /** /acperm discord test: posts a test embed (with the role ping) and reports the real result. */
  public static void sendTest(
    MinecraftServer server,
    Consumer<Component> feedback
  ) {
    try {
      if (!Config.DISCORD_ENABLED.get() || !validUrl()) {
        feedback.accept(Text.color("&cDiscord: " + describe()));
        return;
      }
      JsonObject body = base();
      mentions(body, true, "Clover webhook test");
      JsonObject embed = embed("Clover webhook test", 0x3498DB);
      embed.addProperty(
        "description",
        "If you can see this, the webhook works."
      );
      attach(body, embed);

      feedback.accept(
        Text.color("&7Sending test message (" + describe() + ")...")
      );
      Msg m = new Msg(url(), body.toString(), "test message", 0, result ->
        server.execute(() ->
          feedback.accept(
            result.startsWith("OK")
              ? Text.color("&aDiscord test: " + Text.esc(result))
              : Text.color("&cDiscord test failed: " + Text.esc(result))
          )
        )
      );
      enqueue(m, 0, 0);
    } catch (Exception | LinkageError e) {
      clover.LOGGER.warn("[Clover] Discord test failed: {}", e.toString());
      feedback.accept(
        Text.color("&cDiscord test failed: " + Text.esc(e.toString()))
      );
    }
  }

  // ------------------------------------------------------------------ json helpers

  private static JsonObject base() {
    JsonObject body = new JsonObject();
    body.addProperty("username", cut(Config.DISCORD_USERNAME.get(), 80));
    return body;
  }

  /** Nothing pings unless it's the configured role. */
  private static void mentions(JsonObject body, boolean ping, String text) {
    JsonObject allowed = new JsonObject();
    allowed.add("parse", new JsonArray());
    String role = role();
    if (ping && !role.isEmpty()) {
      body.addProperty("content", "<@&" + role + "> " + text);
      JsonArray roles = new JsonArray();
      roles.add(role);
      allowed.add("roles", roles);
    }
    body.add("allowed_mentions", allowed);
  }

  private static JsonObject embed(String title, int color) {
    JsonObject e = new JsonObject();
    e.addProperty("title", cut(title, 256));
    e.addProperty("color", color);
    e.addProperty("timestamp", Instant.now().toString());
    return e;
  }

  private static void attach(JsonObject body, JsonObject embed) {
    JsonArray embeds = new JsonArray();
    embeds.add(embed);
    body.add("embeds", embeds);
  }

  private static JsonObject field(String name, String value, boolean inline) {
    JsonObject f = new JsonObject();
    f.addProperty("name", cut(name, 256));
    String v = cut(value, 1024);
    f.addProperty("value", v.isBlank() ? "\u200b" : v);
    f.addProperty("inline", inline);
    return f;
  }

  private static String cut(String s, int n) {
    return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
  }

  // ------------------------------------------------------------------ delivery

  private static void send(JsonObject body, String label) {
    // config is read here, on the calling (server) thread, then handed off
    enqueue(
      new Msg(
        url(),
        body.toString(),
        label,
        Config.DISCORD_RETRY_ATTEMPTS.get(),
        null
      ),
      0,
      0
    );
  }

  private static void enqueue(Msg m, int attempt, long delayMs) {
    try {
      pool().schedule(
        () -> attemptSend(m, attempt),
        delayMs,
        TimeUnit.MILLISECONDS
      );
    } catch (RuntimeException e) {
      clover.LOGGER.warn(
        "[Clover] Discord queue rejected {}: {}",
        m.label(),
        e.toString()
      );
      finish(m, "queue rejected: " + e);
    }
  }

  private static void finish(Msg m, String result) {
    if (m.done() == null) return;
    try {
      m.done().accept(result);
    } catch (RuntimeException ignored) {
      // never let a callback kill the Discord thread
    }
  }

  private static void attemptSend(Msg m, int attempt) {
    HttpURLConnection c = null;
    try {
      c = (HttpURLConnection) URI.create(m.url()).toURL().openConnection();
      c.setRequestMethod("POST");
      c.setConnectTimeout(5000);
      c.setReadTimeout(5000);
      c.setDoOutput(true);
      c.setRequestProperty("Content-Type", "application/json");
      c.setRequestProperty("User-Agent", "Clover-Minecraft-Mod");
      byte[] data = m.json().getBytes(StandardCharsets.UTF_8);
      c.setFixedLengthStreamingMode(data.length);
      try (OutputStream os = c.getOutputStream()) {
        os.write(data);
      }

      int code = c.getResponseCode();
      String body = readBody(
        code >= 400 ? c.getErrorStream() : c.getInputStream()
      );
      if (code >= 200 && code < 300) {
        clover.LOGGER.info(
          "[Clover] Discord: posted {} (HTTP {})",
          m.label(),
          code
        );
        finish(m, "OK (HTTP " + code + ")");
        return;
      }
      if ((code == 429 || code >= 500) && attempt < m.max()) {
        long delay = code == 429 ? retryAfterMs(c) : backoff(attempt);
        clover.LOGGER.warn(
          "[Clover] Discord returned HTTP {} for {}; retrying in {}ms",
          code,
          m.label(),
          delay
        );
        enqueue(m, attempt + 1, delay);
        return;
      }
      clover.LOGGER.error(
        "[Clover] Discord rejected {} (HTTP {}): {}",
        m.label(),
        code,
        body
      );
      finish(m, "HTTP " + code + (body.isEmpty() ? "" : ": " + body));
    } catch (IOException | RuntimeException e) {
      if (attempt < m.max()) {
        long delay = backoff(attempt);
        clover.LOGGER.warn(
          "[Clover] Discord send of {} failed ({}); retrying in {}ms",
          m.label(),
          e.toString(),
          delay
        );
        enqueue(m, attempt + 1, delay);
      } else {
        clover.LOGGER.error(
          "[Clover] Discord send of {} failed permanently: {}",
          m.label(),
          e.toString()
        );
        finish(m, e.toString());
      }
    } finally {
      if (c != null) c.disconnect();
    }
  }

  private static long backoff(int attempt) {
    return Math.min(30000L, 2000L << Math.min(attempt, 4));
  }

  private static long retryAfterMs(HttpURLConnection c) {
    String h = c.getHeaderField("Retry-After");
    if (h != null) {
      try {
        return Math.min(30000L, (long) (Double.parseDouble(h) * 1000));
      } catch (NumberFormatException ignored) {}
    }
    return 2000L;
  }

  /** Reads (and caps) the response so the connection is cleanly reusable and errors are visible. */
  private static String readBody(InputStream in) {
    if (in == null) return "";
    try (in) {
      String s = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
      return s.length() > 500 ? s.substring(0, 500) : s;
    } catch (IOException e) {
      return "";
    }
  }
}
