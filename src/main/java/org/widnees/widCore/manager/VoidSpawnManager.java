package org.widnees.widCore.manager;

import java.io.File;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class VoidSpawnManager {
    private final Main plugin;
    private final FileConfiguration moduleConfig;
    private FileConfiguration voidSpawnDataConfig;
    private File voidSpawnDataFile;

    public VoidSpawnManager(Main plugin, FileConfiguration moduleConfig) {
        this.plugin = plugin;
        this.moduleConfig = moduleConfig;
        this.setup();
    }

    public void setup() {
        File databaseDir = new File(this.plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.voidSpawnDataFile = new File(databaseDir, "voidspawn.yml");
        if (!this.voidSpawnDataFile.exists()) {
            try {
                this.voidSpawnDataFile.createNewFile();
            }
            catch (IOException e) {
                this.plugin.getLogger().severe(this.plugin.getLanguageManager().getMessage("voidspawnmanager.create-error"));
            }
        }
        this.voidSpawnDataConfig = YamlConfiguration.loadConfiguration((File)this.voidSpawnDataFile);
    }

    public void saveConfig() {
        try {
            this.voidSpawnDataConfig.save(this.voidSpawnDataFile);
        }
        catch (IOException e) {
            this.plugin.getLogger().severe(this.plugin.getLanguageManager().getMessage("voidspawnmanager.save-error"));
        }
    }

    public void setVoidSpawn(Location location, String worldName) {
        boolean perWorld = this.moduleConfig.getBoolean("per-world-spawn", true);
        String path = "spawns." + (perWorld && worldName != null ? worldName : "global");
        this.voidSpawnDataConfig.set(String.valueOf(path) + ".world", (Object)location.getWorld().getName());
        this.voidSpawnDataConfig.set(String.valueOf(path) + ".x", (Object)location.getX());
        this.voidSpawnDataConfig.set(String.valueOf(path) + ".y", (Object)location.getY());
        this.voidSpawnDataConfig.set(String.valueOf(path) + ".z", (Object)location.getZ());
        this.voidSpawnDataConfig.set(String.valueOf(path) + ".yaw", (Object)Float.valueOf(location.getYaw()));
        this.voidSpawnDataConfig.set(String.valueOf(path) + ".pitch", (Object)Float.valueOf(location.getPitch()));
        this.saveConfig();
    }

    public Location getVoidSpawn(String worldName) {
        World world;
        boolean perWorld = this.moduleConfig.getBoolean("per-world-spawn", true);
        String path = "spawns." + (perWorld ? worldName : "global");
        if (!this.voidSpawnDataConfig.contains(String.valueOf(path) + ".world")) {
            if (perWorld) {
                path = "spawns.global";
                if (!this.voidSpawnDataConfig.contains(String.valueOf(path) + ".world")) {
                    return null;
                }
            } else {
                return null;
            }
        }
        if ((world = Bukkit.getWorld((String)this.voidSpawnDataConfig.getString(String.valueOf(path) + ".world"))) == null) {
            return null;
        }
        double x = this.voidSpawnDataConfig.getDouble(String.valueOf(path) + ".x");
        double y = this.voidSpawnDataConfig.getDouble(String.valueOf(path) + ".y");
        double z = this.voidSpawnDataConfig.getDouble(String.valueOf(path) + ".z");
        float yaw = (float)this.voidSpawnDataConfig.getDouble(String.valueOf(path) + ".yaw");
        float pitch = (float)this.voidSpawnDataConfig.getDouble(String.valueOf(path) + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }
}
