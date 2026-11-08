package com.dracolich777.clover;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

  private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

  /** Sets the translation key for the next value/section: clover.configuration.<key> */
  private static ModConfigSpec.Builder t(String key) {
    return B.translation("clover.configuration." + key);
  }

  public enum BackendType {
    LUCKPERMS,
    MOCK,
  }

  public enum MergeMode {
    ADD_TO_EXISTING,
    REPLACE_IF_LONGER,
  }

  public static final ModConfigSpec.EnumValue<BackendType> BACKEND = t(
    "backend"
  )
    .comment(
      "LUCKPERMS for real servers. MOCK is dev-only (everyone is staff AND admin). Restart to change."
    )
    .defineEnum("backend", BackendType.LUCKPERMS);

  public static final ModConfigSpec.ConfigValue<
    List<? extends String>
  > STAFF_GROUPS = t("staffGroups")
    .comment(
      "LuckPerms groups (including inherited) allowed to use /rqperm and the /clover request menu."
    )
    .defineListAllowEmpty(
      "staffGroups",
      List.of("helper", "moderator", "admin"),
      () -> "",
      o -> o instanceof String
    );

  public static final ModConfigSpec.ConfigValue<String> ADMIN_PERMISSION = t(
    "adminPermission"
  )
    .comment(
      "LuckPerms node that lets a player use /acperm and the /clover admin menu. Console/RCON always can."
    )
    .define("adminPermission", "clover.admin");

  public static final ModConfigSpec.EnumValue<MergeMode> MERGE_MODE = t(
    "mergeMode"
  )
    .comment(
      "If the player already has that temporary permission in LuckPerms:",
      "ADD_TO_EXISTING extends the remaining LuckPerms expiry by the approved duration.",
      "REPLACE_IF_LONGER keeps whichever expiry is longer."
    )
    .defineEnum("mergeMode", MergeMode.ADD_TO_EXISTING);

  public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS = t(
    "requestTimeoutSeconds"
  )
    .comment("Pending requests expire after this long.")
    .defineInRange("requestTimeoutSeconds", 600, 10, 86400);
  public static final ModConfigSpec.IntValue COOLDOWN_SECONDS = t(
    "cooldownSeconds"
  ).defineInRange("cooldownSeconds", 5, 0, 3600);
  public static final ModConfigSpec.IntValue MAX_PENDING = t(
    "maxPendingPerPlayer"
  )
    .comment("Max simultaneous pending requests per player.")
    .defineInRange("maxPendingPerPlayer", 3, 1, 50);
  public static final ModConfigSpec.BooleanValue REQUIRE_REASON = t(
    "requireReason"
  ).define("requireReason", true);
  public static final ModConfigSpec.IntValue MAX_REASON_LENGTH = t(
    "maxReasonLength"
  ).defineInRange("maxReasonLength", 120, 10, 400);
  public static final ModConfigSpec.BooleanValue ALLOW_SELF_APPROVAL = t(
    "allowSelfApproval"
  )
    .comment(
      "Let an admin approve their own request. Turn on for solo testing."
    )
    .define("allowSelfApproval", false);
  public static final ModConfigSpec.BooleanValue CANCEL_ON_LOGOUT = t(
    "cancelOnLogout"
  )
    .comment(
      "Cancel a player's pending requests when they log out (the grant timer starts at approval)."
    )
    .define("cancelOnLogout", true);
  public static final ModConfigSpec.BooleanValue NOTIFY_ADMINS = t(
    "notifyAdmins"
  )
    .comment("Tell online admins in chat (with clickable Accept/Deny).")
    .define("notifyAdmins", true);
  public static final ModConfigSpec.IntValue LP_RETRY_ATTEMPTS = t(
    "luckPermsRetryAttempts"
  ).defineInRange("luckPermsRetryAttempts", 3, 0, 10);
  public static final ModConfigSpec.IntValue LP_RETRY_DELAY_MS = t(
    "luckPermsRetryDelayMs"
  ).defineInRange("luckPermsRetryDelayMs", 1000, 100, 60000);
  public static final ModConfigSpec.DoubleValue MOCK_FAILURE_CHANCE = t(
    "mockFailureChance"
  )
    .comment(
      "MOCK backend only: chance (0-1) a grant fails, to test retry/error handling."
    )
    .defineInRange("mockFailureChance", 0.0, 0.0, 1.0);

  public static final ModConfigSpec.BooleanValue DISCORD_ENABLED;
  public static final ModConfigSpec.ConfigValue<
    String
  > DISCORD_WEBHOOK_URL, DISCORD_PING_ROLE_ID, DISCORD_USERNAME, DISCORD_FOOTER;
  public static final ModConfigSpec.BooleanValue DISCORD_POST_OUTCOMES;
  public static final ModConfigSpec.IntValue DISCORD_RETRY_ATTEMPTS;

  // Messages: & color codes. Placeholders: {id} {player} {permission} {duration} {reason} {admin} {note} {max}
  public static final ModConfigSpec.ConfigValue<String> PREFIX, NOTE_FORMAT;
  public static final ModConfigSpec.ConfigValue<
    String
  > MSG_USAGE, MSG_REQUEST_SENT, MSG_NOT_STAFF, MSG_NOT_ALLOWED, MSG_UNKNOWN_PERMISSION, MSG_INVALID_DURATION, MSG_REASON_REQUIRED, MSG_REASON_TOO_LONG, MSG_COOLDOWN, MSG_TOO_MANY, MSG_ADMIN_NOTIFY, MSG_APPROVED_PLAYER, MSG_APPROVED_ADMIN, MSG_DENIED_PLAYER, MSG_DENIED_ADMIN, MSG_EXPIRED, MSG_NO_PENDING, MSG_NONE_AVAILABLE, MSG_SELF_APPROVAL, MSG_BACKEND_DOWN, MSG_GRANT_FAILED, MSG_ERROR, MSG_RELOADED, MSG_NO_CLIENT;

  private static ModConfigSpec.ConfigValue<String> msg(String key, String def) {
    return t("msg." + key).define(key, def);
  }

  static {
    t("discord").push("discord");
    DISCORD_ENABLED = t("discord.enabled").define("enabled", false);
    DISCORD_WEBHOOK_URL = t("discord.webhookUrl")
      .comment(
        "Discord webhook URL. Treat this like a password; never share this file."
      )
      .define("webhookUrl", "");
    DISCORD_PING_ROLE_ID = t("discord.pingRoleId")
      .comment(
        "Numeric role ID to ping on new requests (digits only). Empty = no ping."
      )
      .define("pingRoleId", "");
    DISCORD_USERNAME = t("discord.username").define("username", "Clover");
    DISCORD_FOOTER = t("discord.footer")
      .comment("Embed footer. {id} = request ID.")
      .define(
        "footer",
        "Console: acperm accept {id}  |  acperm deny {id} <reason>"
      );
    DISCORD_POST_OUTCOMES = t("discord.postOutcomes")
      .comment("Also post when a request is approved, denied or expires.")
      .define("postOutcomes", true);
    DISCORD_RETRY_ATTEMPTS = t("discord.retryAttempts").defineInRange(
      "retryAttempts",
      4,
      0,
      10
    );
    B.pop();

    t("messages").push("messages");
    PREFIX = msg("prefix", "&8[&aClover&8] &r");
    NOTE_FORMAT = t("msg.noteFormat")
      .comment("Appended as {note} when an admin gives a note/reason.")
      .define("noteFormat", " &7Reason: &f{note}");
    MSG_USAGE = msg(
      "usage",
      "&cUsage: /rqperm <permission> <duration> <reason>   (see /rqperm list)"
    );
    MSG_REQUEST_SENT = msg(
      "requestSent",
      "&7Request &e{id} &7sent: &f{permission} &7for &f{duration}&7. If no admin is online, give them this ID: &f/acperm accept {id}"
    );
    MSG_NOT_STAFF = msg("notStaff", "&cYou are not in a staff group.");
    MSG_NOT_ALLOWED = msg(
      "notAllowed",
      "&cYour group can't request that permission."
    );
    MSG_UNKNOWN_PERMISSION = msg(
      "unknownPermission",
      "&cThat permission can't be requested. Try &f/rqperm list&c."
    );
    MSG_INVALID_DURATION = msg(
      "invalidDuration",
      "&cInvalid duration. Use e.g. &f10m&c or &f1h30m&c (max for this permission: &f{max}&c)."
    );
    MSG_REASON_REQUIRED = msg(
      "reasonRequired",
      "&cPlease give a reason: &f/rqperm <permission> <duration> <reason>"
    );
    MSG_REASON_TOO_LONG = msg(
      "reasonTooLong",
      "&cYour reason is too long (max {max} characters)."
    );
    MSG_COOLDOWN = msg("cooldown", "&cWait a moment before requesting again.");
    MSG_TOO_MANY = msg("tooMany", "&cYou have too many pending requests.");
    MSG_ADMIN_NOTIFY = msg(
      "adminNotify",
      "&e{player} &7wants to have &f{permission} &7for &f{duration} &7because of reason: &f{reason}"
    );
    MSG_APPROVED_PLAYER = msg(
      "approvedPlayer",
      "&aYour request &e{id} &afor &f{permission} &a({duration}) was approved by &e{admin}&a.{note}"
    );
    MSG_APPROVED_ADMIN = msg(
      "approvedAdmin",
      "&aApproved &e{id}&a: &f{player} &7-> &f{permission} &a({duration})."
    );
    MSG_DENIED_PLAYER = msg(
      "deniedPlayer",
      "&cYour request &e{id} &cfor &f{permission} &cwas denied by &e{admin}&c.{note}"
    );
    MSG_DENIED_ADMIN = msg(
      "deniedAdmin",
      "&7Denied &e{id}&7 (&f{player} &7-> &f{permission}&7)."
    );
    MSG_EXPIRED = msg(
      "expired",
      "&7Your request &e{id} &7for &f{permission} &7expired."
    );
    MSG_NO_PENDING = msg("noPending", "&cNo pending request with ID &e{id}&c.");
    MSG_NONE_AVAILABLE = msg(
      "noneAvailable",
      "&7There are no permissions you can request."
    );
    MSG_SELF_APPROVAL = msg(
      "selfApproval",
      "&cYou can't approve your own request."
    );
    MSG_BACKEND_DOWN = msg(
      "backendDown",
      "&cThe permission system isn't available right now. Try again shortly."
    );
    MSG_GRANT_FAILED = msg(
      "grantFailed",
      "&cCouldn't apply &e{permission} &cfor &e{player} &cafter several tries; request &e{id} &cwas kept. Check the server log."
    );
    MSG_ERROR = msg(
      "error",
      "&cSomething went wrong. It was logged; please try again."
    );
    MSG_RELOADED = msg("reloaded", "&aReloaded {max} permission entries.");
    MSG_NO_CLIENT = msg(
      "noClient",
      "&cThe Clover menu needs the Clover mod on your client. Use &f/rqperm&c (staff) or &f/acperm&c (admins) instead."
    );
    B.pop();
  }

  static final ModConfigSpec SPEC = B.build();
}
