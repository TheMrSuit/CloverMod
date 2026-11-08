package com.dracolich777.clover.client;

import com.dracolich777.clover.net.DecisionPayload;
import com.dracolich777.clover.net.SyncPayload;
import com.dracolich777.clover.net.SyncPayload.PendingDto;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.Text;
import java.time.Duration;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class AdminScreen extends CloverScreen {

  private List<PendingDto> pending;

  public AdminScreen(SyncPayload p) {
    super(
      Component.literal("Pending Permission Requests"),
      SyncPayload.MODE_ADMIN,
      Component.literal(
        "Optional note for the player (sent with Accept / Deny)"
      )
    );
    this.pending = p.pending();
  }

  public void update(SyncPayload p) {
    this.pending = p.pending();
    refresh();
  }

  @Override
  protected int rowCount() {
    return pending.size();
  }

  @Override
  protected int rowHeight() {
    return 36;
  }

  @Override
  protected Component emptyText() {
    return Component.literal("No pending requests");
  }

  @Override
  protected void buildRow(int index, int x, int y) {
    PendingDto r = pending.get(index);
    String dur = Durations.format(Duration.ofSeconds(r.durationSeconds()));

    addRenderableWidget(
      new StringWidget(
        x,
        y,
        220,
        16,
        Text.color(
          "&e" +
            r.playerName() +
            " &7-> &f" +
            r.node() +
            " &8(" +
            dur +
            ") &6[" +
            r.code() +
            "]"
        ),
        font
      ).alignLeft()
    );

    String reasonText = r.reason().isEmpty() ? "(no reason given)" : r.reason();
    StringWidget reason = addRenderableWidget(
      new StringWidget(
        x,
        y + 16,
        220,
        16,
        Text.color("&7" + Text.esc(reasonText)),
        font
      ).alignLeft()
    );
    reason.setTooltip(Tooltip.create(Component.literal(reasonText)));

    Button[] pair = new Button[2];
    pair[0] = Button.builder(
      Component.literal("Accept").withStyle(ChatFormatting.GREEN),
      b -> decide(r, true, pair)
    )
      .bounds(x + 224, y, 66, 16)
      .build();
    pair[1] = Button.builder(
      Component.literal("Deny").withStyle(ChatFormatting.RED),
      b -> decide(r, false, pair)
    )
      .bounds(x + 224, y + 18, 66, 16)
      .build();
    addRenderableWidget(pair[0]);
    addRenderableWidget(pair[1]);
  }

  private void decide(PendingDto r, boolean approve, Button[] pair) {
    pair[0].active = false; // stop double-clicks; the server pushes the new list right after
    pair[1].active = false;
    PacketDistributor.sendToServer(
      new DecisionPayload(r.code(), approve, inputText)
    );
    input.setValue(""); // the note belonged to this decision
  }
}
