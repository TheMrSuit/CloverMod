package com.dracolich777.clover.client;

import com.dracolich777.clover.net.ClosePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

abstract class CloverScreen extends Screen {

  protected static final int ROW_W = 290,
    TOP = 40,
    BOTTOM = 66;

  private final byte mode;
  private final Component hint;
  protected int page;
  /** One instance for the screen's life, so typing/focus survive server updates. */
  protected EditBox input;
  protected String inputText = "";

  protected CloverScreen(Component title, byte mode, Component hint) {
    super(title);
    this.mode = mode;
    this.hint = hint;
  }

  protected abstract int rowCount();

  protected abstract int rowHeight();

  protected abstract void buildRow(int index, int x, int y);

  protected abstract Component emptyText();

  protected int perPage() {
    return Math.max(1, (height - TOP - BOTTOM) / rowHeight());
  }

  protected int pages() {
    return Math.max(1, (rowCount() + perPage() - 1) / perPage());
  }

  @Override
  protected void init() {
    page = Math.max(0, Math.min(page, pages() - 1));
    int left = (width - ROW_W) / 2;
    int start = page * perPage();
    for (int i = 0; i < perPage() && start + i < rowCount(); i++) {
      buildRow(start + i, left, TOP + i * rowHeight());
    }

    if (input == null) {
      input = new EditBox(font, left, height - 52, ROW_W, 20, hint);
      input.setHint(hint);
      input.setMaxLength(400);
      input.setResponder(s -> inputText = s);
    }
    input.setX(left);
    input.setY(height - 52);
    input.setWidth(ROW_W);
    addRenderableWidget(input);

    int by = height - 28;
    Button prev = addRenderableWidget(
      Button.builder(Component.literal("<"), b -> {
        page--;
        rebuildWidgets();
      })
        .bounds(width / 2 - 100, by, 40, 20)
        .build()
    );
    prev.active = page > 0;
    addRenderableWidget(
      Button.builder(CommonComponents.GUI_DONE, b -> onClose())
        .bounds(width / 2 - 50, by, 100, 20)
        .build()
    );
    Button next = addRenderableWidget(
      Button.builder(Component.literal(">"), b -> {
        page++;
        rebuildWidgets();
      })
        .bounds(width / 2 + 60, by, 40, 20)
        .build()
    );
    next.active = page < pages() - 1;
  }

  /** Re-layout after new data, keeping what the player is typing and their focus. */
  protected void refresh() {
    boolean typing = input != null && input.isFocused();
    rebuildWidgets();
    if (typing) setFocused(input);
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    super.renderBackground(g, mx, my, pt); // vanilla menu darkening / blur
    int left = (width - ROW_W) / 2;
    g.fill(
      left - 6,
      TOP - 6,
      left + ROW_W + 6,
      height - BOTTOM + 4,
      0x55000000
    );
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float pt) {
    super.render(g, mx, my, pt);
    g.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
    if (rowCount() == 0) g.drawCenteredString(
      font,
      emptyText(),
      width / 2,
      TOP + 20,
      0xAAAAAA
    );
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  public void removed() {
    super.removed();
    try {
      PacketDistributor.sendToServer(new ClosePayload(mode));
    } catch (Exception | LinkageError ignored) {
      // the server prunes dead viewers itself
    }
  }
}
