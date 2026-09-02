package org.widnees.widCore.migrate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.WarpManager;

import java.io.File;
import java.util.UUID;
import java.util.logging.Level;

public class EssentialsWarpMigrator implements MigrateHandler {

    private final Main plugin;

    public EssentialsWarpMigrator(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "warp";
    }

    @Override
    public MigrateResult migrate(File sourceFolder, boolean dryRun) {
        MigrateResult result = new MigrateResult();

        File[] files = sourceFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            result.addMessage(plugin.getLanguageManager().getMessage("migrate.no-files-found"));
            return result;
        }

        WarpManager warpManager = plugin.getWarpManager();
        if (warpManager == null) {
            result.addMessage("§cWarp modülü aktif değil. Migration durduruldu.");
            return result;
        }

        for (File file : files) {
            String fileName = file.getName();

            YamlConfiguration yml;
            try {
                yml = YamlConfiguration.loadConfiguration(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "migrate/warp: " + fileName + " okunamadı", e);
                result.addFailed();
                continue;
            }

            String warpName = yml.getString("name");
            if (warpName == null || warpName.isEmpty()) {
                warpName = fileName.replace(".yml", "");
            }

            World world = null;
            String worldName = yml.getString("world-name");
            if (worldName != null) {
                world = Bukkit.getWorld(worldName);
            }
            if (world == null) {
                String worldField = yml.getString("world");
                if (worldField != null) {
                    try {
                        world = Bukkit.getWorld(UUID.fromString(worldField));
                    } catch (IllegalArgumentException ignored) {
                        world = Bukkit.getWorld(worldField);
                    }
                }
            }

            if (world == null) {
                String missing = worldName != null ? worldName : yml.getString("world", "?");
                result.addSkipped();
                result.addMessage("§eWarp '" + warpName + "' atlandı: dünya bulunamadı → " + missing);
                continue;
            }

            double x     = yml.getDouble("x");
            double y     = yml.getDouble("y");
            double z     = yml.getDouble("z");
            float  yaw   = (float) yml.getDouble("yaw");
            float  pitch = (float) yml.getDouble("pitch");

            Location location = new Location(world, x, y, z, yaw, pitch);

            if (!dryRun) {
                warpManager.setWarp(warpName, location);
            }
            result.addSuccess();
        }

        return result;
    }
}