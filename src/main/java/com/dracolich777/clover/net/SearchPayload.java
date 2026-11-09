package com.dracolich777.clover.net;

import com.dracolich777.clover.util.Compat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SearchPayload(String query) implements CustomPacketPayload {
  public static final Type<SearchPayload> TYPE = Compat.payloadType("search");
  public static final StreamCodec<
    RegistryFriendlyByteBuf,
    SearchPayload
  > CODEC = StreamCodec.ofMember(SearchPayload::write, SearchPayload::read);

  private void write(FriendlyByteBuf b) {
    b.writeUtf(query, 64);
  }

  private static SearchPayload read(FriendlyByteBuf b) {
    return new SearchPayload(b.readUtf(64));
  }

  @Override
  public Type<SearchPayload> type() {
    return TYPE;
  }
}
