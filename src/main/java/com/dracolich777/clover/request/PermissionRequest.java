package com.dracolich777.clover.request;

import java.time.Duration;
import java.util.UUID;

public record PermissionRequest(
  String code,
  UUID player,
  String playerName,
  String node,
  Duration duration,
  String reason,
  long createdAtMs
) {}
