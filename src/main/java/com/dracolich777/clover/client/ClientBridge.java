package com.dracolich777.clover.client;

import com.dracolich777.clover.net.SyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientBridge {

  private ClientBridge() {}

  public static void onSync(SyncPayload p) {
    Minecraft mc = Minecraft.getInstance();
    Screen cur = mc.screen;
    if (p.mode() == SyncPayload.MODE_ADMIN) {
      if (cur instanceof AdminScreen s) s.update(p);
      else if (p.open()) mc.setScreen(new AdminScreen(p));
    } else {
      if (cur instanceof RequestScreen s) s.update(p);
      else if (p.open()) mc.setScreen(new RequestScreen(p));
    }
    // Updates (open == false) for a screen that's no longer showing are ignored.
  }
}
