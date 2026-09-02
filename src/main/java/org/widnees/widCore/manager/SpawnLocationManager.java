package org.widnees.widCore.manager;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class SpawnLocationManager {
    private final Main plugin;
    private File spawnFile;
    private FileConfiguration spawnConfig;

    public SpawnLocationManager(Main plugin) {
        this.plugin = plugin;
        this.setupSpawnFile();
    }

    private void setupSpawnFile() {
        File databaseDir = new File(this.plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.spawnFile = new File(databaseDir, "spawn.yml");
        if (!this.spawnFile.exists()) {
            try {
                this.spawnFile.createNewFile();
            }
            catch (IOException e) {
                this.plugin.getLogger().severe(this.plugin.getLanguageManager().getMessage("spawnmanager.create-error"));
                e.printStackTrace();
            }
        }
        this.spawnConfig = YamlConfiguration.loadConfiguration((File)this.spawnFile);
    }

    public void setSpawn(Location location) {
        this.spawnConfig.set("spawn-location.world", (Object)location.getWorld().getName());
        this.spawnConfig.set("spawn-location.x", (Object)location.getX());
        this.spawnConfig.set("spawn-location.y", (Object)location.getY());
        this.spawnConfig.set("spawn-location.z", (Object)location.getZ());
        this.spawnConfig.set("spawn-location.yaw", (Object)Float.valueOf(location.getYaw()));
        this.spawnConfig.set("spawn-location.pitch", (Object)Float.valueOf(location.getPitch()));
        this.saveConfig();
    }

    public Location getSpawnLocation() {
        World world;
        block4: {
            if (!this.isSpawnSet()) {
                return null;
            }
            try {
                world = Bukkit.getWorld((String)this.spawnConfig.getString("spawn-location.world"));
                if (world != null) break block4;
                this.plugin.getLogger().severe(this.plugin.getLanguageManager().getMessage("spawnmanager.world-not-found").replace("%world%", this.spawnConfig.getString("spawn-location.world")));
                return null;
            }
            catch (Exception e) {
                this.plugin.getLogger().severe(String.valueOf(this.plugin.getLanguageManager().getMessage("spawnmanager.read-error")) + ": " + e.getMessage());
                return null;
            }
        }
        double x = this.spawnConfig.getDouble("spawn-location.x");
        double y = this.spawnConfig.getDouble("spawn-location.y");
        double z = this.spawnConfig.getDouble("spawn-location.z");
        float yaw = (float)this.spawnConfig.getDouble("spawn-location.yaw");
        float pitch = (float)this.spawnConfig.getDouble("spawn-location.pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isSpawnSet() {
        this.reloadConfig();
        return this.spawnConfig.contains("spawn-location.world");
    }

    private void saveConfig() {
        try {
            this.spawnConfig.save(this.spawnFile);
        }
        catch (IOException e) {
            this.plugin.getLogger().severe(this.plugin.getLanguageManager().getMessage("spawnmanager.save-error"));
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        this.spawnConfig = YamlConfiguration.loadConfiguration((File)this.spawnFile);
    }
        @SuppressWarnings("unused")
    private static final String __wNx8b2c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
