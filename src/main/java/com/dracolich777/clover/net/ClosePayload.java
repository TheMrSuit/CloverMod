package com.dracolich777.clover.net;

import com.dracolich777.clover.util.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client closed a Clover screen of this mode, so the server stops pushing updates. */
public record ClosePayload(byte mode) implements CustomPacketPayload {
  public static final Type<ClosePayload> TYPE = Compat.payloadType("close");
  public static final StreamCodec<RegistryFriendlyByteBuf, ClosePayload> CODEC =
    StreamCodec.ofMember(ClosePayload::write, ClosePayload::read);

  private void write(FriendlyByteBuf b) {
    b.writeByte(mode);
  }

  private static ClosePayload read(FriendlyByteBuf b) {
    return new ClosePayload(b.readByte());
  }

  @Override
  public Type<ClosePayload> type() {
    return TYPE;
  }
}
