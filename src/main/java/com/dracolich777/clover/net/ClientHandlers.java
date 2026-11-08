package com.dracolich777.clover.net;

import com.dracolich777.clover.client.ClientBridge;
import com.dracolich777.clover.clover;
import com.dracolich777.clover.util.Compat;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientHandlers {

  private ClientHandlers() {}

  public static void onSync(SyncPayload p, IPayloadContext ctx) {
    if (!Compat.isClient()) return;
    try {
      ClientBridge.onSync(p);
    } catch (Exception | LinkageError e) {
      clover.LOGGER.error("[Clover] Failed to show screen", e);
    }
  }
}
