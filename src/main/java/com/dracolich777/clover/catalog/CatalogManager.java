package com.dracolich777.clover.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.backend.PermissionBackend;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.NodeNames;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;


public final class CatalogManager {
    private static final Gson GSON = new Gson();

    private static volatile Map<String, CatalogEntry> entries = Map.of(); // key = lowercase node
    private static Map<String, CatalogEntry> manual = Map.of();
    private static volatile long lastAutoMs;

    public record SearchResult(List<CatalogEntry> entries, int total) {}
    private record Scored(CatalogEntry entry, int score) {}

    private CatalogManager() {}

    public static Path dir() { return FMLPaths.CONFIGDIR.get().resolve("clover"); }
    public static Path file() { return dir().resolve("permissions.json"); }
    public static Path namesFile() { return dir().resolve("node-names.json"); }

    public static CatalogEntry get(String node) {
        maybeRefresh();
        return node == null ? null : entries.get(node.toLowerCase(Locale.ROOT));
    }

    /** Entries this player may request that match every word of the query, best matches first. */
    public static SearchResult search(ServerPlayer p, String query, int limit) {
        maybeRefresh();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String[] tokens = q.isEmpty() ? new String[0] : q.split("\\s+");
        PermissionBackend backend = Backends.get();
        Map<List<String>, Boolean> groupMemo = new IdentityHashMap<>(); // auto entries share one list instance

        List<Scored> hits = new ArrayList<>();
        for (CatalogEntry e : entries.values()) {
            if (!e.allowedGroups.isEmpty()) {
                boolean ok = groupMemo.computeIfAbsent(e.allowedGroups, g -> backend != null && backend.isStaff(p, g));
                if (!ok) continue;
            }
            int score = 0;
            boolean all = true;
            for (String t : tokens) {
                int s = e.score(t);
                if (s == 0) { all = false; break; }
                score += s;
            }
            if (all) hits.add(new Scored(e, score));
        }
        hits.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.entry().titleLower));

        List<CatalogEntry> out = new ArrayList<>();
        for (int i = 0; i < hits.size() && out.size() < limit; i++) out.add(hits.get(i).entry());
        return new SearchResult(out, hits.size());
    }


    /** @return number of requestable permissions, or -1 on failure (previous data is kept). */
    public static synchronized int load() {
        try {
            Files.createDirectories(dir());
            loadNames();
            loadManual();
            rebuild();
            clover.LOGGER.info("[Clover] Permission registry ({}): {} requestable permissions ({} manual)",
                    Config.REGISTRY_MODE.get(), entries.size(), manual.size());
            return entries.size();
        } catch (Exception ex) {
            clover.LOGGER.error("[Clover] Failed to load {} - keeping previous catalog", file(), ex);
            return -1;
        }
    }

    /** Re-runs detection when the refresh interval has passed (cheap check otherwise). */
    public static void maybeRefresh() {
        try {
            if (Config.REGISTRY_MODE.get() == Config.RegistryMode.MANUAL) return;
            long ttl = Config.REGISTRY_REFRESH_SECONDS.get() * 1000L;
            if (System.currentTimeMillis() - lastAutoMs >= ttl) rebuild();
        } catch (Exception | LinkageError e) {
            clover.LOGGER.debug("[Clover] registry refresh failed", e);
        }
    }

    private static synchronized void rebuild() {
        Config.RegistryMode mode = Config.REGISTRY_MODE.get();
        Map<String, CatalogEntry> out = new LinkedHashMap<>();
        boolean autoOk = true;
        if (mode != Config.RegistryMode.MANUAL) autoOk = loadAutomatic(out);
        if (mode != Config.RegistryMode.AUTOMATIC) out.putAll(manual);
        entries = Collections.unmodifiableMap(out);
        long now = System.currentTimeMillis();
        long ttl = Config.REGISTRY_REFRESH_SECONDS.get() * 1000L;
        lastAutoMs = autoOk ? now : now - ttl + 5000L;
    }

    private static boolean loadAutomatic(Map<String, CatalogEntry> out) {
        PermissionBackend backend = Backends.get();
        if (backend == null) return false;
        Collection<String> known;
        try {
            known = backend.knownPermissions();
        } catch (Exception | LinkageError e) {
            clover.LOGGER.warn("[Clover] Could not list permissions from {}: {}", backend.name(), e.toString());
            return false;
        }
        if (known == null) return true;

        List<String> durations = new ArrayList<>();
        for (String d : Config.AUTO_DURATIONS.get()) if (Durations.parse(d) != null) durations.add(d);
        if (durations.isEmpty()) durations = List.of("10m", "30m", "1h");
        durations = List.copyOf(durations);

        Duration cap = Durations.parse(Config.AUTO_MAX_DURATION.get());
        if (cap == null) {
            for (String d : durations) {
                Duration p = Durations.parse(d);
                if (cap == null || p.compareTo(cap) > 0) cap = p;
            }
        }
        List<String> groups = List.copyOf(Config.AUTO_ALLOWED_GROUPS.get());
        List<String> blocked = new ArrayList<>();
        for (String b : Config.AUTO_BLOCKED_NODES.get()) blocked.add(b.toLowerCase(Locale.ROOT));
        String adminNode = PermissionBackend.ADMIN_PERMISSION.toLowerCase(Locale.ROOT);

        int skipped = 0;
        for (String node : known) {
            if (!validNode(node) || isBlocked(node.toLowerCase(Locale.ROOT), blocked, adminNode)) { skipped++; continue; }
            CatalogEntry e = new CatalogEntry();
            e.node = node;
            e.durations = durations;
            e.maxDuration = Config.AUTO_MAX_DURATION.get();
            e.max = cap;
            e.allowedGroups = groups;
            e.prepare();
            out.putIfAbsent(node.toLowerCase(Locale.ROOT), e);
        }
        clover.LOGGER.debug("[Clover] Detected {} permissions ({} skipped/blocked)", out.size(), skipped);
        return true;
    }

    private static boolean isBlocked(String n, List<String> blocked, String adminNode) {
        for (String pat : blocked) {
            if (pat.equals(n)) return true;
            if (pat.endsWith(".*")) {
                String prefix = pat.substring(0, pat.length() - 1); // "clover."
                if (n.startsWith(prefix) || n.equals(pat.substring(0, pat.length() - 2))) return true;
            }
        }
        if (n.equals(adminNode) || n.equals("*")) return true;
        // a wildcard that would grant the admin permission
        return n.endsWith(".*") && adminNode.startsWith(n.substring(0, n.length() - 1));
    }

    private static boolean validNode(String node) {
        return node != null && node.matches("[A-Za-z0-9_.*:\\-]+");
    }

    private static void loadManual() throws IOException {
        Path f = file();
        if (!Files.exists(f)) Files.writeString(f, DEFAULT);
        JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
        if (root.has("mode")) {
            clover.LOGGER.warn("[Clover] permissions.json: \"mode\" is ignored now. Set permissionRegistry.mode in clover-common.toml.");
        }
        CatalogEntry[] arr = (root.has("permissions") && !root.get("permissions").isJsonNull())
                ? GSON.fromJson(root.get("permissions"), CatalogEntry[].class) : null;

        Map<String, CatalogEntry> out = new LinkedHashMap<>();
        if (arr != null) {
            for (CatalogEntry e : arr) {
                if (e == null || !validNode(e.node)) {
                    clover.LOGGER.warn("[Clover] Skipping entry with missing/invalid node");
                    continue;
                }
                String key = e.node.toLowerCase(Locale.ROOT);
                if (out.containsKey(key)) {
                    clover.LOGGER.warn("[Clover] Skipping duplicate node '{}'", e.node);
                    continue;
                }
                if (e.description == null) e.description = "";
                if (e.displayName == null) e.displayName = "";
                if (e.allowedGroups == null) e.allowedGroups = new ArrayList<>();
                List<String> good = new ArrayList<>();
                Duration longest = null;
                if (e.durations != null) {
                    for (String d : e.durations) {
                        Duration p = Durations.parse(d);
                        if (p == null) continue;
                        good.add(d);
                        if (longest == null || p.compareTo(longest) > 0) longest = p;
                    }
                }
                e.durations = good;
                Duration cap = (e.maxDuration == null || e.maxDuration.isBlank()) ? longest : Durations.parse(e.maxDuration);
                if (cap == null) {
                    clover.LOGGER.warn("[Clover] Skipping '{}': no valid durations / maxDuration", e.node);
                    continue;
                }
                e.max = cap;
                e.prepare();
                out.put(key, e);
            }
        }
        manual = out;
    }

    private static void loadNames() {
        try {
            Path f = namesFile();
            if (!Files.exists(f)) Files.writeString(f, DEFAULT_NAMES);
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            Map<String, String> m = new HashMap<>();
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                if (en.getKey().startsWith("_") || !en.getValue().isJsonPrimitive()) continue;
                m.put(en.getKey().toLowerCase(Locale.ROOT), en.getValue().getAsString());
            }
            NodeNames.setOverrides(m);
        } catch (Exception e) {
            clover.LOGGER.warn("[Clover] Could not read node-names.json ({}); keeping previous names", e.toString());
        }
    }

    private static final String DEFAULT = """
            {
              "_readme": "Used when permissionRegistry.mode is MANUAL or HYBRID (set in clover-common.toml). displayName is optional; without it a friendly name is generated from the node.",
              "permissions": [
                {
                  "node": "command.gamemode.*",
                  "description": "Change gamemode",
                  "durations": ["10m", "30m", "1h"],
                  "maxDuration": "1h",
                  "allowedGroups": []
                },
                {
                  "node": "minecraft.command.tp",
                  "displayName": "Teleport (/tp)",
                  "description": "Teleport to players and coordinates",
                  "durations": ["10m", "30m"],
                  "maxDuration": "30m",
                  "allowedGroups": ["moderator", "admin"]
                }
              ]
            }
            """;

    private static final String DEFAULT_NAMES = """
            {
              "_readme": "Friendly names for permission nodes. Key = node in lowercase. A trailing * applies to everything under that prefix (an exact key wins over a prefix).",
              "example.node": "Example Friendly Name"
            }
            """;
}