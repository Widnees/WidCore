package org.widnees.widCore.manager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class WarpManager {
    private final Main plugin;
    private final Map<String, Location> warps = new HashMap<String, Location>();
    private final Map<String, Map<String, Object>> pendingWarps = new HashMap<String, Map<String, Object>>();
    private final File warpsFile;

    public WarpManager(Main plugin) {
        this.plugin = plugin;
        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.warpsFile = new File(databaseDir, "warps.yml");
        this.loadWarps();
    }

    public void setWarp(String name, Location location) {
        this.warps.put(name.toLowerCase(), location);
        this.pendingWarps.remove(name.toLowerCase());
        this.saveWarps();
    }

    public Location getWarp(String name) {
        Map<String, Object> data;
        String key = name.toLowerCase();
        Location loc = this.warps.get(key);
        if (loc != null) {
            return loc;
        }
        if (this.pendingWarps.containsKey(key) && (loc = this.resolveLocation(data = this.pendingWarps.get(key))) != null) {
            this.warps.put(key, loc);
            this.pendingWarps.remove(key);
            return loc;
        }
        return null;
    }

    public void delWarp(String name) {
        this.warps.remove(name.toLowerCase());
        this.pendingWarps.remove(name.toLowerCase());
        this.saveWarps();
    }

    public Set<String> getWarpNames() {
        HashSet<String> names = new HashSet<String>(this.warps.keySet());
        names.addAll(this.pendingWarps.keySet());
        return names;
    }

    public void saveWarps() {
        String key;
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Location> entry : this.warps.entrySet()) {
            Location loc = entry.getValue();
            key = "warps." + entry.getKey();
            config.set(String.valueOf(key) + ".world", (Object)loc.getWorld().getName());
            config.set(String.valueOf(key) + ".x", (Object)loc.getX());
            config.set(String.valueOf(key) + ".y", (Object)loc.getY());
            config.set(String.valueOf(key) + ".z", (Object)loc.getZ());
            config.set(String.valueOf(key) + ".yaw", (Object)Float.valueOf(loc.getYaw()));
            config.set(String.valueOf(key) + ".pitch", (Object)Float.valueOf(loc.getPitch()));
        }
        for (Map.Entry<String, Map<String, Object>> entry : this.pendingWarps.entrySet()) {
            Map<String, Object> data = entry.getValue();
            key = "warps." + entry.getKey();
            config.set(String.valueOf(key) + ".world", data.get("world"));
            config.set(String.valueOf(key) + ".x", data.get("x"));
            config.set(String.valueOf(key) + ".y", data.get("y"));
            config.set(String.valueOf(key) + ".z", data.get("z"));
            config.set(String.valueOf(key) + ".yaw", data.get("yaw"));
            config.set(String.valueOf(key) + ".pitch", data.get("pitch"));
        }
        try {
            config.save(this.warpsFile);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().severe("Warplar " + this.warpsFile + " dosyas\u0131na kaydedilemedi!");
            iOException.printStackTrace();
        }
    }

    public void loadWarps() {
        if (!this.warpsFile.exists()) {
            return;
        }
        this.warps.clear();
        this.pendingWarps.clear();
        YamlConfiguration config = YamlConfiguration.loadConfiguration((File)this.warpsFile);
        ConfigurationSection warpsSection = config.getConfigurationSection("warps");
        if (warpsSection != null) {
            for (String key : warpsSection.getKeys(false)) {
                ConfigurationSection warpData = warpsSection.getConfigurationSection(key);
                if (warpData == null) continue;
                String worldName = warpData.getString("world");
                double x = warpData.getDouble("x");
                double y = warpData.getDouble("y");
                double z = warpData.getDouble("z");
                float yaw = (float)warpData.getDouble("yaw");
                float pitch = (float)warpData.getDouble("pitch");
                World world = Bukkit.getWorld((String)worldName);
                if (world != null) {
                    this.warps.put(key, new Location(world, x, y, z, yaw, pitch));
                    continue;
                }
                HashMap<String, Object> data = new HashMap<String, Object>();
                data.put("world", worldName);
                data.put("x", x);
                data.put("y", y);
                data.put("z", z);
                data.put("yaw", Float.valueOf(yaw));
                data.put("pitch", Float.valueOf(pitch));
                this.pendingWarps.put(key, data);
            }
        }
    }

    private Location resolveLocation(Map<String, Object> data) {
        String worldName = (String)data.get("world");
        World world = Bukkit.getWorld((String)worldName);
        if (world == null) {
            return null;
        }
        double x = ((Number)data.get("x")).doubleValue();
        double y = ((Number)data.get("y")).doubleValue();
        double z = ((Number)data.get("z")).doubleValue();
        float yaw = ((Number)data.get("yaw")).floatValue();
        float pitch = ((Number)data.get("pitch")).floatValue();
        return new Location(world, x, y, z, yaw, pitch);
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
