package com.dracolich777.clover.net;

import com.dracolich777.clover.util.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DecisionPayload(
  String code,
  boolean approve,
  String note
) implements CustomPacketPayload {
  public static final Type<DecisionPayload> TYPE = Compat.payloadType(
    "decision"
  );
  public static final StreamCodec<
    RegistryFriendlyByteBuf,
    DecisionPayload
  > CODEC = StreamCodec.ofMember(DecisionPayload::write, DecisionPayload::read);

  private void write(FriendlyByteBuf b) {
    b.writeUtf(code, 8);
    b.writeBoolean(approve);
    b.writeUtf(note, 512);
  }

  private static DecisionPayload read(FriendlyByteBuf b) {
    return new DecisionPayload(b.readUtf(8), b.readBoolean(), b.readUtf(512));
  }

  @Override
  public Type<DecisionPayload> type() {
    return TYPE;
  }
}
