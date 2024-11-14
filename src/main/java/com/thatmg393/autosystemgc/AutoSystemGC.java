package com.thatmg393.autosystemgc;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thatmg393.autosystemgc.config.Config;
import com.thatmg393.autosystemgc.config.ConfigManager;

public class AutoSystemGC implements ModInitializer {
	public static final String MOD_ID = "autosystemgc";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Config CONFIG = ConfigManager.getOrLoadConfig();

	@Override
	public void onInitialize() {
		
	}
}