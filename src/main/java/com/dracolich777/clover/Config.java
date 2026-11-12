package com.dracolich777.clover;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public record Key(String path, ModConfigSpec.ConfigValue<?> value, Number min, Number max, boolean secret) {}

    private static final Map<String, Key> KEYS = new LinkedHashMap<>();

    public static Map<String, Key> keys() { return KEYS; }

    public enum BackendType { LUCKPERMS, MOCK }
    public enum MergeMode { ADD_TO_EXISTING, REPLACE_IF_LONGER }
    public enum RegistryMode { AUTOMATIC, MANUAL, HYBRID }


    private static ModConfigSpec.Builder prep(String tkey, String comment) {
        B.translation("clover.configuration." + tkey);
        if (comment != null && !comment.isEmpty()) B.comment(comment.split("\n"));
        return B;
    }

    private static <V extends ModConfigSpec.ConfigValue<?>> V reg(V v, Number min, Number max, boolean secret) {
        String path = String.join(".", v.getPath());
        KEYS.put(path, new Key(path, v, min, max, secret));
        return v;
    }

    private static ModConfigSpec.BooleanValue bool(String tkey, String key, boolean def, String comment) {
        return reg(prep(tkey, comment).define(key, def), null, null, false);
    }

    private static ModConfigSpec.IntValue num(String tkey, String key, int def, int min, int max, String comment) {
        return reg(prep(tkey, comment).defineInRange(key, def, min, max), min, max, false);
    }

    private static ModConfigSpec.DoubleValue dec(String tkey, String key, double def, double min, double max, String comment) {
        return reg(prep(tkey, comment).defineInRange(key, def, min, max), min, max, false);
    }

    private static ModConfigSpec.ConfigValue<String> str(String tkey, String key, String def, String comment, boolean secret) {
        return reg(prep(tkey, comment).define(key, def), null, null, secret);
    }

    private static <E extends Enum<E>> ModConfigSpec.EnumValue<E> choice(String tkey, String key, E def, String comment) {
        return reg(prep(tkey, comment).defineEnum(key, def), null, null, false);
    }

    private static ModConfigSpec.ConfigValue<List<? extends String>> list(String tkey, String key, List<String> def, String comment) {
        return reg(prep(tkey, comment).defineListAllowEmpty(key, def, () -> "", o -> o instanceof String), null, null, false);
    }

    private static void section(String tkey, String name) {
        B.translation("clover.configuration." + tkey);
        B.push(name);
    }

    private static ModConfigSpec.ConfigValue<String> msg(String key, String def) {
        return str("msg." + key, key, def, "", false);
    }

    public static final ModConfigSpec.EnumValue<BackendType> BACKEND = choice("backend", "backend", BackendType.LUCKPERMS,
            "LUCKPERMS for real servers. MOCK is dev-only (everyone is staff AND admin, fake permission list).");

    public static final ModConfigSpec.EnumValue<MergeMode> MERGE_MODE = choice("mergeMode", "mergeMode", MergeMode.ADD_TO_EXISTING,
            "If the player already has that temporary permission in LuckPerms:\n"
                    + "ADD_TO_EXISTING extends the remaining LuckPerms expiry by the approved duration.\n"
                    + "REPLACE_IF_LONGER keeps whichever expiry is longer.");

    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS = num("requestTimeoutSeconds",
            "requestTimeoutSeconds", 600, 10, 86400, "Pending requests expire after this long.");
    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS = num("cooldownSeconds", "cooldownSeconds", 5, 0, 3600, "");
    public static final ModConfigSpec.IntValue MAX_PENDING = num("maxPendingPerPlayer", "maxPendingPerPlayer", 3, 1, 50,
            "Max simultaneous pending requests per player.");
    public static final ModConfigSpec.BooleanValue REQUIRE_REASON = bool("requireReason", "requireReason", true, "");
    public static final ModConfigSpec.IntValue MAX_REASON_LENGTH = num("maxReasonLength", "maxReasonLength", 120, 10, 400, "");
    public static final ModConfigSpec.BooleanValue ALLOW_SELF_APPROVAL = bool("allowSelfApproval", "allowSelfApproval", false,
            "Let an admin approve their own request. Turn on for solo testing.");
    public static final ModConfigSpec.BooleanValue CANCEL_ON_LOGOUT = bool("cancelOnLogout", "cancelOnLogout", true,
            "Cancel a player's pending requests when they log out (the grant timer starts at approval).");
    public static final ModConfigSpec.BooleanValue NOTIFY_ADMINS = bool("notifyAdmins", "notifyAdmins", true,
            "Tell online admins in chat (with clickable Accept/Deny).");
    public static final ModConfigSpec.IntValue PERMISSION_EXPIRY_WARNING_SECONDS = num("permissionExpiryWarningSeconds",
            "permissionExpiryWarningSeconds", 60, 0, 3600,
            "Warn players this many seconds before a temporary permission expires. 0 disables the warning.");
    public static final ModConfigSpec.IntValue LP_RETRY_ATTEMPTS = num("luckPermsRetryAttempts", "luckPermsRetryAttempts", 3, 0, 10, "");
    public static final ModConfigSpec.IntValue LP_RETRY_DELAY_MS = num("luckPermsRetryDelayMs", "luckPermsRetryDelayMs", 1000, 100, 60000, "");
    public static final ModConfigSpec.DoubleValue MOCK_FAILURE_CHANCE = dec("mockFailureChance", "mockFailureChance", 0.0, 0.0, 1.0,
            "MOCK backend only: chance (0-1) a grant fails, to test retry/error handling.");


    public static final ModConfigSpec.EnumValue<RegistryMode> REGISTRY_MODE;
    public static final ModConfigSpec.IntValue REGISTRY_REFRESH_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> AUTO_DURATIONS, AUTO_ALLOWED_GROUPS, AUTO_BLOCKED_NODES;
    public static final ModConfigSpec.ConfigValue<String> AUTO_MAX_DURATION;

    public static final ModConfigSpec.BooleanValue DISCORD_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> DISCORD_WEBHOOK_URL, DISCORD_PING_ROLE_ID, DISCORD_USERNAME, DISCORD_FOOTER;
    public static final ModConfigSpec.BooleanValue DISCORD_POST_OUTCOMES;
    public static final ModConfigSpec.IntValue DISCORD_RETRY_ATTEMPTS;

    // {id} {player} {permission} {duration} {reason} {admin} {note} {max}
    public static final ModConfigSpec.ConfigValue<String> PREFIX, NOTE_FORMAT;
    public static final ModConfigSpec.ConfigValue<String> MSG_USAGE, MSG_REQUEST_SENT, MSG_NOT_STAFF, MSG_NOT_ALLOWED,
            MSG_UNKNOWN_PERMISSION, MSG_INVALID_DURATION, MSG_REASON_REQUIRED, MSG_REASON_TOO_LONG, MSG_COOLDOWN,
            MSG_TOO_MANY, MSG_ADMIN_NOTIFY, MSG_APPROVED_PLAYER, MSG_APPROVED_ADMIN, MSG_DENIED_PLAYER,
            MSG_DENIED_ADMIN, MSG_EXPIRED, MSG_NO_PENDING, MSG_NONE_AVAILABLE, MSG_SELF_APPROVAL,
            MSG_BACKEND_DOWN, MSG_GRANT_FAILED, MSG_ERROR, MSG_RELOADED, MSG_NO_CLIENT, MSG_PERMISSION_EXPIRING,
            MSG_PERMISSION_LOST;

    static {
        section("permissionRegistry", "permissionRegistry");
        REGISTRY_MODE = choice("permissionRegistry.mode", "mode", RegistryMode.MANUAL,
                "How the list of requestable permissions is built:\n"
                        + "MANUAL    - only the nodes in config/clover/permissions.json.\n"
                        + "AUTOMATIC - every permission LuckPerms knows about (minus the blocklist below).\n"
                        + "HYBRID    - both; permissions.json entries override detected ones.");
        REGISTRY_REFRESH_SECONDS = num("permissionRegistry.refreshSeconds", "refreshSeconds", 60, 10, 3600,
                "How often the detected list is refreshed (LuckPerms learns nodes as other mods register them).");
        AUTO_DURATIONS = list("permissionRegistry.automaticDurations", "automaticDurations", List.of("10m", "30m", "1h"),
                "Duration choices offered for detected permissions.");
        AUTO_MAX_DURATION = str("permissionRegistry.automaticMaxDuration", "automaticMaxDuration", "1h",
                "Longest duration anyone can request for a detected permission.", false);
        AUTO_ALLOWED_GROUPS = list("permissionRegistry.automaticAllowedGroups", "automaticAllowedGroups", List.of(),
                "Only these LuckPerms groups can request detected permissions. Empty = every player with clover.requests.");
        AUTO_BLOCKED_NODES = list("permissionRegistry.blockedNodes", "blockedNodes", List.of("*", "luckperms.*", "clover.*"),
                "Detected permissions that can never be requested. A trailing .* blocks everything under that prefix.\n"
                        + "The admin permission and any wildcard that would grant it are always blocked.");
        B.pop();

        section("discord", "discord");
        DISCORD_ENABLED = bool("discord.enabled", "enabled", false, "");
        DISCORD_WEBHOOK_URL = str("discord.webhookUrl", "webhookUrl", "",
                "Discord webhook URL. Treat this like a password; never share this file. (Not editable via /clover config.)", true);
        DISCORD_PING_ROLE_ID = str("discord.pingRoleId", "pingRoleId", "",
                "Numeric role ID to ping on new requests (digits only). Empty = no ping.", false);
        DISCORD_USERNAME = str("discord.username", "username", "Clover", "", false);
        DISCORD_FOOTER = str("discord.footer", "footer", "Console: acperm accept {id}  |  acperm deny {id} <reason>",
                "Embed footer. {id} = request ID.", false);
        DISCORD_POST_OUTCOMES = bool("discord.postOutcomes", "postOutcomes", true,
                "Also post when a request is approved, denied or expires.");
        DISCORD_RETRY_ATTEMPTS = num("discord.retryAttempts", "retryAttempts", 4, 0, 10, "");
        B.pop();

        section("messages", "messages");
        PREFIX = msg("prefix", "&8[&aClover&8] &r");
        NOTE_FORMAT = str("msg.noteFormat", "noteFormat", " &7Reason: &f{note}",
                "Appended as {note} when an admin gives a note/reason.", false);
        MSG_USAGE = msg("usage", "&cUsage: /rqperm <permission> <duration> <reason>   (find permissions with /rqperm search <text>)");
        MSG_REQUEST_SENT = msg("requestSent",
                "&7Request &e{id} &7sent: &f{permission} &7for &f{duration}&7. If no admin is online, give them this ID: &f/acperm accept {id}");
        MSG_NOT_STAFF = msg("notStaff", "&cYou are not in a staff group.");
        MSG_NOT_ALLOWED = msg("notAllowed", "&cYour group can't request that permission.");
        MSG_UNKNOWN_PERMISSION = msg("unknownPermission", "&cThat permission can't be requested. Try &f/rqperm search <text>&c.");
        MSG_INVALID_DURATION = msg("invalidDuration",
                "&cInvalid duration. Use e.g. &f10m&c or &f1h30m&c (max for this permission: &f{max}&c).");
        MSG_REASON_REQUIRED = msg("reasonRequired", "&cPlease give a reason: &f/rqperm <permission> <duration> <reason>");
        MSG_REASON_TOO_LONG = msg("reasonTooLong", "&cYour reason is too long (max {max} characters).");
        MSG_COOLDOWN = msg("cooldown", "&cWait a moment before requesting again.");
        MSG_TOO_MANY = msg("tooMany", "&cYou have too many pending requests.");
        MSG_ADMIN_NOTIFY = msg("adminNotify",
                "&e{player} &7wants to have &f{permission} &7for &f{duration} &7because of reason: &f{reason}");
        MSG_APPROVED_PLAYER = msg("approvedPlayer",
                "&aYour request &e{id} &afor &f{permission} &a({duration}) was approved by &e{admin}&a.{note}");
        MSG_APPROVED_ADMIN = msg("approvedAdmin", "&aApproved &e{id}&a: &f{player} &7-> &f{permission} &a({duration}).");
        MSG_DENIED_PLAYER = msg("deniedPlayer", "&cYour request &e{id} &cfor &f{permission} &cwas denied by &e{admin}&c.{note}");
        MSG_DENIED_ADMIN = msg("deniedAdmin", "&7Denied &e{id}&7 (&f{player} &7-> &f{permission}&7).");
        MSG_EXPIRED = msg("expired", "&7Your request &e{id} &7for &f{permission} &7expired.");
        MSG_NO_PENDING = msg("noPending", "&cNo pending request with ID &e{id}&c.");
        MSG_NONE_AVAILABLE = msg("noneAvailable", "&7There are no permissions you can request.");
        MSG_SELF_APPROVAL = msg("selfApproval", "&cYou can't approve your own request.");
        MSG_BACKEND_DOWN = msg("backendDown", "&cThe permission system isn't available right now. Try again shortly.");
        MSG_GRANT_FAILED = msg("grantFailed",
                "&cCouldn't apply &e{permission} &cfor &e{player} &cafter several tries; request &e{id} &cwas kept. Check the server log.");
        MSG_ERROR = msg("error", "&cSomething went wrong. It was logged; please try again.");
        MSG_RELOADED = msg("reloaded", "&aReloaded {max} permission entries.");
        MSG_NO_CLIENT = msg("noClient",
                "&cThe Clover menu needs the Clover mod on your client. Use &f/rqperm&c (staff) or &f/acperm&c (admins) instead.");
        MSG_PERMISSION_EXPIRING = msg("permissionExpiring",
                "&eYour temporary permission &f{permission} &eexpires in about &f{time}&e.");
        MSG_PERMISSION_LOST = msg("permissionLost",
                "&cYou lost the temporary permission &f{permission}&c.");
        B.pop();
    }

    static final ModConfigSpec SPEC = B.build();
}