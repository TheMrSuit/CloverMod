package com.dracolich777.clover.net;

import com.dracolich777.clover.util.Compat;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SyncPayload(
  boolean open,
  byte mode,
  List<EntryDto> entries,
  List<PendingDto> pending,
  int total,
  String query
) implements CustomPacketPayload {
  public static final byte MODE_REQUEST = 0,
    MODE_ADMIN = 1;
  public static final int MAX_ENTRIES = 100,
    MAX_PENDING = 300,
    MAX_DURATIONS = 21;

  public record EntryDto(
    String node,
    String title,
    String description,
    List<String> durations,
    String pendingCode
  ) {}

  public record PendingDto(
    String code,
    String playerName,
    String node,
    long durationSeconds,
    String reason,
    long ageSeconds
  ) {}

  public static final Type<SyncPayload> TYPE = Compat.payloadType("sync");
  public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC =
    StreamCodec.ofMember(SyncPayload::write, SyncPayload::read);

  @Override
  public Type<SyncPayload> type() {
    return TYPE;
  }

  private void write(FriendlyByteBuf b) {
    b.writeBoolean(open);
    b.writeByte(mode);
    b.writeVarInt(entries.size());
    for (EntryDto e : entries) {
      b.writeUtf(e.node(), 128);
      b.writeUtf(e.title(), 128);
      b.writeUtf(e.description(), 256);
      b.writeVarInt(e.durations().size());
      for (String d : e.durations()) b.writeUtf(d, 32);
      b.writeUtf(e.pendingCode(), 8);
    }
    b.writeVarInt(pending.size());
    for (PendingDto p : pending) {
      b.writeUtf(p.code(), 8);
      b.writeUtf(p.playerName(), 64);
      b.writeUtf(p.node(), 128);
      b.writeLong(p.durationSeconds());
      b.writeUtf(p.reason(), 512);
      b.writeLong(p.ageSeconds());
    }
    b.writeVarInt(total);
    b.writeUtf(query, 64);
  }

  private static SyncPayload read(FriendlyByteBuf b) {
    boolean open = b.readBoolean();
    byte mode = b.readByte();
    int n = checked(b.readVarInt(), MAX_ENTRIES);
    List<EntryDto> entries = new ArrayList<>(n);
    for (int i = 0; i < n; i++) {
      String node = b.readUtf(128);
      String title = b.readUtf(128);
      String desc = b.readUtf(256);
      int dn = checked(b.readVarInt(), MAX_DURATIONS);
      List<String> durs = new ArrayList<>(dn);
      for (int j = 0; j < dn; j++) durs.add(b.readUtf(32));
      entries.add(new EntryDto(node, title, desc, durs, b.readUtf(8)));
    }
    int m = checked(b.readVarInt(), MAX_PENDING);
    List<PendingDto> pending = new ArrayList<>(m);
    for (int i = 0; i < m; i++) {
      pending.add(
        new PendingDto(
          b.readUtf(8),
          b.readUtf(64),
          b.readUtf(128),
          b.readLong(),
          b.readUtf(512),
          b.readLong()
        )
      );
    }
    int total = Math.max(0, b.readVarInt());
    return new SyncPayload(open, mode, entries, pending, total, b.readUtf(64));
  }

  private static int checked(int n, int max) {
    if (n < 0 || n > max) throw new DecoderException(
      "Clover: list size out of range: " + n
    );
    return n;
  }
}
