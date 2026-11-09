package com.dracolich777.clover.gui;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.catalog.CatalogEntry;
import com.dracolich777.clover.catalog.CatalogManager;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.net.SyncPayload;
import com.dracolich777.clover.net.SyncPayload.EntryDto;
import com.dracolich777.clover.net.SyncPayload.PendingDto;
import com.dracolich777.clover.request.PermissionRequest;
import com.dracolich777.clover.request.RequestManager;
import com.dracolich777.clover.request.RequestService;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens the right screen for the right person and keeps open screens fresh. Server thread only. */
public final class GuiService {

  private record Viewer(byte mode, String query) {}

  private static final Map<UUID, Viewer> viewers = new HashMap<>();
  private static final Map<UUID, Long> lastSearch = new HashMap<>();
  private static int lastVersion = -1;
  private static int ticks;

  private GuiService() {}

  /** /clover: admins get the pending-requests screen, staff get the request screen. */
  public static void openAuto(ServerPlayer p) {
    if (RequestService.isAdmin(p)) open(p, SyncPayload.MODE_ADMIN);
    else if (RequestService.isStaff(p)) open(p, SyncPayload.MODE_REQUEST);
    else p.sendSystemMessage(Text.msg(Config.MSG_NOT_STAFF));
  }

  public static void openRequest(ServerPlayer p) {
    if (!RequestService.isStaff(p)) {
      p.sendSystemMessage(Text.msg(Config.MSG_NOT_STAFF));
      return;
    }
    open(p, SyncPayload.MODE_REQUEST);
  }

  public static void openAdmin(ServerPlayer p) {
    if (!RequestService.isAdmin(p)) {
      p.sendSystemMessage(Text.msg(Config.MSG_NOT_ALLOWED));
      return;
    }
    open(p, SyncPayload.MODE_ADMIN);
  }

  private static void open(ServerPlayer p, byte mode) {
    if (!hasClientGui(p)) {
      p.sendSystemMessage(Text.msg(Config.MSG_NO_CLIENT));
      return;
    }
    Viewer v = new Viewer(mode, "");
    if (send(p, v, true)) viewers.put(p.getUUID(), v);
  }

  private static boolean hasClientGui(ServerPlayer p) {
    try {
      return p.connection.hasChannel(SyncPayload.TYPE);
    } catch (Exception | LinkageError e) {
      return false;
    }
  }

  private static boolean send(ServerPlayer p, Viewer v, boolean open) {
    try {
      PacketDistributor.sendToPlayer(p, snapshot(p, v, open));
      return true;
    } catch (Exception | LinkageError e) {
      clover.LOGGER.error(
        "[Clover] Failed to send menu data to {}",
        p.getName().getString(),
        e
      );
      viewers.remove(p.getUUID());
      return false;
    }
  }

  /** Immediate refresh for a player who has a screen open (after their own action). */
  public static void resend(ServerPlayer p) {
    Viewer v = viewers.get(p.getUUID());
    if (v != null) send(p, v, false);
  }

  /** The request screen's search box. */
  public static void search(ServerPlayer p, String rawQuery) {
    Viewer v = viewers.get(p.getUUID());
    if (v == null || v.mode() != SyncPayload.MODE_REQUEST) return;
    long now = System.nanoTime();
    Long last = lastSearch.get(p.getUUID());
    if (last != null && now - last < 100_000_000L) return; // the client debounces at 250ms; this just drops spam
    lastSearch.put(p.getUUID(), now);
    Viewer nv = new Viewer(v.mode(), normalize(rawQuery));
    viewers.put(p.getUUID(), nv);
    send(p, nv, false);
  }

  public static String normalize(String s) {
    String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
    return t.length() > 64 ? t.substring(0, 64) : t;
  }

  public static void closed(ServerPlayer p, byte mode) {
    Viewer cur = viewers.get(p.getUUID());
    if (cur != null && cur.mode() == mode) viewers.remove(p.getUUID());
  }

  public static void forget(UUID id) {
    viewers.remove(id);
    lastSearch.remove(id);
  }

  public static void clear() {
    viewers.clear();
    lastSearch.clear();
    lastVersion = -1;
  }

  /** Returns immediately with no viewers; otherwise one int compare every 5 ticks. */
  public static void tick(MinecraftServer server) {
    if (viewers.isEmpty() || ++ticks % 5 != 0) return;
    int v = RequestManager.get().version();
    if (v == lastVersion) return;
    lastVersion = v;
    Iterator<Map.Entry<UUID, Viewer>> it = viewers.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<UUID, Viewer> en = it.next();
      ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
      if (p == null || !send(p, en.getValue(), false)) it.remove();
    }
  }

  // ------------------------------------------------------------ snapshot

  private static SyncPayload snapshot(ServerPlayer p, Viewer v, boolean open) {
    RequestManager rm = RequestManager.get();
    List<EntryDto> entries = new ArrayList<>();
    List<PendingDto> pending = new ArrayList<>();
    int total = 0;
    if (v.mode() == SyncPayload.MODE_REQUEST) {
      List<PermissionRequest> mine = new ArrayList<>();
      for (PermissionRequest r : rm.all())
        if (r.player().equals(p.getUUID())) mine.add(r);
      CatalogManager.SearchResult res = CatalogManager.search(
        p,
        v.query(),
        SyncPayload.MAX_ENTRIES
      );
      total = res.total();
      for (CatalogEntry e : res.entries()) {
        String code = "";
        for (PermissionRequest r : mine)
          if (r.node().equalsIgnoreCase(e.node)) code = r.code();
        List<String> durs = new ArrayList<>(
          e.durations.subList(
            0,
            Math.min(e.durations.size(), SyncPayload.MAX_DURATIONS)
          )
        );
        if (durs.isEmpty()) durs.add(Durations.format(e.max())); // only maxDuration configured
        entries.add(
          new EntryDto(
            cut(e.node, 128),
            cut(e.title(), 128),
            cut(e.description, 256),
            durs,
            code
          )
        );
      }
    } else if (RequestService.isAdmin(p)) {
      long now = System.currentTimeMillis();
      for (PermissionRequest r : rm.all()) {
        if (pending.size() >= SyncPayload.MAX_PENDING) break;
        pending.add(
          new PendingDto(
            r.code(),
            cut(r.playerName(), 64),
            cut(r.node(), 128),
            r.duration().getSeconds(),
            cut(r.reason(), 512),
            Math.max(0, (now - r.createdAtMs()) / 1000)
          )
        );
      }
      total = pending.size();
    }
    return new SyncPayload(open, v.mode(), entries, pending, total, v.query());
  }

  private static String cut(String s, int n) {
    return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
  }
}
