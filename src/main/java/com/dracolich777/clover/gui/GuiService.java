package com.dracolich777.clover.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.backend.PermissionBackend;
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

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Opens the right screen for the right person and keeps open screens fresh. Server thread only. */
public final class GuiService {
    private static final Map<UUID, Byte> viewers = new HashMap<>();
    private static int lastVersion = -1;
    private static int ticks;

    private GuiService() {}

    /** /clover: admins get the pending-requests screen, staff get the request screen. */
    public static void openAuto(ServerPlayer p) {
        if (RequestService.isAdmin(p)) open(p, SyncPayload.MODE_ADMIN);
        else if (isStaff(p)) open(p, SyncPayload.MODE_REQUEST);
        else p.sendSystemMessage(Text.msg(Config.MSG_NOT_STAFF));
    }

    public static void openRequest(ServerPlayer p) {
        if (!isStaff(p)) { p.sendSystemMessage(Text.msg(Config.MSG_NOT_STAFF)); return; }
        open(p, SyncPayload.MODE_REQUEST);
    }

    public static void openAdmin(ServerPlayer p) {
        if (!RequestService.isAdmin(p)) { p.sendSystemMessage(Text.msg(Config.MSG_NOT_ALLOWED)); return; }
        open(p, SyncPayload.MODE_ADMIN);
    }

    private static boolean isStaff(ServerPlayer p) {
        PermissionBackend b = Backends.get();
        return b != null && b.isStaff(p, Config.STAFF_GROUPS.get());
    }

    private static void open(ServerPlayer p, byte mode) {
        if (!hasClientGui(p)) { p.sendSystemMessage(Text.msg(Config.MSG_NO_CLIENT)); return; }
        if (send(p, mode, true)) viewers.put(p.getUUID(), mode);
    }

    private static boolean hasClientGui(ServerPlayer p) {
        try {
            return p.connection.hasChannel(SyncPayload.TYPE);
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    private static boolean send(ServerPlayer p, byte mode, boolean open) {
        try {
            PacketDistributor.sendToPlayer(p, snapshot(p, mode, open));
            return true;
        } catch (Exception | LinkageError e) {
            clover.LOGGER.error("[Clover] Failed to send menu data to {}", p.getName().getString(), e);
            viewers.remove(p.getUUID());
            return false;
        }
    }

    /** Immediate refresh for a player who has a screen open (after their own action). */
    public static void resend(ServerPlayer p) {
        Byte mode = viewers.get(p.getUUID());
        if (mode != null) send(p, mode, false);
    }

    public static void closed(ServerPlayer p, byte mode) {
        Byte cur = viewers.get(p.getUUID());
        if (cur != null && cur == mode) viewers.remove(p.getUUID());
    }

    public static void forget(UUID id) { viewers.remove(id); }
    public static void clear() { viewers.clear(); lastVersion = -1; }

    /** Returns immediately with no viewers; otherwise one int compare every 5 ticks. */
    public static void tick(MinecraftServer server) {
        if (viewers.isEmpty() || ++ticks % 5 != 0) return;
        int v = RequestManager.get().version();
        if (v == lastVersion) return;
        lastVersion = v;
        Iterator<Map.Entry<UUID, Byte>> it = viewers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Byte> en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !send(p, en.getValue(), false)) it.remove();
        }
    }

    // ------------------------------------------------------------ snapshot

    private static SyncPayload snapshot(ServerPlayer p, byte mode, boolean open) {
        RequestManager rm = RequestManager.get();
        List<EntryDto> entries = new ArrayList<>();
        List<PendingDto> pending = new ArrayList<>();
        if (mode == SyncPayload.MODE_REQUEST) {
            List<PermissionRequest> mine = new ArrayList<>();
            for (PermissionRequest r : rm.all()) if (r.player().equals(p.getUUID())) mine.add(r);
            for (CatalogEntry e : CatalogManager.visibleTo(p)) {
                if (entries.size() >= SyncPayload.MAX_ENTRIES) break;
                String code = "";
                for (PermissionRequest r : mine) if (r.node().equalsIgnoreCase(e.node)) code = r.code();
                List<String> durs = new ArrayList<>(
                        e.durations.subList(0, Math.min(e.durations.size(), SyncPayload.MAX_DURATIONS)));
                if (durs.isEmpty()) durs.add(Durations.format(e.max())); // only maxDuration configured
                entries.add(new EntryDto(cut(e.node, 128), cut(e.description, 256), durs, code));
            }
        } else if (RequestService.isAdmin(p)) {
            long now = System.currentTimeMillis();
            for (PermissionRequest r : rm.all()) {
                if (pending.size() >= SyncPayload.MAX_PENDING) break;
                pending.add(new PendingDto(r.code(), cut(r.playerName(), 64), cut(r.node(), 128),
                        r.duration().getSeconds(), cut(r.reason(), 512), Math.max(0, (now - r.createdAtMs()) / 1000)));
            }
        }
        return new SyncPayload(open, mode, entries, pending);
    }

    private static String cut(String s, int n) {
        return s == null ? "" : (s.length() <= n ? s : s.substring(0, n));
    }
}