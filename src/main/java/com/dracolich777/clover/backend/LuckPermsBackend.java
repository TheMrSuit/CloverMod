package com.dracolich777.clover.backend;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.dracolich777.clover.Config;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.TemporaryNodeMergeStrategy;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.platform.PlayerAdapter;
import net.minecraft.server.level.ServerPlayer;

public final class LuckPermsBackend implements PermissionBackend {
    private final LuckPerms lp = LuckPermsProvider.get();
    private final PlayerAdapter<ServerPlayer> adapter = lp.getPlayerAdapter(ServerPlayer.class);

    @Override public String name() { return "luckperms"; }

    @Override
    public boolean isStaff(ServerPlayer p, Collection<? extends String> groups) {
        if (groups.isEmpty()) return false;
        try {
            User user = adapter.getUser(p);
            for (Group g : user.getInheritedGroups(adapter.getQueryOptions(p))) {
                for (String want : groups) if (g.getName().equalsIgnoreCase(want)) return true;
            }
        } catch (IllegalStateException notLoaded) {
            // user data not loaded yet
        }
        return false;
    }

    @Override
    public boolean hasPermission(ServerPlayer p, String node) {
        try {
            return adapter.getPermissionData(p).checkPermission(node).asBoolean();
        } catch (IllegalStateException notLoaded) {
            return false;
        }
    }

    @Override
    public CompletableFuture<Void> grant(UUID uuid, String permission, Duration duration, Config.MergeMode merge) {
        Node node = Node.builder(permission).value(true).expiry(duration).build();
        TemporaryNodeMergeStrategy strategy = merge == Config.MergeMode.ADD_TO_EXISTING
                ? TemporaryNodeMergeStrategy.ADD_NEW_DURATION_TO_EXISTING
                : TemporaryNodeMergeStrategy.REPLACE_EXISTING_IF_DURATION_LONGER;
        return lp.getUserManager().modifyUser(uuid, user -> user.data().add(node, strategy));
    }

    @Override
    public long activeRemainingSeconds(ServerPlayer p, String permission) {
        try {
            long best = ACTIVE_NONE;
            for (PermissionNode n : adapter.getUser(p).getNodes(NodeType.PERMISSION)) {
                if (!n.getValue() || !n.getKey().equalsIgnoreCase(permission) || n.hasExpired()) continue;
                if (!n.hasExpiry()) return ACTIVE_PERMANENT;
                best = Math.max(best, n.getExpiryDuration().getSeconds());
            }
            return best;
        } catch (IllegalStateException notLoaded) {
            return ACTIVE_NONE;
        }
    }
}