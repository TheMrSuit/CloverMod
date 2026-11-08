package com.dracolich777.clover.client;

import com.dracolich777.clover.net.RequestPayload;
import com.dracolich777.clover.net.SyncPayload;
import com.dracolich777.clover.net.SyncPayload.EntryDto;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.Text;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RequestScreen extends CloverScreen {

  private List<EntryDto> entries;
  private final Map<String, Integer> selected = new HashMap<>();

  public RequestScreen(SyncPayload p) {
    super(
      Component.literal("Request Permissions"),
      SyncPayload.MODE_REQUEST,
      Component.literal("Reason for the request")
    );
    this.entries = p.entries();
  }

  public void update(SyncPayload p) {
    this.entries = p.entries();
    refresh();
  }

  @Override
  protected int rowCount() {
    return entries.size();
  }

  @Override
  protected int rowHeight() {
    return 24;
  }

  @Override
  protected Component emptyText() {
    return Component.literal("No permissions available to you");
  }

  @Override
  protected void buildRow(int index, int x, int y) {
    EntryDto e = entries.get(index);

    MutableComponent label = Text.color("&f" + e.node());
    if (!e.pendingCode().isEmpty()) label.append(
      Text.color(" &6(pending " + e.pendingCode() + ")")
    );
    StringWidget name = addRenderableWidget(
      new StringWidget(x, y, 150, 20, label, font).alignLeft()
    );
    if (!e.description().isBlank()) name.setTooltip(
      Tooltip.create(Text.color(e.description()))
    );

    if (e.durations().isEmpty()) return;
    addRenderableWidget(
      Button.builder(Component.literal(durationText(e)), b -> {
        selected.merge(e.node(), 1, (o, n) -> (o + 1) % e.durations().size());
        b.setMessage(Component.literal(durationText(e)));
      })
        .bounds(x + 154, y, 60, 20)
        .build()
    );

    addRenderableWidget(
      Button.builder(Component.literal("Request"), b ->
        PacketDistributor.sendToServer(
          new RequestPayload(e.node(), e.durations().get(sel(e)), inputText)
        )
      )
        .bounds(x + 218, y, 72, 20)
        .build()
    );
  }

  private int sel(EntryDto e) {
    return Math.min(
      selected.getOrDefault(e.node(), 0),
      Math.max(0, e.durations().size() - 1)
    );
  }

  private String durationText(EntryDto e) {
    String raw = e.durations().get(sel(e));
    Duration d = Durations.parse(raw);
    return d != null ? Durations.format(d) : raw;
  }
}
