package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager; 
import org.widnees.widCore.manager.SpawnLocationManager;

public class SpawnListener implements Listener {

    private final FileConfiguration spawnConfig;
    private final SpawnLocationManager spawnLocationManager;

    public SpawnListener(Main plugin, SpawnLocationManager spawnLocationManager, FileConfiguration spawnConfig) {
        this.spawnLocationManager = spawnLocationManager;
        this.spawnConfig = spawnConfig;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        Location spawnLocation = spawnLocationManager.getSpawnLocation();
        if (spawnLocation == null) {
            return;
        }

        boolean forceSpawnOnJoin = spawnConfig.getBoolean("force-spawn-on-join", false);
        if (forceSpawnOnJoin && spawnLocationManager.isSpawnSet()) {
            event.getPlayer().teleportAsync(spawnLocation);
            return;
        }

        boolean spawnOnFirstJoin = spawnConfig.getBoolean("spawn-on-first-join", true);
        if (spawnOnFirstJoin && !event.getPlayer().hasPlayedBefore() && spawnLocationManager.isSpawnSet()) {
            event.getPlayer().teleportAsync(spawnLocation);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        boolean overrideBedSpawn = spawnConfig.getBoolean("override-bed-spawn", false);
        Location spawnLocation = spawnLocationManager.getSpawnLocation();

        if (spawnLocation != null) {
            if (overrideBedSpawn || !event.isBedSpawn()) {
                event.setRespawnLocation(spawnLocation);
            }
        }
    }
        @SuppressWarnings("unused")
    private static final String _W3f0b7c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}