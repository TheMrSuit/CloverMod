package com.dracolich777.clover.net;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {

  private Net() {}

  public static void register(RegisterPayloadHandlersEvent e) {
 
    PayloadRegistrar r = e.registrar("1").optional();
    r.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, ClientHandlers::onSync);
    r.playToServer(
      RequestPayload.TYPE,
      RequestPayload.CODEC,
      ServerHandlers::onRequest
    );
    r.playToServer(
      DecisionPayload.TYPE,
      DecisionPayload.CODEC,
      ServerHandlers::onDecision
    );
    r.playToServer(
      SearchPayload.TYPE,
      SearchPayload.CODEC,
      ServerHandlers::onSearch
    );
    r.playToServer(
      ClosePayload.TYPE,
      ClosePayload.CODEC,
      ServerHandlers::onClose
    );
  }
}
