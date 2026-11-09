package com.dracolich777.clover.client;

import com.dracolich777.clover.net.ClosePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

abstract class CloverScreen extends Screen {

  protected static final int ROW_W = 290,
    BOTTOM = 66;

  private final byte mode;
  private final Component hint;
  private final Component searchHint; // null = no search box

  protected int page;
  /** Bottom box: request reason (staff) or note (admin). One instance for the screen's life. */
  protected EditBox input;
  /** Top box (request screen only). */
  protected EditBox search;
  protected String inputText = "";
  protected String searchText = "";

  private long searchDirtyAt = -1;
  private String lastSent = "";
  private boolean focusedOnce;

  protected CloverScreen(
    Component title,
    byte mode,
    Component hint,
    Component searchHint
  ) {
    super(title);
    this.mode = mode;
    this.hint = hint;
    this.searchHint = searchHint;
  }

  protected abstract int rowCount();

  protected abstract int rowHeight();

  protected abstract void buildRow(int index, int x, int y);

  protected abstract Component emptyText();

  protected void onSearch(String query) {}

  protected Component headerLine() {
    return null;
  }

  protected final boolean hasSearch() {
    return searchHint != null;
  }

  protected int top() {
    return hasSearch() ? 70 : 40;
  }

  protected static String normalize(String s) {
    String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
    return t.length() > 64 ? t.substring(0, 64) : t;
  }

  protected int perPage() {
    return Math.max(1, (height - top() - BOTTOM) / rowHeight());
  }

  protected int pages() {
    return Math.max(1, (rowCount() + perPage() - 1) / perPage());
  }

  @Override
  protected void init() {
    page = Math.max(0, Math.min(page, pages() - 1));
    int left = (width - ROW_W) / 2;

    if (hasSearch()) {
      if (search == null) {
        search = new EditBox(font, left, 26, ROW_W, 18, searchHint);
        search.setHint(searchHint);
        search.setMaxLength(64);
        search.setResponder(s -> {
          searchText = s;
          searchDirtyAt = System.currentTimeMillis();
        });
      }
      search.setX(left);
      search.setY(26);
      search.setWidth(ROW_W);
      addRenderableWidget(search);
    }

    int start = page * perPage();
    for (int i = 0; i < perPage() && start + i < rowCount(); i++) {
      buildRow(start + i, left, top() + i * rowHeight());
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

    if (hasSearch() && !focusedOnce) {
      // start typing straight away
      focusedOnce = true;
      setFocused(search);
    }
  }

  /** Re-layout after new data, keeping what the player is typing and which box has focus. */
  protected void refresh() {
    GuiEventListener f = getFocused();
    rebuildWidgets();
    if (f instanceof EditBox eb && (eb == input || eb == search)) setFocused(
      eb
    );
  }

  @Override
  public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    super.renderBackground(g, mx, my, pt); // vanilla menu darkening / blur
    int left = (width - ROW_W) / 2;
    g.fill(
      left - 6,
      top() - 6,
      left + ROW_W + 6,
      height - BOTTOM + 4,
      0x55000000
    );
  }

  @Override
  public void render(GuiGraphics g, int mx, int my, float pt) {
    super.render(g, mx, my, pt);
    g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
    Component header = headerLine();
    if (header != null) g.drawString(
      font,
      header,
      (width - ROW_W) / 2,
      top() - 16,
      0xA0A0A0
    );
    if (rowCount() == 0) g.drawCenteredString(
      font,
      emptyText(),
      width / 2,
      top() + 20,
      0xAAAAAA
    );
  }

  @Override
  public void tick() {
    super.tick();
    // debounce: send the search 250ms after the last keystroke
    if (
      searchDirtyAt >= 0 && System.currentTimeMillis() - searchDirtyAt >= 250
    ) {
      searchDirtyAt = -1;
      String q = normalize(searchText);
      if (!q.equalsIgnoreCase(lastSent)) {
        lastSent = q;
        onSearch(q);
      }
    }
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
