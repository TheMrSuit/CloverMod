package com.dracolich777.clover.request;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RequestManager {

  private static final RequestManager INSTANCE = new RequestManager();

  public static RequestManager get() {
    return INSTANCE;
  }

  /** Consonants only, so a code can never spell a word. */
  private static final String ALPHABET = "BCDFGHJKLMNPQRSTVWXZ";

  public enum Result {
    OK,
    TOO_MANY,
    COOLDOWN,
  }

  public record Outcome(Result result, PermissionRequest request) {}

  private final Map<String, PermissionRequest> byCode = new LinkedHashMap<>();
  private final Map<UUID, Long> lastSubmit = new HashMap<>();
  private int version;

  public Outcome submit(
    UUID player,
    String name,
    String node,
    Duration d,
    String reason,
    int maxPending,
    long cooldownMs,
    long now
  ) {
    Long last = lastSubmit.get(player);
    if (last != null && now - last < cooldownMs) return new Outcome(
      Result.COOLDOWN,
      null
    );

    String duplicate = null;
    int mine = 0;
    for (PermissionRequest r : byCode.values()) {
      if (!r.player().equals(player)) continue;
      mine++;
      if (r.node().equalsIgnoreCase(node)) duplicate = r.code();
    }
    if (duplicate == null && mine >= maxPending) return new Outcome(
      Result.TOO_MANY,
      null
    );
    if (duplicate != null) byCode.remove(duplicate); // re-requesting the same node replaces the old one

    PermissionRequest req = new PermissionRequest(
      newCode(),
      player,
      name,
      node,
      d,
      reason,
      now
    );
    byCode.put(req.code(), req);
    lastSubmit.put(player, now);
    version++;
    return new Outcome(Result.OK, req);
  }

  private String newCode() {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    for (int attempt = 0; attempt < 200; attempt++) {
      char[] c = new char[4];
      for (int i = 0; i < 4; i++) c[i] = ALPHABET.charAt(
        rnd.nextInt(ALPHABET.length())
      );
      String s = new String(c);
      if (!byCode.containsKey(s)) return s;
    }
    throw new IllegalStateException("no free request codes");
  }

  public PermissionRequest get(String code) {
    return code == null ? null : byCode.get(code.toUpperCase(Locale.ROOT));
  }

  public PermissionRequest take(String code) {
    PermissionRequest r =
      code == null ? null : byCode.remove(code.toUpperCase(Locale.ROOT));
    if (r != null) version++;
    return r;
  }

  /** Re-add after a failed grant (bypasses cooldown/limits). */
  public void restore(PermissionRequest r) {
    byCode.put(r.code(), r);
    version++;
  }

  public List<PermissionRequest> all() {
    List<PermissionRequest> out = new ArrayList<>(byCode.values());
    out.sort(Comparator.comparingLong(PermissionRequest::createdAtMs));
    return out;
  }

  public void removeAll(UUID player) {
    lastSubmit.remove(player);
    if (byCode.values().removeIf(r -> r.player().equals(player))) version++;
  }

  public List<PermissionRequest> expire(long cutoffMs) {
    List<PermissionRequest> gone = new ArrayList<>();
    byCode
      .values()
      .removeIf(r -> {
        if (r.createdAtMs() < cutoffMs) {
          gone.add(r);
          return true;
        }
        return false;
      });
    if (!gone.isEmpty()) version++;
    return gone;
  }

  public boolean isEmpty() {
    return byCode.isEmpty();
  }

  public int version() {
    return version;
  }

  public void clear() {
    byCode.clear();
    lastSubmit.clear();
    version++;
  }
}
