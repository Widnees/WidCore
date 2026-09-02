package org.widnees.widCore.migrate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EssentialsHomeMigrator implements MigrateHandler {

    private final Main plugin;

    public EssentialsHomeMigrator(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "home";
    }

    @Override
    public MigrateResult migrate(File sourceFolder, boolean dryRun) {
        MigrateResult result = new MigrateResult();

        File[] files = sourceFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            result.addMessage(plugin.getLanguageManager().getMessage("migrate.no-files-found"));
            return result;
        }

        for (File file : files) {
            String fileName = file.getName();
            String uuidStr  = fileName.replace(".yml", "");

            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                result.addSkipped();
                result.addMessage("§cUUID geçersiz, atlandı: " + fileName);
                continue;
            }

            YamlConfiguration yml;
            try {
                yml = YamlConfiguration.loadConfiguration(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "migrate/home: " + fileName + " okunamadı", e);
                result.addFailed();
                continue;
            }

            ConfigurationSection homesSection = yml.getConfigurationSection("homes");
            if (homesSection == null) {
                result.addSkipped();
                continue;
            }

            Map<String, Location> homes = new HashMap<>();

            for (String homeName : homesSection.getKeys(false)) {
                ConfigurationSection homeData = homesSection.getConfigurationSection(homeName);
                if (homeData == null) continue;

                World world = null;
                String worldName = homeData.getString("world-name");
                if (worldName != null) {
                    world = Bukkit.getWorld(worldName);
                }
                if (world == null) {
                    String worldField = homeData.getString("world");
                    if (worldField != null) {
                        try {
                            UUID worldUuid = UUID.fromString(worldField);
                            world = Bukkit.getWorld(worldUuid);
                        } catch (IllegalArgumentException ignored) {
                            world = Bukkit.getWorld(worldField);
                        }
                    }
                }

                if (world == null) {
                    String missing = worldName != null ? worldName : homeData.getString("world", "?");
                    result.addSkipped();
                    result.addMessage("§eHome '" + homeName + "' atlandı (" + fileName + "): dünya bulunamadı → " + missing);
                    continue;
                }

                double x     = homeData.getDouble("x");
                double y     = homeData.getDouble("y");
                double z     = homeData.getDouble("z");
                float  yaw   = (float) homeData.getDouble("yaw");
                float  pitch = (float) homeData.getDouble("pitch");

                homes.put(homeName.toLowerCase(), new Location(world, x, y, z, yaw, pitch));
            }

            if (homes.isEmpty()) {
                result.addSkipped();
                continue;
            }

            if (!dryRun) {
                final UUID finalUuid = uuid;
                final Map<String, Location> finalHomes = homes;
                plugin.getDataManager().setPlayerHomes(finalUuid, finalHomes)
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.SEVERE,
                                    "migrate/home: " + finalUuid + " kaydedilemedi", ex);
                            return null;
                        });
            }

            result.addSuccess();
        }

        return result;
    }
}