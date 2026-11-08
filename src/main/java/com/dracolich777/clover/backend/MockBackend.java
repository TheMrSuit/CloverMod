package com.dracolich777.clover.backend;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.clover;

import net.minecraft.server.level.ServerPlayer;

/** DEV ONLY: everyone is staff and admin. Fakes LuckPerms-style expiry in memory. */
public final class MockBackend implements PermissionBackend {
    private final Map<String, Long> expiries = new ConcurrentHashMap<>(); // uuid|node -> epoch ms

    @Override public String name() { return "mock"; }
    @Override public boolean isStaff(ServerPlayer p, Collection<? extends String> groups) { return true; }
    @Override public boolean hasPermission(ServerPlayer p, String node) { return true; }

    @Override
    public CompletableFuture<Void> grant(UUID player, String node, Duration d, Config.MergeMode merge) {
        if (ThreadLocalRandom.current().nextDouble() < Config.MOCK_FAILURE_CHANCE.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("simulated failure"));
        }
        String key = player + "|" + node;
        long now = System.currentTimeMillis();
        long base = merge == Config.MergeMode.ADD_TO_EXISTING ? Math.max(now, expiries.getOrDefault(key, now)) : now;
        long end = base + d.toMillis();
        expiries.merge(key, end, merge == Config.MergeMode.ADD_TO_EXISTING ? (o, n) -> n : Math::max);
        clover.LOGGER.info("[Clover/mock] granted {} to {} for {}", node, player, d);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public long activeRemainingSeconds(ServerPlayer p, String node) {
        Long end = expiries.get(p.getUUID() + "|" + node);
        if (end == null) return ACTIVE_NONE;
        long left = (end - System.currentTimeMillis()) / 1000;
        return left > 0 ? left : ACTIVE_NONE;
    }
}