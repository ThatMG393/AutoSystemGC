package com.thatmg393.autosystemgc;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import com.thatmg393.autosystemgc.config.Config;
import com.thatmg393.autosystemgc.config.ConfigManager;
import com.thatmg393.autosystemgc.utils.MemoryMonitor;
import com.thatmg393.autosystemgc.utils.MemoryMonitor.MemoryClearResult;
import com.thatmg393.autosystemgc.utils.MemoryMonitor.MemoryListener;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.CommandManager.RegistrationEnvironment;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class AutoSystemGC implements ModInitializer, Runnable, MemoryListener, ServerStarted, ServerStopping, CommandRegistrationCallback {
	public static final String MOD_ID = "autosystemgc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	private Config currentConfig = ConfigManager.getOrLoadConfig();
	private ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
	private MemoryMonitor memoryMonitor = new MemoryMonitor(currentConfig.cleanThresholdPercent, currentConfig.memoryCheckInterval);
	private MinecraftServer serverInstance = null;

	@Override
	public void onInitialize() {
		LOGGER.info("lowkey overengineered and messy ash");
		ConfigManager.registerShutdownListener();
		CommandRegistrationCallback.EVENT.register(this);
		ServerLifecycleEvents.SERVER_STARTED.register(this);
		ServerLifecycleEvents.SERVER_STOPPING.register(this);
	}

	@Override
	public void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, RegistrationEnvironment environment) {
		dispatcher.register(
			CommandManager.literal("autosystemgc")
			.then(
				CommandManager.literal("trigger")
				.executes(ctx -> { run(); return 0; })
			).then(
				CommandManager.literal("reload")
				.executes(ctx -> reloadModAndConfig(ctx.getSource().getServer()))
			)
		);

		dispatcher.register(
			CommandManager.literal("agc")
			.redirect(dispatcher.getRoot().getChild("autosystemgc"))
		);
	}

	@Override
	public void run() {
		MemoryClearResult result = memoryMonitor.clearMemory();
		if (currentConfig.logOnCleanTrigger) LOGGER.info(result.toString());
		if (currentConfig.broadcastOnCleanTrigger) runOnServerThread(() -> serverInstance.getPlayerManager().broadcast(Text.of(result.toString()), false));
	}

	@Override
	public void onHighMemory(long usedMemory, long freeMemory, double usedPercent) {
		LOGGER.info("Memory threshold reached! " + usedPercent + " > " + currentConfig.cleanThresholdPercent);
		run();
	}

	@Override
	public void onServerStarted(MinecraftServer server) {
		serverInstance = server;

		if (currentConfig.cleanInterval > 30) {
			LOGGER.info("Will now clean memory every " + currentConfig.cleanInterval + "s!");
			scheduledExecutor.scheduleWithFixedDelay(this, currentConfig.cleanInterval, currentConfig.cleanInterval, TimeUnit.SECONDS);
		} else LOGGER.info("Will not be cleaning with intervals because the clean intervals is lower than 30 seconds.");

		if (currentConfig.cleanThresholdPercent < 30) {
			LOGGER.info("Clean threshold is lower than 30!");
			LOGGER.info("Will not be monitoring memory.");
			
			return;
		}

		LOGGER.info("Will now be monitoring memory!");
		memoryMonitor.addListener(this);
		memoryMonitor.startMonitoring();
	}

	@Override
	public void onServerStopping(MinecraftServer server) {
		ConfigManager.saveConfig();
	}

	public int reloadModAndConfig(MinecraftServer server) {
		LOGGER.info("Reloading AutoSystemGC.");

		// Goofy aah reload sequence
		ConfigManager.prepareForReload();
		currentConfig = ConfigManager.getOrLoadConfig();

		scheduledExecutor.shutdownNow();
		scheduledExecutor = Executors.newScheduledThreadPool(1);

		memoryMonitor.removeListener(this);
		memoryMonitor.stopMonitoring();
		memoryMonitor = new MemoryMonitor(currentConfig.cleanThresholdPercent, currentConfig.memoryCheckInterval);

		onServerStarted(server);
		
		return 0;
	}

	public void runOnServerThread(Runnable task) {
		serverInstance.executeSync(task);
	}
}