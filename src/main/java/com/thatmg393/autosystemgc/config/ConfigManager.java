package com.thatmg393.autosystemgc.config;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoSystemGC-Config");

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().setLenient().create();
    public static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("autosystemgc.json");
    public static final Config DEFAULT_CONFIG = new Config();

    private static Config loadedConfig;

    public static Config getOrLoadConfig() {
        if (loadedConfig != null)
            return loadedConfig;

        LOGGER.info("Loading config!");
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_PATH.toFile()))) {
            Config onDiskConfig = GSON.fromJson(reader, Config.class);

            if (onDiskConfig.configVersion < DEFAULT_CONFIG.configVersion) {
                LOGGER.info("Config version mismatch! Expecting " + DEFAULT_CONFIG.configVersion + ", instead got " + onDiskConfig.configVersion);
                LOGGER.warn("Upgrading config, this may or may not preserve config values!");

                return loadedConfig = mergeConfig(new Config(), onDiskConfig);
            } else if (onDiskConfig.configVersion > DEFAULT_CONFIG.configVersion) {
                LOGGER.warn("Is this a mod downgrade? Found newer version (" + onDiskConfig.configVersion + ") of the config!");
                LOGGER.warn("Please proceed with caution...");
            }

            LOGGER.info("Successfully loaded config!");
            return onDiskConfig;
        } catch (IOException e) {
            LOGGER.error("Failed to load config! Falling back to default!", e);
            return DEFAULT_CONFIG;
        }
    }

    public static void saveConfig() {
        LOGGER.info("Saving config file...");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CONFIG_PATH.toFile()))) {
            writer.write(GSON.toJson(loadedConfig));
            writer.flush();

            LOGGER.info("Successfully saved configs!");
        } catch (IOException e) {
            LOGGER.error("Failed to save config file!", e);
        }
    }

    private static Config mergeConfig(Config newConfig, Config currentConfig) {
        Config mergedConfig = new Config();
        mergedConfig.configVersion = newConfig.configVersion;

        // Copy the new default values
        for (Field field : newConfig.getClass().getFields()) {
            try {
                field.set(mergedConfig, field.get(newConfig));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }

        // Merge the user-customized values from the current config
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
