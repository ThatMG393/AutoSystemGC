package com.thatmg393.autosystemgc;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thatmg393.autosystemgc.config.Config;
import com.thatmg393.autosystemgc.config.ConfigManager;

public class AutoSystemGC implements ModInitializer, Runnable {
	public static final String MOD_ID = "autosystemgc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Config CONFIG = ConfigManager.getOrLoadConfig();

	private static final ScheduledExecutorService POOL = Executors.newScheduledThreadPool(2);
	private static MinecraftServer serverInstance = null;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
			serverInstance = server;
			if (CONFIG.cleanInterval > 30) POOL.scheduleWithFixedDelay(this, 1, CONFIG.cleanInterval, TimeUnit.SECONDS);
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			dispatcher.register(
				CommandManager.literal("systemgc")
				.executes(ctx -> { run(); return 0; })
			);
		});
	}

	@Override
	public void run() {
		System.gc();
		if (CONFIG.logOnCleanTrigger) LOGGER.info("Ran System.gc() at " + Instant.now());
		if (CONFIG.broadcastOnCleanTrigger) runOnServerThread(() -> serverInstance.sendMessage(Text.of("Ran System.gc() at " + Instant.now())));
	}

	public static void runOnServerThread(Runnable task) {
		serverInstance.executeSync(task);
	}
}