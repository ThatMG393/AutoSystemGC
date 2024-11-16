package com.thatmg393.autosystemgc.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thatmg393.autosystemgc.AutoSystemGC;

import lombok.Getter;
import net.fabricmc.loader.api.FabricLoader;

public class ConfigManager {
    public static interface ConfigReloadCallback {
        public void onConfigReload(Config reloadedConfig);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("AutoSystemGC-Config");
    private static final ArrayList<ConfigReloadCallback> RELOAD_LISTENERS = new ArrayList<>();

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().setLenient().create();
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve(AutoSystemGC.MOD_ID + ".json");
    public static final Config DEFAULT_CONFIG = new Config();

    @Getter
    private static boolean isManagerWatchingConfig;

    private static ConfigWatchThread configWatcher;
    private static Config loadedConfig;

    static {
        try {
            configWatcher = new ConfigWatchThread(CONFIG_PATH, () -> RELOAD_LISTENERS);
            if (CONFIG_PATH.toFile().exists()) {
                LOGGER.info("Config exists, will now monitor...");
                configWatcher.start();
                isManagerWatchingConfig = true;
            }
        } catch (IOException e) {
            LOGGER.info("File watching instantiation failed! Automatic config reloading is going to be disabled.");
            RELOAD_LISTENERS.removeAll(RELOAD_LISTENERS);
        }
    }

    public static Config getOrLoadConfig() {
        if (loadedConfig != null)
            return loadedConfig;

        LOGGER.info("Loading config...");
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
            Config onDiskConfig = GSON.fromJson(reader, Config.class);

            if (onDiskConfig.configVersion < DEFAULT_CONFIG.configVersion) {
                LOGGER.info("Config version mismatch! Expecting " + DEFAULT_CONFIG.configVersion + ", instead got "
                        + onDiskConfig.configVersion);
                LOGGER.warn("Upgrading config, this may or may not preserve config values!");

                return loadedConfig = mergeConfig(DEFAULT_CONFIG, onDiskConfig);
            } else if (onDiskConfig.configVersion > DEFAULT_CONFIG.configVersion) {
                LOGGER.warn("Is this a mod downgrade? Found newer version (" + onDiskConfig.configVersion
                        + ") of the config!");
                LOGGER.warn("Please proceed with caution...");
            }

            LOGGER.info("Successfully loaded config!");
            return loadedConfig = onDiskConfig;
        } catch (IOException e) {
            LOGGER.error("Failed to load config! Falling back to default!", e);
            saveDefaultConfig();
            return loadedConfig = DEFAULT_CONFIG;
        }
    }

    public static Config reloadLoadedConfig() {
        LOGGER.info("Reloading config...");
        loadedConfig = null;
        return getOrLoadConfig();
    }

    public static boolean saveConfig() {
        if (loadedConfig == null) {
            LOGGER.info("No loaded config, ignoring save operation.");
            return false;
        }

        return saveConfigInternal(loadedConfig);
    }

    public static void saveDefaultConfig() {
        LOGGER.info("Creating default config.");
        saveConfigInternal(DEFAULT_CONFIG);

        if (!isManagerWatchingConfig)
            configWatcher.start();
    }

    public static void saveAndUnloadConfig() {
        saveConfig();

        LOGGER.info("Unloading loaded config.");
        loadedConfig = null;
    }

    public static void registerShutdownHandler() {
        LOGGER.info("Registering config shutdown hook");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (configWatcher != null)
                    configWatcher.shutdown();
            } catch (IOException e) {
            }

            saveAndUnloadConfig();
        }));
    }

    public static void addConfigReloadListener(ConfigReloadCallback c) {
        if (isManagerWatchingConfig)
            RELOAD_LISTENERS.add(c);
    }

    public static void removeConfigReloadListener(ConfigReloadCallback instance) {
        RELOAD_LISTENERS.remove(instance);
    }

    private static boolean saveConfigInternal(Config config) {
        LOGGER.info("Saving config file...");
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH, StandardOpenOption.WRITE,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(GSON.toJson(config));
            writer.flush();

            LOGGER.info("Successfully saved config!");
            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to save config file!", e);
            return false;
        }
    }

    private static Config mergeConfig(Config newConfig, Config currentConfig) {
        Config mergedConfig = new Config();
        mergedConfig.configVersion = newConfig.configVersion;

        for (Field field : newConfig.getClass().getFields()) {
            try {
                field.set(mergedConfig, field.get(newConfig));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        for (Field field : currentConfig.getClass().getFields()) {
            try {
                Object currentValue = field.get(currentConfig);
                if (field.get(newConfig) == null || !field.get(newConfig).equals(currentValue)) {
                    field.set(mergedConfig, currentValue);
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        return mergedConfig;
    }
}
