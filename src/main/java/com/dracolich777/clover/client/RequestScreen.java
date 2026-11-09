package com.dracolich777.clover.client;

import com.dracolich777.clover.net.RequestPayload;
import com.dracolich777.clover.net.SearchPayload;
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
  private int total;
  private String shownQuery;
  private final Map<String, Integer> selected = new HashMap<>();

  public RequestScreen(SyncPayload p) {
    super(
      Component.literal("Request Permissions"),
      SyncPayload.MODE_REQUEST,
      Component.literal("Reason for the request"),
      Component.literal("Search permissions (e.g. gamemode, teleport, fly)")
    );
    apply(p);
  }

  private void apply(SyncPayload p) {
    entries = p.entries();
    total = p.total();
    shownQuery = p.query();
  }

  public void update(SyncPayload p) {
    // ignore results for a query the player has already typed past; a newer response is on its way
    if (!normalize(searchText).equalsIgnoreCase(p.query())) return;
    boolean newQuery = !p.query().equalsIgnoreCase(shownQuery);
    apply(p);
    if (newQuery) page = 0;
    refresh();
  }

  @Override
  protected void onSearch(String query) {
    PacketDistributor.sendToServer(new SearchPayload(query));
  }

  @Override
  protected int rowCount() {
    return entries.size();
  }

  @Override
  protected int rowHeight() {
    return 30;
  }

  @Override
  protected Component emptyText() {
    return Component.literal(
      shownQuery.isEmpty()
        ? "No permissions available to you"
        : "No permissions match your search"
    );
  }

  @Override
  protected Component headerLine() {
    if (total > entries.size()) {
      return Component.literal(
        "Showing the best " +
          entries.size() +
          " of " +
          total +
          " matches - keep typing to narrow it down"
      );
    }
    return Component.literal(
      total +
        (shownQuery.isEmpty()
          ? " permissions"
          : (total == 1 ? " match" : " matches"))
    );
  }

  @Override
  protected void buildRow(int index, int x, int y) {
    EntryDto e = entries.get(index);

    MutableComponent title = Text.color("&f" + e.title());
    if (!e.pendingCode().isEmpty()) title.append(
      Text.color(" &6(pending " + e.pendingCode() + ")")
    );
    StringWidget name = addRenderableWidget(
      new StringWidget(x, y, 150, 14, title, font).alignLeft()
    );
    StringWidget node = addRenderableWidget(
      new StringWidget(
        x,
        y + 14,
        150,
        12,
        Text.color("&8" + e.node()),
        font
      ).alignLeft()
    );
    String tip =
      "&7" +
      e.node() +
      (e.description().isBlank() ? "" : "\n&f" + e.description());
    Tooltip tooltip = Tooltip.create(Text.color(tip));
    name.setTooltip(tooltip);
    node.setTooltip(tooltip);

    if (e.durations().isEmpty()) return;
    addRenderableWidget(
      Button.builder(Component.literal(durationText(e)), b -> {
        selected.merge(e.node(), 1, (o, n) -> (o + 1) % e.durations().size());
        b.setMessage(Component.literal(durationText(e)));
      })
        .bounds(x + 154, y + 4, 60, 20)
        .build()
    );

    addRenderableWidget(
      Button.builder(Component.literal("Request"), b ->
        PacketDistributor.sendToServer(
          new RequestPayload(e.node(), e.durations().get(sel(e)), inputText)
        )
      )
        .bounds(x + 218, y + 4, 72, 20)
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
