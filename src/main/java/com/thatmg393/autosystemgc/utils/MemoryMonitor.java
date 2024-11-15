package com.thatmg393.autosystemgc.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.slf4j.helpers.MessageFormatter;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MemoryMonitor {
    private final List<MemoryListener> listeners = new ArrayList<>();
    private final Runtime runtime = Runtime.getRuntime();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @NotNull
    private final float highMemoryThresholdPercent;
    @NotNull
    private final int checkIntervalSeconds;

    @Getter
    private long peakUsedMemory = 0;

    private Instant lastHighMemoryAlert;

    public interface MemoryListener {
        void onHighMemory(long usedMemory, long freeMemory, double usedPercent);
    }

    public void addListener(@NotNull MemoryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(@NotNull MemoryListener listener) {
        listeners.remove(listener);
    }

    public void startMonitoring() {
        scheduler.scheduleWithFixedDelay(this::checkMemory, 0, checkIntervalSeconds, TimeUnit.SECONDS);
    }

    public void stopMonitoring() {
        scheduler.shutdown();
    }

    @SuppressWarnings("removal")
    public MemoryClearResult clearMemory() {
        long beforeUsed = getCurrentStats().getUsedMemory();
        long beforeFree = getCurrentStats().getFreeMemory();

        Instant gcTime = Instant.now();
        System.gc();
        System.runFinalization(); // ts not necessary

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long afterUsed = getCurrentStats().getUsedMemory();
        long afterFree = getCurrentStats().getFreeMemory();

        long freedMemory = beforeUsed - afterUsed;
        double percentFreed = (freedMemory * 100.0) / runtime.totalMemory();

        return new MemoryClearResult(
            gcTime,
            freedMemory,
            percentFreed,
            beforeUsed,
            afterUsed,
            beforeFree,
            afterFree,
            peakUsedMemory
        );
    }

    private void checkMemory() {
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        this.peakUsedMemory = Math.max(peakUsedMemory, usedMemory);

        double usagePercent = (usedMemory * 100.0) / totalMemory;

        if (usagePercent >= highMemoryThresholdPercent) {
            if (lastHighMemoryAlert == null)
                notifyHighMemory(usedMemory, freeMemory, usagePercent);
            else if (Duration.between(lastHighMemoryAlert, Instant.now()).getSeconds() > 5)
                notifyHighMemory(usedMemory, freeMemory, usagePercent);
            
            lastHighMemoryAlert = Instant.now();
        }
    }

    private void notifyHighMemory(long usedMemory, long freeMemory, double usagePercent) {
        listeners.parallelStream().forEach(l -> l.onHighMemory(usedMemory, freeMemory, usagePercent));
    }

    @Getter
    public static class MemoryStats {
        private final long usedMemory;
        private final long freeMemory;
        private final double usagePercent;

        public MemoryStats(long totalMemory, long freeMemory) {
            this.freeMemory = freeMemory;
            this.usedMemory = totalMemory - freeMemory;
            this.usagePercent = (usedMemory * 100.0) / totalMemory;
        }
    }

    public MemoryStats getCurrentStats() {
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        return new MemoryStats(total, free);
    }

    @Getter
    @AllArgsConstructor
    public static class MemoryClearResult {
        private final Instant executionTime;
        private final long memoryFreed;
        private final double percentageFreed;
        private final long beforeUsed;
        private final long afterUsed;
        private final long beforeFree;
        private final long afterFree;
        private final long peakUsed;

        @Override
        public String toString() {
            return MessageFormatter.arrayFormat(
                "Memory Cleared on {}:\n" +
                "- Freed: {} MB ({} %)\n" + 
                "- Before: Used={} MB, Free={} MB\n" +
                "- After: Used={} MB, Free={} MB\n" +
                "- Peak Usage: {} MB",
                new Object[] {
                    executionTime,
                    memoryFreed / (1024 * 1024),
                    Math.round(percentageFreed * 100) / 100.0,
                    beforeUsed / (1024 * 1024), 
                    beforeFree / (1024 * 1024),
                    afterUsed / (1024 * 1024),
                    afterFree / (1024 * 1024),
                    peakUsed / (1024 * 1024)
                }
            ).getMessage();
        }
    }
}