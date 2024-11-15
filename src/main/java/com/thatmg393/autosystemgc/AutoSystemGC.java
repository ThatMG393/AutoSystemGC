package com.thatmg393.autosystemgc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thatmg393.autosystemgc.config.Config;
import com.thatmg393.autosystemgc.config.ConfigManager;
import com.thatmg393.autosystemgc.utils.MemoryMonitor;
import com.thatmg393.autosystemgc.utils.MemoryMonitor.MemoryClearResult;
import com.thatmg393.autosystemgc.utils.MemoryMonitor.MemoryListener;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class AutoSystemGC implements ModInitializer, Runnable {
	public static final String MOD_ID = "autosystemgc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static Config CONFIG = ConfigManager.getOrLoadConfig();

	private static final ScheduledExecutorService POOL = Executors.newScheduledThreadPool(1);
	private static MemoryMonitor MONITOR = new MemoryMonitor(CONFIG.cleanThresholdPercent, CONFIG.memoryCheckInterval);

	private static MinecraftServer serverInstance = null;

	private final MemoryListener MEMORY_LISTENER = (used, free, usedPercent) -> {
		LOGGER.info("Memory threshold reached! " + usedPercent + " > " + CONFIG.cleanThresholdPercent);
		run();
	};

	@Override
	public void onInitialize() {
		LOGGER.info("lowkey overengineered and messy ash");
		ConfigManager.registerShutdownListener();

		ServerStarted eventListener = (server) -> {
			serverInstance = server;

			if (CONFIG.cleanInterval > 30) POOL.scheduleWithFixedDelay(this, CONFIG.cleanInterval, CONFIG.cleanInterval, TimeUnit.SECONDS);
			else LOGGER.info("Will not be cleaning with intervals because the clean intervals is lower than 30 seconds.");

			if (CONFIG.cleanThresholdPercent < 30) {
				LOGGER.info("Clean threshold is lower than 30!");
				LOGGER.info("Will not be monitoring memory.");
			}
			LOGGER.info("Will be monitoring memory as well.");
			MONITOR.addListener(MEMORY_LISTENER);
			MONITOR.startMonitoring();
		};

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
			dispatcher.register(
				CommandManager.literal("autosystemgc")
				.then(
					CommandManager.literal("trigger")
					.executes(ctx -> { run(); return 0; })
				).then(
					CommandManager.literal("reload")
					.executes(ctx -> {
						ConfigManager.prepareForReload();
						CONFIG = ConfigManager.getOrLoadConfig();

						MONITOR.removeListener(MEMORY_LISTENER);
						MONITOR.stopMonitoring();

						MONITOR = new MemoryMonitor(CONFIG.cleanThresholdPercent, CONFIG.memoryCheckInterval);
						eventListener.onServerStarted(ctx.getSource().getServer());

						return 0;
					})
				)
			);
		});

		ServerLifecycleEvents.SERVER_STARTED.register(eventListener);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> ConfigManager.saveConfig());
	}

	@Override
	public void run() {
		MemoryClearResult result = MONITOR.clearMemory();
		if (CONFIG.logOnCleanTrigger) LOGGER.info(result.toString());
		if (CONFIG.broadcastOnCleanTrigger) runOnServerThread(() -> serverInstance.sendMessage(Text.of(result.toString())));
	}

	public static void runOnServerThread(Runnable task) {
		serverInstance.executeSync(task);
	}
}