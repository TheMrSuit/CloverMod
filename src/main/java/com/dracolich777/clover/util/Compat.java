package com.dracolich777.clover.util;

import com.dracolich777.clover.clover;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Everything version-specific lives here. Written for 1.21.1.
 *  - 1.21.5+: ClickEvent is a sealed interface -> new ClickEvent.RunCommand(cmd) / .SuggestCommand(cmd) / .CopyToClipboard(text)
 *  - 1.21.11 (I believe): ResourceLocation was renamed Identifier
 *  - newer FML may expose FMLEnvironment.getDist() instead of the dist field
 */
public final class Compat {

  private Compat() {}

  public static Style run(Style s, String command) {
    return s.withClickEvent(
      new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)
    );
  }

  public static Style suggest(Style s, String command) {
    return s.withClickEvent(
      new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command)
    );
  }

  public static Style copy(Style s, String text) {
    return s.withClickEvent(
      new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text)
    );
  }

  public static <T extends CustomPacketPayload> CustomPacketPayload.Type<
    T
  > payloadType(String path) {
    return new CustomPacketPayload.Type<>(
      ResourceLocation.fromNamespaceAndPath(clover.MODID, path)
    );
  }

  public static boolean isClient() {
    return FMLEnvironment.dist.isClient();
  }
}
