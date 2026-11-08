
package com.dracolich777.clover.catalog;

import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.backend.PermissionBackend;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.util.Durations;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

public final class CatalogManager {

    private static final Gson GSON = new Gson();

    private static volatile Map<String, CatalogEntry> entries = Map.of();

    private static final List<String> DEFAULT_DURATIONS =
        List.of("10m", "30m", "1h");

    private static final String DEFAULT_MAX_DURATION = "1h";

    private enum Mode {
        DYNAMIC,
        CONFIGURED,
        HYBRID
    }

    private CatalogManager() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get()
            .resolve("clover")
            .resolve("permissions.json");
    }

    public static CatalogEntry get(String node) {
        return node == null
            ? null
            : entries.get(node.toLowerCase(Locale.ROOT));
    }

    public static List<CatalogEntry> visibleTo(ServerPlayer p) {
        PermissionBackend backend = Backends.get();

        List<CatalogEntry> out = new ArrayList<>();

        for (CatalogEntry entry : entries.values()) {
            if (
                entry.allowedGroups.isEmpty() ||
                (backend != null &&
                    backend.isStaff(p, entry.allowedGroups))
            ) {
                out.add(entry);
            }
        }

        return out;
    }

    public static synchronized int load() {
        try {
            Path file = file();

            Files.createDirectories(file.getParent());

            if (!Files.exists(file)) {
                Files.writeString(file, DEFAULT);
            }

            JsonObject root = JsonParser.parseString(
                Files.readString(file)
            ).getAsJsonObject();

            Mode mode;

            try {
                mode = Mode.valueOf(
                    root.has("mode")
                        ? root.get("mode").getAsString()
                            .toUpperCase(Locale.ROOT)
                        : "HYBRID"
                );
            } catch (IllegalArgumentException ex) {
                clover.LOGGER.warn(
                    "[Clover] Invalid catalog mode. Using HYBRID."
                );
                mode = Mode.HYBRID;
            }

            Map<String, CatalogEntry> out = new LinkedHashMap<>();

            // Load automatically discovered LuckPerms permissions.
            if (mode == Mode.DYNAMIC || mode == Mode.HYBRID) {
                loadDynamic(out);
            }

            // Load manually configured permissions.
            if (mode == Mode.CONFIGURED || mode == Mode.HYBRID) {
                loadConfigured(root, out);
            }

            entries = Collections.unmodifiableMap(out);

            clover.LOGGER.info(
                "[Clover] Loaded {} permission catalog entries ({})",
                out.size(),
                mode
            );

            return out.size();

        } catch (Exception ex) {
            clover.LOGGER.error(
                "[Clover] Failed to load {} - keeping previous catalog",
                file(),
                ex
            );

            return -1;
        }
    }

    /**
     * Discovers permissions known to LuckPerms.
     */
    private static void loadDynamic(
        Map<String, CatalogEntry> out
    ) {
        LuckPerms luckPerms;

        try {
            luckPerms = LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            clover.LOGGER.warn(
                "[Clover] LuckPerms is unavailable. " +
                "Dynamic permissions could not be loaded."
            );
            return;
        }

        Collection<String> permissions =
            luckPerms.getPlatform().getKnownPermissions();

        if (permissions == null) {
            return;
        }

        for (String node : permissions) {
            if (!validNode(node)) {
                continue;
            }

            String key = node.toLowerCase(Locale.ROOT);

            CatalogEntry entry = new CatalogEntry();

            entry.node = node;
            entry.description = "Discovered permission";
            entry.durations = new ArrayList<>(DEFAULT_DURATIONS);
            entry.maxDuration = DEFAULT_MAX_DURATION;
            entry.allowedGroups = new ArrayList<>();

            entry.max = Durations.parse(DEFAULT_MAX_DURATION);

            out.putIfAbsent(key, entry);
        }

        clover.LOGGER.info(
            "[Clover] Discovered {} LuckPerms permissions",
            permissions.size()
        );
    }

    /**
     * Loads manually configured permissions.
     * Configured entries override dynamically discovered entries.
     */
    private static void loadConfigured(
        JsonObject root,
        Map<String, CatalogEntry> out
    ) {
        if (!root.has("permissions") ||
            root.get("permissions").isJsonNull()) {
            return;
        }

        CatalogEntry[] configured = GSON.fromJson(
            root.get("permissions"),
            CatalogEntry[].class
        );

        if (configured == null) {
            return;
        }

        for (CatalogEntry entry : configured) {
            if (entry == null || !validNode(entry.node)) {
                clover.LOGGER.warn(
                    "[Clover] Skipping entry with missing/invalid node"
                );
                continue;
            }

            String key = entry.node.toLowerCase(Locale.ROOT);

            normalize(entry);

            if (entry.max == null) {
                clover.LOGGER.warn(
                    "[Clover] Skipping '{}': no valid durations/maxDuration",
                    entry.node
                );
                continue;
            }

            out.put(key, entry);
        }
    }

    /**
     * Normalizes configured entries and calculates their maximum duration.
     */
    private static void normalize(CatalogEntry entry) {
        if (entry.description == null) {
            entry.description = "";
        }

        if (entry.allowedGroups == null) {
            entry.allowedGroups = new ArrayList<>();
        }

        List<String> validDurations = new ArrayList<>();

        Duration longest = null;

        if (entry.durations != null) {
            for (String duration : entry.durations) {
                Duration parsed = Durations.parse(duration);

                if (parsed == null) {
                    continue;
                }

                validDurations.add(duration);

                if (longest == null ||
                    parsed.compareTo(longest) > 0) {
                    longest = parsed;
                }
            }
        }

        entry.durations = validDurations;

        Duration cap =
            entry.maxDuration == null ||
            entry.maxDuration.isBlank()
                ? longest
                : Durations.parse(entry.maxDuration);

        if (cap != null) {
            entry.max = cap;
        }
    }

    private static boolean validNode(String node) {
        return node != null &&
            node.matches("[A-Za-z0-9_.*:\\-]+");
    }

    private static final String DEFAULT = """
        {
          "mode": "DYNAMIC",
          "permissions": [
            {
              "node": "command.gamemode.*",
              "description": "Change gamemode",
              "durations": ["10m", "30m", "1h"],
              "maxDuration": "1h",
              "allowedGroups": ["moderator", "admin"]
            },
            {
              "node": "minecraft.command.tp",
              "description": "Teleport",
              "durations": ["10m", "30m"],
              "maxDuration": "30m",
              "allowedGroups": ["moderator", "admin"]
            }
          ]
        }
        """;
}
