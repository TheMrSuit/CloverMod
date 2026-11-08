package com.dracolich777.clover.net;

import com.dracolich777.clover.util.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RequestPayload(
  String node,
  String duration,
  String reason
) implements CustomPacketPayload {
  public static final Type<RequestPayload> TYPE = Compat.payloadType("request");
  public static final StreamCodec<
    RegistryFriendlyByteBuf,
    RequestPayload
  > CODEC = StreamCodec.ofMember(RequestPayload::write, RequestPayload::read);

  private void write(FriendlyByteBuf b) {
    b.writeUtf(node, 128);
    b.writeUtf(duration, 32);
    b.writeUtf(reason, 512);
  }

  private static RequestPayload read(FriendlyByteBuf b) {
    return new RequestPayload(b.readUtf(128), b.readUtf(32), b.readUtf(512));
  }

  @Override
  public Type<RequestPayload> type() {
    return TYPE;
  }
}
