package com.dracolich777.clover.client;

import com.dracolich777.clover.net.SyncPayload;
import com.dracolich777.clover.net.SyncPayload.TemporaryDto;
import com.dracolich777.clover.net.ClosePayload;
import com.dracolich777.clover.util.Durations;
import com.dracolich777.clover.util.NodeNames;
import java.time.Duration;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class PermissionInfoScreen extends Screen {
  private List<TemporaryDto> temporary;

  public PermissionInfoScreen(SyncPayload payload) {
    super(Component.literal("Temporary Permissions"));
    temporary = payload.temporary();
  }

  public void update(SyncPayload payload) {
    temporary = payload.temporary();
  }

  @Override
  protected void init() {
    addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
      .bounds(width / 2 - 50, height - 30, 100, 20).build());
  }

  @Override
  public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    super.render(graphics, mouseX, mouseY, partialTick);
    graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
    if (temporary.isEmpty()) {
      graphics.drawCenteredString(font, Component.literal("No temporary permissions are active"),
        width / 2, 50, 0xAAAAAA);
    } else {
      long now = System.currentTimeMillis();
      int y = 45;
      for (TemporaryDto permission : temporary) {
        long remaining = Math.max(0, permission.expiresAtMs() - now);
        graphics.drawString(font, Component.literal(NodeNames.friendly(permission.node())),
          width / 2 - 140, y, 0xFFFFFF);
        graphics.drawString(font, Component.literal(permission.node()),
          width / 2 - 140, y + 12, 0x808080);
        Component time = Component.literal(Durations.format(Duration.ofMillis(remaining)));
        graphics.drawString(font, time,
          width / 2 + 140 - font.width(time), y + 6, remaining == 0 ? 0xFF5555 : 0x55FF55);
        y += 30;
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
      PacketDistributor.sendToServer(new ClosePayload(SyncPayload.MODE_INFO));
    } catch (Exception | LinkageError ignored) {
    }
  }
}
