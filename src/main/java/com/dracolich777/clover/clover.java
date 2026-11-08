package com.dracolich777.clover;

import org.slf4j.Logger;

import com.dracolich777.clover.backend.Backends;
import com.dracolich777.clover.catalog.CatalogManager;
import com.dracolich777.clover.command.CloverCommands;
import com.dracolich777.clover.discord.Discord;
import com.dracolich777.clover.gui.GuiService;
import com.dracolich777.clover.net.Net;
import com.dracolich777.clover.request.RequestManager;
import com.dracolich777.clover.request.RequestService;
import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(clover.MODID)
public class clover {
    public static final String MODID = "clover";
    public static final Logger LOGGER = LogUtils.getLogger();

    private int ticks;

    public clover(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        modEventBus.addListener(Net::register);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent e) {
        try {
            CloverCommands.register(e.getDispatcher());
        } catch (Exception | LinkageError ex) {
            LOGGER.error("[Clover] Failed to register commands", ex);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent e) {
        int n = CatalogManager.load();
        LOGGER.info("[Clover] Loaded {} requestable permissions", Math.max(n, 0));
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent e) {
        RequestManager.get().clear();
        GuiService.clear();
        Backends.reset();
        Discord.shutdown();
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            GuiService.forget(p.getUUID());
            if (Config.CANCEL_ON_LOGOUT.get()) RequestManager.get().removeAll(p.getUUID());
        }
    }

    @SubscribeEvent
    public void onTick(ServerTickEvent.Post e) {
        try {
            GuiService.tick(e.getServer());
            if (++ticks % 20 == 0) RequestService.sweep(e.getServer());
        } catch (Exception | LinkageError ex) {
            LOGGER.error("[Clover] Tick task failed", ex);
        }
    }
}