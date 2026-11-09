package com.dracolich777.clover;

import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.catalog.CatalogManager;
import com.dracolich777.clover.discord.Discord;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side config editing (/clover config) and "re-apply everything" after any config change. */
public final class ConfigService {

  private ConfigService() {}

  /** Re-read everything that caches config-derived state. @return catalog size, or -1 on failure. */
  public static int apply() {
    Backends.reset();
    int n = CatalogManager.load();
    Discord.logStatus();
    return n;
  }

  public static String display(Config.Key k) {
    Object v = k.value().get();
    if (k.secret()) return (v instanceof String s && !s.isBlank())
      ? "(set - hidden)"
      : "(empty)";
    if (v instanceof Iterable<?> it) {
      StringJoiner j = new StringJoiner(", ", "[", "]");
      for (Object o : it) j.add(String.valueOf(o));
      return j.toString();
    }
    return String.valueOf(v);
  }

  /** @return the new value as displayed. @throws IllegalArgumentException with a user-readable reason. */
  public static String set(String path, String raw) {
    Config.Key k = Config.keys().get(path);
    if (k == null) throw new IllegalArgumentException(
      "Unknown setting '" + path + "'. Try /clover config list"
    );
    if (k.secret()) {
      throw new IllegalArgumentException(
        "Edit this one in config/clover/clover-common.toml " +
          "(commands are logged and echoed to Discord console channels)."
      );
    }
    Object parsed = parse(k, raw.trim());
    setRaw(k, parsed);
    Config.SPEC.save();
    apply();
    return display(k);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static void setRaw(Config.Key k, Object parsed) {
    ((ModConfigSpec.ConfigValue) k.value()).set(parsed);
  }

  private static Object parse(Config.Key k, String raw) {
    ModConfigSpec.ConfigValue<?> v = k.value();
    if (v instanceof ModConfigSpec.BooleanValue) {
      if (raw.equalsIgnoreCase("true")) return true;
      if (raw.equalsIgnoreCase("false")) return false;
      throw new IllegalArgumentException("Expected true or false");
    }
    if (v instanceof ModConfigSpec.IntValue) {
      int n;
      try {
        n = Integer.parseInt(raw);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Expected a whole number");
      }
      if (n < k.min().intValue() || n > k.max().intValue()) {
        throw new IllegalArgumentException(
          "Must be between " + k.min() + " and " + k.max()
        );
      }
      return n;
    }
    if (v instanceof ModConfigSpec.DoubleValue) {
      double d;
      try {
        d = Double.parseDouble(raw);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Expected a number");
      }
      if (d < k.min().doubleValue() || d > k.max().doubleValue()) {
        throw new IllegalArgumentException(
          "Must be between " + k.min() + " and " + k.max()
        );
      }
      return d;
    }
    if (v instanceof ModConfigSpec.EnumValue<?> ev) {
      Object[] constants = (
        (Enum<?>) ev.getDefault()
      ).getDeclaringClass().getEnumConstants();
      StringJoiner names = new StringJoiner(", ");
      for (Object c : constants) {
        if (c.toString().equalsIgnoreCase(raw)) return c;
        names.add(c.toString());
      }
      throw new IllegalArgumentException("Expected one of: " + names);
    }
    Object def = v.getDefault();
    if (def instanceof List<?>) {
      List<String> out = new ArrayList<>();
      for (String s : raw.split(",")) {
        String t = s.trim();
        if (!t.isEmpty()) out.add(t);
      }
      return out;
    }
    if (def instanceof String) return raw;
    throw new IllegalArgumentException("Unsupported setting type");
  }
}
