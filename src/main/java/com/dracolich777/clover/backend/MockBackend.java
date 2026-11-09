package com.dracolich777.clover.backend;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.clover;

import net.minecraft.server.level.ServerPlayer;

/** DEV ONLY: everyone is staff and admin; grants are just logged. */
public final class MockBackend implements PermissionBackend {
    private static final List<String> KNOWN = List.of(
            "minecraft.command.gamemode", "minecraft.command.tp", "minecraft.command.give", "minecraft.command.time",
            "minecraft.command.weather", "minecraft.command.xp", "minecraft.command.kill", "command.gamemode.*",
            "worldedit.selection.pos", "worldedit.region.set", "worldedit.clipboard.copy", "worldedit.*",
            "essentials.fly", "essentials.fly.safelogin", "essentials.tpa", "oclaims.command.admin-mode",
            "oclaims.command.claim", "oclaims.bypass", "luckperms.*", "clover.admin", "clover.*", "*");

    @Override public String name() { return "mock"; }
    @Override public boolean isStaff(ServerPlayer p, Collection<? extends String> groups) { return true; }
    @Override public boolean hasPermission(ServerPlayer p, String node) { return true; }
    @Override public Collection<String> knownPermissions() { return KNOWN; }

    @Override
    public CompletableFuture<Void> grant(UUID player, String node, Duration d, Config.MergeMode merge) {
        if (ThreadLocalRandom.current().nextDouble() < Config.MOCK_FAILURE_CHANCE.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("simulated failure"));
        }
        clover.LOGGER.info("[Clover/mock] would grant {} to {} for {} ({})", node, player, d, merge);
        return CompletableFuture.completedFuture(null);
    }
}