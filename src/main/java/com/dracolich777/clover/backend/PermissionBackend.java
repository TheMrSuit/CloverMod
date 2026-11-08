package com.dracolich777.clover.backend;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.dracolich777.clover.Config;

import net.minecraft.server.level.ServerPlayer;

public interface PermissionBackend {
    long ACTIVE_NONE = -1;
    long ACTIVE_PERMANENT = -2;

    String name();
    boolean isStaff(ServerPlayer player, Collection<? extends String> groups);
    boolean hasPermission(ServerPlayer player, String node);

    /** Writes a native LuckPerms temporary node. Must not block the server thread. */
    CompletableFuture<Void> grant(UUID player, String node, Duration duration, Config.MergeMode merge);

    /** Seconds left on the player's own temporary node (read from LuckPerms), ACTIVE_NONE, or ACTIVE_PERMANENT. */
    long activeRemainingSeconds(ServerPlayer player, String node);
}