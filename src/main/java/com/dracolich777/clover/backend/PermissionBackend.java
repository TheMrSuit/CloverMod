package com.dracolich777.clover.backend;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.dracolich777.clover.Config;

import net.minecraft.server.level.ServerPlayer;

public interface PermissionBackend {
    String REQUEST_PERMISSION = "clover.requests";
    String ADMIN_PERMISSION = "clover.admin";

    String name();
    boolean isStaff(ServerPlayer player, Collection<? extends String> groups);
    boolean hasPermission(ServerPlayer player, String node);
    CompletableFuture<Void> grant(UUID player, String node, Duration duration, Config.MergeMode merge);
    Collection<String> knownPermissions();
}