package com.dracolich777.clover.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Durations {

  private static final Pattern PART = Pattern.compile("(\\d+)([smhd])");
  private static final long MAX_SECONDS = 365L * 86400L;

  private Durations() {}

  /** "30s", "10m", "1h30m", "2d". Returns null if invalid. */
  public static Duration parse(String s) {
    if (s == null) return null;
    String t = s.trim().toLowerCase(Locale.ROOT);
    if (t.isEmpty()) return null;
    Matcher m = PART.matcher(t);
    long secs = 0;
    int end = 0;
    while (m.find()) {
      if (m.start() != end || m.group(1).length() > 7) return null;
      long n = Long.parseLong(m.group(1));
      secs +=
        n *
        switch (m.group(2).charAt(0)) {
          case 's' -> 1L;
          case 'm' -> 60L;
          case 'h' -> 3600L;
          default -> 86400L;
        };
      end = m.end();
    }
    if (end != t.length() || secs <= 0 || secs > MAX_SECONDS) return null;
    return Duration.ofSeconds(secs);
  }

  /** "1h 30m" */
  public static String format(Duration d) {
    long s = Math.max(0, d.getSeconds());
    StringBuilder sb = new StringBuilder();
    if (s >= 86400) {
      sb.append(s / 86400).append("d ");
      s %= 86400;
    }
    if (s >= 3600) {
      sb.append(s / 3600).append("h ");
      s %= 3600;
    }
    if (s >= 60) {
      sb.append(s / 60).append("m ");
      s %= 60;
    }
    if (s > 0 || sb.length() == 0) sb.append(s).append("s");
    return sb.toString().trim();
  }

  /** "1 hour 30 minutes" */
  public static String formatLong(Duration d) {
    long s = Math.max(0, d.getSeconds());
    List<String> parts = new ArrayList<>();
    add(parts, s / 86400, "day");
    s %= 86400;
    add(parts, s / 3600, "hour");
    s %= 3600;
    add(parts, s / 60, "minute");
    s %= 60;
    add(parts, s, "second");
    return parts.isEmpty() ? "0 seconds" : String.join(" ", parts);
  }

  private static void add(List<String> p, long n, String unit) {
    if (n > 0) p.add(n + " " + unit + (n == 1 ? "" : "s"));
  }
}
