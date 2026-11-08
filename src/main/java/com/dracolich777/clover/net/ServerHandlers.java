package com.dracolich777.clover.net;

import com.dracolich777.clover.Config;
import com.dracolich777.clover.gui.GuiService;
import com.dracolich777.clover.request.RequestService;
import com.dracolich777.clover.util.Safe;
import com.dracolich777.clover.util.Text;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Every handler treats the client as untrusted: RequestService re-validates everything. */
public final class ServerHandlers {

  private ServerHandlers() {}

  public static void onRequest(RequestPayload p, IPayloadContext ctx) {
    if (!(ctx.player() instanceof ServerPlayer sp)) return;
    Safe.run("request packet", sp, () -> {
      // staff check, catalog, allowed group, duration cap, reason rules, cooldown: all in submit()
      RequestService.submit(sp, p.node(), p.duration(), p.reason());
      GuiService.resend(sp);
    });
  }

  public static void onDecision(DecisionPayload p, IPayloadContext ctx) {
    if (!(ctx.player() instanceof ServerPlayer sp)) return;
    Safe.run("decision packet", sp, () -> {
      if (!RequestService.isAdmin(sp)) {
        // re-checked on every packet
        sp.sendSystemMessage(Text.msg(Config.MSG_NOT_ALLOWED));
        return;
      }
      String who = sp.getName().getString();
      if (p.approve()) {
        RequestService.approve(
          sp.getServer(),
          p.code(),
          sp.getUUID(),
          who,
          p.note(),
          sp::sendSystemMessage
        );
      } else {
        RequestService.deny(
          sp.getServer(),
          p.code(),
          who,
          p.note(),
          sp::sendSystemMessage
        );
      }
      GuiService.resend(sp);
    });
  }

  public static void onClose(ClosePayload p, IPayloadContext ctx) {
    if (ctx.player() instanceof ServerPlayer sp) GuiService.closed(
      sp,
      p.mode()
    );
  }
}
