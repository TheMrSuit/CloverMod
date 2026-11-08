package com.dracolich777.clover.util;

import com.dracolich777.clover.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class Text {

  private Text() {}

  public static String fill(String t, String... kv) {
    for (int i = 0; i + 1 < kv.length; i += 2) t = t.replace(
      "{" + kv[i] + "}",
      kv[i + 1]
    );
    return t;
  }

  /** Escape player-supplied text before putting it in a template: '&' becomes '&&'. */
  public static String esc(String s) {
    return s == null ? "" : s.replace("&", "&&");
  }

  public static String strip(String s) {
    return s == null ? "" : s.replaceAll("(?i)&[0-9a-fk-or]", "");
  }

  /** &-codes to a styled component. '&&' is a literal '&'. */
  public static MutableComponent color(String s) {
    MutableComponent out = Component.empty();
    Style style = Style.EMPTY;
    StringBuilder buf = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '&' && i + 1 < s.length()) {
        char n = s.charAt(i + 1);
        if (n == '&') {
          buf.append('&');
          i++;
          continue;
        }
        ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(n));
        if (f != null) {
          flush(out, buf, style);
          style = style.applyLegacyFormat(f);
          i++;
          continue;
        }
      }
      buf.append(c);
    }
    flush(out, buf, style);
    return out;
  }

  private static void flush(
    MutableComponent out,
    StringBuilder buf,
    Style style
  ) {
    if (buf.length() > 0) {
      out.append(Component.literal(buf.toString()).withStyle(style));
      buf.setLength(0);
    }
  }

  public static MutableComponent msg(
    ModConfigSpec.ConfigValue<String> cfg,
    String... kv
  ) {
    return color(Config.PREFIX.get() + fill(cfg.get(), kv));
  }
}
