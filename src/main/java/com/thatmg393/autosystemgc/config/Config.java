package com.thatmg393.autosystemgc.config;

public class Config {
    public int cleanInterval = -1;

    public float cleanThresholdPercent = 70.0f;
    public int memoryCheckInterval = 2;

    public boolean logOnCleanTrigger = true;
    public boolean broadcastOnCleanTrigger = false;

    public long configVersion = 1;
}
