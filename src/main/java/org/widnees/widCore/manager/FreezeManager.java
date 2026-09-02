package org.widnees.widCore.manager;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;

public class FreezeManager {
    private final Main plugin;
    private final File freezeFile;
    private FileConfiguration freezeConfig;
    private final Map<UUID, Long> frozenPlayers = new ConcurrentHashMap<UUID, Long>();

    public FreezeManager(Main plugin) {
        this.plugin = plugin;
        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        this.freezeFile = new File(databaseDir, "freezes.yml");
        this.loadFreezes();
    }

    public void loadFreezes() {
        if (!this.freezeFile.exists()) {
            try {
                this.freezeFile.createNewFile();
            }
            catch (IOException e) {
                this.plugin.getLogger().log(Level.SEVERE, this.plugin.getLanguageManager().getMessage("database.create-error").replace("%file%", "freezes.yml"), e);
            }
        }
        this.freezeConfig = YamlConfiguration.loadConfiguration((File)this.freezeFile);
        this.frozenPlayers.clear();
        if (this.freezeConfig.isConfigurationSection("frozen-players")) {
            for (String uuidString : this.freezeConfig.getConfigurationSection("frozen-players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    long expiry = this.freezeConfig.getLong("frozen-players." + uuidString);
                    this.frozenPlayers.put(uuid, expiry);
                }
                catch (IllegalArgumentException e) {
                    this.plugin.getLogger().warning(this.plugin.getLanguageManager().getMessage("database.invalid-data").replace("%file%", "freezes.yml").replace("%data%", uuidString));
                }
            }
        }
        this.plugin.getLogger().info(this.plugin.getLanguageManager().getMessage("database.loaded-count").replace("%count%", String.valueOf(this.frozenPlayers.size())).replace("%type%", "dondurulmu\u015f oyuncu"));
    }

    public void saveFreezes() {
        if (this.freezeConfig == null) {
            this.freezeConfig = YamlConfiguration.loadConfiguration((File)this.freezeFile);
        }
        for (String string : this.freezeConfig.getKeys(false)) {
            this.freezeConfig.set(string, null);
        }
        if (!this.frozenPlayers.isEmpty()) {
            for (Map.Entry entry : this.frozenPlayers.entrySet()) {
                this.freezeConfig.set("frozen-players." + ((UUID)entry.getKey()).toString(), entry.getValue());
            }
        }
        try {
            this.freezeConfig.save(this.freezeFile);
        }
        catch (IOException iOException) {
            this.plugin.getLogger().log(Level.SEVERE, this.plugin.getLanguageManager().getMessage("database.save-error").replace("%file%", "freezes.yml"), iOException);
        }
    }

    public void freeze(UUID uuid, long durationMillis) {
        long expiry = durationMillis == -1L ? -1L : System.currentTimeMillis() + durationMillis;
        this.frozenPlayers.put(uuid, expiry);
        this.saveFreezes();
    }

    public void unfreeze(UUID uuid) {
        this.frozenPlayers.remove(uuid);
        this.saveFreezes();
    }

    public boolean isFrozen(UUID uuid) {
        if (!this.frozenPlayers.containsKey(uuid)) {
            return false;
        }
        long expiry = this.frozenPlayers.get(uuid);
        if (expiry == -1L) {
            return true;
        }
        if (System.currentTimeMillis() < expiry) {
            return true;
        }
        this.unfreeze(uuid);
        return false;
    }

    public String getFormattedRemainingTime(UUID uuid) {
        if (!this.frozenPlayers.containsKey(uuid)) {
            return this.plugin.getLanguageManager().getMessage("freeze.status-na");
        }
        long expiry = this.frozenPlayers.get(uuid);
        if (expiry == -1L) {
            return this.plugin.getLanguageManager().getMessage("freeze.status-perm");
        }
        long remainingMillis = expiry - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            return this.plugin.getLanguageManager().getMessage("freeze.status-expired");
        }
        return this.plugin.getPunishmentManager().formatDuration(remainingMillis);
    }
        @SuppressWarnings("unused")
    private static final String __xW9a4f1 = "\u0077" + "\u0069\u0064\u006e\u0065\u0065\u0073";

}
