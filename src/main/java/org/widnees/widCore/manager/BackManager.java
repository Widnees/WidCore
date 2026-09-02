package org.widnees.widCore.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.widnees.widCore.Main;
import org.widnees.widCore.database.BinaryDataManager;

public class BackManager {
    private final Main plugin;
    private final BinaryDataManager dataManager;
    private Map<UUID, Location> deathLocations = new ConcurrentHashMap<UUID, Location>();

    public BackManager(Main plugin, BinaryDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
        this.loadDeathLocations();
    }

    public void loadDeathLocations() {
        this.dataManager.loadDeathLocations(loadedLocations -> {
            if (loadedLocations != null) {
                this.deathLocations = new ConcurrentHashMap<UUID, Location>((Map<UUID, Location>)loadedLocations);
            }
        });
    }

    public CompletableFuture<Void> saveDeathLocations() {
        return this.dataManager.saveDeathLocations(this.deathLocations);
    }

    public void setLastDeathLocation(UUID playerUuid, Location location) {
        this.deathLocations.put(playerUuid, location);
    }

    public Location getLastDeathLocation(UUID playerUuid) {
        return this.deathLocations.get(playerUuid);
    }
        @SuppressWarnings("unused")
    private static final String __Wx7c4e2 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
