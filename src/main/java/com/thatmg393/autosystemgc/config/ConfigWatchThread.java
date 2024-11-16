package com.thatmg393.autosystemgc.config;

import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thatmg393.autosystemgc.config.ConfigManager.ConfigReloadCallback;

public class ConfigWatchThread extends Thread {
    private final WatchService WATCH_SERVICE = FileSystems.getDefault().newWatchService();
    private final Logger LOGGER;

    private final String nameOfFileBeingWatched;
    private final Supplier<ArrayList<ConfigReloadCallback>> listeners;

    public ConfigWatchThread(Path fileToBeWatched, Supplier<ArrayList<ConfigReloadCallback>> listeners) throws IOException {
        super("ConfigWatchThread-for-" + fileToBeWatched.toFile().getName());
        this.LOGGER = LoggerFactory.getLogger(getName());

        this.nameOfFileBeingWatched = fileToBeWatched.toFile().getName();
        this.listeners = listeners;

        fileToBeWatched.getParent().register(WATCH_SERVICE, ENTRY_DELETE, ENTRY_MODIFY);
    }

    /*
     * May or may not stop this thread immediately.
     */
    public void shutdown() throws IOException {
        WATCH_SERVICE.close();
        interrupt();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run() {
        LOGGER.info("Will now monitor " + nameOfFileBeingWatched);
        while (!interrupted()) {
            try {
                WatchKey fileEvent = WATCH_SERVICE.take();

                for (WatchEvent<?> event : fileEvent.pollEvents()) {
                    if (!fileEvent.isValid()) break;

                    Path file = ((WatchEvent<Path>) event).context();
                    if (!file.toFile().getName().equals(nameOfFileBeingWatched)) continue;
                    LOGGER.info("Config has changed!");
                    
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) continue;

                    if (kind == ENTRY_DELETE) {
                        LOGGER.info("Config has been deleted! Saving loaded one.. ");
                        if (!ConfigManager.saveConfig()) {
                            LOGGER.info("We recreating instead.");
                            ConfigManager.saveDefaultConfig();
                        }
                    } else if (kind == ENTRY_MODIFY) {
                        LOGGER.info("Config has been modified! Reloading config...");
                        notifyConfigReload(ConfigManager.reloadLoadedConfig());
                    }
                }

                fileEvent.reset();
            } catch (ClosedWatchServiceException e) {
                LOGGER.error("Watch service has been shutdown, shutting down thread as well...");
                return;
            } catch (InterruptedException e) {
                LOGGER.error("Somebody interrupted me!", e);
                interrupt();
                return;
            }
        }

        try { WATCH_SERVICE.close(); }
        catch (IOException e) { }
    }

    private void notifyConfigReload(Config reloadedConfig) {
        listeners.get().forEach(e -> e.onConfigReload(reloadedConfig));
    }
}
