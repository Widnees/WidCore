package org.widnees.widCore.manager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.widnees.widCore.Main;

public class WorldDataManager
implements Listener {
    private final Main plugin;
    private final File worldManagerFile;
    private FileConfiguration worldManagerConfig;
    private final Map<String, Object> defaultGameRules = new HashMap<String, Object>();
    private boolean worldsLoadedEarly = false;

    public WorldDataManager(Main plugin) {
        this.plugin = plugin;
        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.worldManagerFile = new File(databaseDir, "worldmanager.yml");
        this.setup();
        this.cacheDefaultGameRules();
        plugin.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plugin);
    }

    public void loadWorldsEarly() {
        this.syncManagedWorldsToBukkitConfig();
        this.worldsLoadedEarly = true;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onDefaultWorldLoad(WorldLoadEvent event) {
        if (this.worldsLoadedEarly) {
            return;
        }
        this.worldsLoadedEarly = true;
        this.loadWorldsOnStartup();
        this.triggerPostWorldLoad();
    }

    private void setup() {
        if (!this.worldManagerFile.exists()) {
            try {
                this.worldManagerFile.createNewFile();
            }
            catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, "worldmanager.yml dosyas\u0131 olu\u015fturulamad\u0131!", e);
            }
        }
        this.worldManagerConfig = YamlConfiguration.loadConfiguration((File)this.worldManagerFile);
    }

    private void cacheDefaultGameRules() {
        World mainWorld;
        World world = mainWorld = Bukkit.getWorlds().isEmpty() ? null : (World)Bukkit.getWorlds().get(0);
        if (mainWorld == null) {
            return;
        }
        GameRule[] gameRuleArray = GameRule.values();
        int n = gameRuleArray.length;
        int n2 = 0;
        while (n2 < n) {
            GameRule rule = gameRuleArray[n2];
            try {
                Object value = mainWorld.getGameRuleDefault(rule);
                if (value != null) {
                    this.defaultGameRules.put(rule.getName(), value);
                }
            }
            catch (IllegalArgumentException illegalArgumentException) {

            }
            ++n2;
        }
    }

    private void saveConfig() {
        try {
            this.worldManagerConfig.save(this.worldManagerFile);
        }
        catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "worldmanager.yml dosyas\u0131 kaydedilemedi!", e);
        }
    }

    public void saveWorldData(String worldName, WorldCreator creator, String generatorType, Biome biome) {
        String path = "worlds." + worldName;
        this.worldManagerConfig.set(String.valueOf(path) + ".environment", (Object)creator.environment().name());
        this.worldManagerConfig.set(String.valueOf(path) + ".generator-type", (Object)generatorType.toUpperCase());
        if (biome != null) {
            this.worldManagerConfig.set(String.valueOf(path) + ".biome", (Object)biome.name());
        } else {
            this.worldManagerConfig.set(String.valueOf(path) + ".biome", null);
        }
        World world = Bukkit.getWorld((String)worldName);
        if (world != null) {
            this.saveGameRules(worldName, world);
        }
        this.saveConfig();
        this.updateBukkitWorldEntry(worldName);
    }

    public void deleteWorldData(String worldName) {
        this.worldManagerConfig.set("worlds." + worldName, null);
        this.saveConfig();
        this.unregisterGeneratorFromBukkitYml(worldName);
    }

    public void setWorldSpawn(String worldName, Location location) {
        String path = "worlds." + worldName + ".spawn";
        this.worldManagerConfig.set(String.valueOf(path) + ".x", (Object)location.getX());
        this.worldManagerConfig.set(String.valueOf(path) + ".y", (Object)location.getY());
        this.worldManagerConfig.set(String.valueOf(path) + ".z", (Object)location.getZ());
        this.worldManagerConfig.set(String.valueOf(path) + ".yaw", (Object)Float.valueOf(location.getYaw()));
        this.worldManagerConfig.set(String.valueOf(path) + ".pitch", (Object)Float.valueOf(location.getPitch()));
        this.saveConfig();
    }

    public String getGeneratorTypeForWorld(String worldName) {
        return this.worldManagerConfig.getString("worlds." + worldName + ".generator-type");
    }

    public String getBiomeForWorld(String worldName) {
        return this.worldManagerConfig.getString("worlds." + worldName + ".biome");
    }

    public void saveGameRules(String worldName, World world) {
        String path = "worlds." + worldName + ".gamerules";
        this.worldManagerConfig.set(path, null);
        GameRule[] gameRuleArray = GameRule.values();
        int n = gameRuleArray.length;
        int n2 = 0;
        while (n2 < n) {
            GameRule rule = gameRuleArray[n2];
            try {
                Object currentValue = world.getGameRuleValue(rule);
                Object defaultValue = this.defaultGameRules.get(rule.getName());
                if (currentValue != null && !currentValue.equals(defaultValue)) {
                    this.worldManagerConfig.set(String.valueOf(path) + "." + rule.getName(), (Object)currentValue.toString());
                }
            }
            catch (IllegalArgumentException illegalArgumentException) {

            }
            ++n2;
        }
        this.saveConfig();
    }

    public void loadGameRules(World world) {
        String worldName = world.getName();
        String path = "worlds." + worldName + ".gamerules";
        ConfigurationSection gameruleSection = this.worldManagerConfig.getConfigurationSection(path);
        if (gameruleSection == null) {
            return;
        }
        for (String ruleName : gameruleSection.getKeys(false)) {
            String savedValue;
            GameRule rule = GameRule.getByName((String)ruleName);
            if (rule == null || (savedValue = gameruleSection.getString(ruleName)) == null) continue;
            try {
                if (rule.getType() == Boolean.class) {
                    world.setGameRule((GameRule<Boolean>)rule, Boolean.parseBoolean(savedValue));
                    continue;
                }
                if (rule.getType() != Integer.class) continue;
                world.setGameRule((GameRule<Integer>)rule, Integer.parseInt(savedValue));
            }
            catch (Exception e) {
                this.plugin.getLogger().warning("Could not apply gamerule " + ruleName + " to world " + worldName + ": " + e.getMessage());
            }
        }
    }

    public <T> boolean setGameRule(World world, GameRule<T> rule, T value) {
        boolean success = world.setGameRule(rule, value);
        if (success) {
            String path = "worlds." + world.getName() + ".gamerules." + rule.getName();
            Object defaultValue = this.defaultGameRules.get(rule.getName());
            if (value.equals(defaultValue)) {
                this.worldManagerConfig.set(path, null);
            } else {
                this.worldManagerConfig.set(path, (Object)value.toString());
            }
            this.saveConfig();
        }
        return success;
    }

    public <T> void resetGameRule(World world, GameRule<T> rule) {
        Object defaultValue = this.defaultGameRules.get(rule.getName());
        if (defaultValue != null) {
            world.setGameRule((GameRule)rule, defaultValue);
            this.worldManagerConfig.set("worlds." + world.getName() + ".gamerules." + rule.getName(), null);
            this.saveConfig();
        }
    }

    public boolean isGameRuleDefault(World world, GameRule<?> rule) {
        try {
            Object currentValue = world.getGameRuleValue(rule);
            Object defaultValue = this.defaultGameRules.get(rule.getName());
            return currentValue != null && currentValue.equals(defaultValue);
        }
        catch (IllegalArgumentException e) {
            return true;
        }
    }

    public Object getDefaultGameRuleValue(GameRule<?> rule) {
        return this.defaultGameRules.get(rule.getName());
    }

    public void loadWorldsOnStartup() {
        ConfigurationSection section = this.worldManagerConfig.getConfigurationSection("worlds");
        if (section == null) {
            return;
        }
        for (String worldName : section.getKeys(false)) {
            String path = "worlds." + worldName;
            String envStr = this.worldManagerConfig.getString(path + ".environment", "NORMAL");
            String genType = this.worldManagerConfig.getString(path + ".generator-type", "NORMAL");

            World.Environment env;
            try {
                env = World.Environment.valueOf(envStr.toUpperCase());
            } catch (Exception e) {
                env = World.Environment.NORMAL;
            }

            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(env);
            if ("EMPTY".equalsIgnoreCase(genType)) {
                creator.generator(this.plugin.getName() + ":empty");
            }

            World world = Bukkit.createWorld(creator);
            if (world != null) {
                this.plugin.getLogger().info("World loaded: " + worldName);
            } else {
                this.plugin.getLogger().warning("Failed to load world: " + worldName);
            }
        }
    }

    public void triggerPostWorldLoad() {
        if (this.plugin.getWarpManager() != null) {
            this.plugin.getWarpManager().loadWarps();
        }
    }

    private void applyStoredWorldSettings(World world) {
        String path = "worlds." + world.getName();
        if (this.worldManagerConfig.contains(String.valueOf(path) + ".spawn")) {
            double x = this.worldManagerConfig.getDouble(String.valueOf(path) + ".spawn.x");
            double y = this.worldManagerConfig.getDouble(String.valueOf(path) + ".spawn.y");
            double z = this.worldManagerConfig.getDouble(String.valueOf(path) + ".spawn.z");
            float yaw = (float)this.worldManagerConfig.getDouble(String.valueOf(path) + ".spawn.yaw");
            float pitch = (float)this.worldManagerConfig.getDouble(String.valueOf(path) + ".spawn.pitch");
            world.setSpawnLocation(new Location(world, x, y, z, yaw, pitch));
            this.plugin.getLogger().info("   -> " + world.getName() + " i\u00e7in \u00f6zel spawn noktas\u0131 ayarland\u0131.");
        }
        this.loadGameRules(world);
    }

    private void syncManagedWorldsToBukkitConfig() {
        Set<String> managedWorlds = this.getManagedWorlds();
        if (managedWorlds.isEmpty()) {
            return;
        }
        for (String worldName : managedWorlds) {
            this.updateBukkitWorldEntry(worldName);
        }
    }

    private void updateBukkitWorldEntry(String worldName) {
        try {
            File bukkitYml = new File("bukkit.yml");
            if (!bukkitYml.exists()) {
                this.plugin.getLogger().warning("bukkit.yml bulunamad\u0131, startup world kayd\u0131 atlan\u0131yor.");
                return;
            }
            String path = "worlds." + worldName;
            YamlConfiguration bukkitConfig = YamlConfiguration.loadConfiguration((File)bukkitYml);
            String environment = this.worldManagerConfig.getString(String.valueOf(path) + ".environment", "NORMAL");
            String generatorType = this.worldManagerConfig.getString(String.valueOf(path) + ".generator-type", "NORMAL");
            bukkitConfig.set(String.valueOf(path) + ".environment", (Object)environment.toLowerCase());
            bukkitConfig.set(String.valueOf(path) + ".type", (Object)this.mapWorldTypeForBukkit(generatorType));
            if (generatorType.equalsIgnoreCase("EMPTY")) {
                bukkitConfig.set(String.valueOf(path) + ".generator", (Object)(String.valueOf(this.plugin.getName()) + ":empty"));
            } else {
                bukkitConfig.set(String.valueOf(path) + ".generator", null);
            }
            bukkitConfig.save(bukkitYml);
        }
        catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "bukkit.yml i\u00e7inde " + worldName + " i\u00e7in startup world kayd\u0131 g\u00fcncellenemedi", e);
        }
    }

    private String mapWorldTypeForBukkit(String generatorType) {
        if (generatorType == null) {
            return "NORMAL";
        }
        switch (generatorType.toUpperCase()) {
            case "FLAT":
                return "FLAT";
            case "AMPLIFIED":
                return "AMPLIFIED";
            case "LARGEBIOMES":
            case "LARGE_BIOMES":
                return "LARGE_BIOMES";
            case "CUSTOMIZED":
                return "CUSTOMIZED";
            default:
                return "NORMAL";
        }
    }

    private void registerGeneratorToBukkitYml(String worldName) {
        try {
            File bukkitYml = new File("bukkit.yml");
            if (!bukkitYml.exists()) {
                this.plugin.getLogger().warning("bukkit.yml bulunamad\u0131, generator kayd\u0131 atlan\u0131yor.");
                return;
            }
            YamlConfiguration bukkitConfig = YamlConfiguration.loadConfiguration((File)bukkitYml);
            String generatorPath = "worlds." + worldName + ".generator";
            String generatorValue = String.valueOf(this.plugin.getName()) + ":empty";
            bukkitConfig.set(generatorPath, (Object)generatorValue);
            bukkitConfig.save(bukkitYml);
            this.plugin.getLogger().info("bukkit.yml'e " + worldName + " i\u00e7in generator kaydedildi: " + generatorValue);
        }
        catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "bukkit.yml g\u00fcncellenirken hata olu\u015ftu", e);
        }
    }

    private void unregisterGeneratorFromBukkitYml(String worldName) {
        try {
            File bukkitYml = new File("bukkit.yml");
            if (!bukkitYml.exists()) {
                return;
            }
            YamlConfiguration bukkitConfig = YamlConfiguration.loadConfiguration((File)bukkitYml);
            bukkitConfig.set("worlds." + worldName, null);
            bukkitConfig.save(bukkitYml);
        }
        catch (Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "bukkit.yml'den d\u00fcnya kald\u0131r\u0131l\u0131rken hata olu\u015ftu", e);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onWorldLoad(WorldLoadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();
        if (this.worldManagerConfig.contains("worlds." + worldName)) {
            this.applyStoredWorldSettings(world);
        }
        if (this.worldsLoadedEarly && event.getWorld().equals(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0))) {
            this.loadWorldsOnStartup();
            this.triggerPostWorldLoad();
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();
        if (this.worldManagerConfig.contains("worlds." + worldName)) {
            this.saveGameRules(worldName, world);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();
        if (this.worldManagerConfig.contains("worlds." + worldName)) {
            this.saveGameRules(worldName, world);
        }
    }

    public boolean isManagedWorld(String worldName) {
        return this.worldManagerConfig.contains("worlds." + worldName);
    }

    public Set<String> getManagedWorlds() {
        ConfigurationSection section = this.worldManagerConfig.getConfigurationSection("worlds");
        return section != null ? section.getKeys(false) : Collections.emptySet();
    }

    public void reloadConfig() {
        this.worldManagerConfig = YamlConfiguration.loadConfiguration((File)this.worldManagerFile);
    }
}
