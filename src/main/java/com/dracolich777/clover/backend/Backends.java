package com.dracolich777.clover.backend;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.clover;

import net.neoforged.fml.ModList;

public final class Backends {
    private static volatile PermissionBackend current;
    private static volatile long failedAt;

    private Backends() {}

    /** Lazily created; returns null (and logs at most every 5s) while unavailable. */
    public static PermissionBackend get() {
        PermissionBackend b = current;
        if (b != null) return b;
        if (System.currentTimeMillis() - failedAt < 5000) return null;
        synchronized (Backends.class) {
            if (current != null) return current;
            try {
                if (Config.BACKEND.get() == Config.BackendType.MOCK) {
                    current = new MockBackend();
                } else {
                    if (!ModList.get().isLoaded("luckperms")) throw new IllegalStateException("LuckPerms mod is not loaded");
                    current = new LuckPermsBackend();
                }
                clover.LOGGER.info("[Clover] Using {} backend", current.name());
                return current;
            } catch (Exception | LinkageError e) {
                failedAt = System.currentTimeMillis();
                clover.LOGGER.warn("[Clover] Permission backend unavailable: {}", e.toString());
                return null;
            }
        }
    }

    public static void reset() {
        current = null;
        failedAt = 0;
    }
}