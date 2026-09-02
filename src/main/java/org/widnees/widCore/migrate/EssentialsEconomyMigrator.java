package org.widnees.widCore.migrate;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.EconomyManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class EssentialsEconomyMigrator implements MigrateHandler {

    private final Main plugin;

    public EssentialsEconomyMigrator(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "economy";
    }

    @Override
    public MigrateResult migrate(File sourceFolder, boolean dryRun) {
        MigrateResult result = new MigrateResult();

        EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            result.addMessage("§cEconomy modülü aktif değil. Migration durduruldu.");
            return result;
        }

        File[] allFiles = sourceFolder.listFiles(File::isFile);
        if (allFiles == null || allFiles.length == 0) {
            result.addMessage(plugin.getLanguageManager().getMessage("migrate.no-files-found"));
            return result;
        }

        for (File file : allFiles) {
            String name = file.getName();
            if (name.endsWith(".dat")) {
                migrateDat(file, economyManager, dryRun, result);
            } else if (name.endsWith(".yml")) {
                migrateYml(file, economyManager, dryRun, result);
            }
        }

        if (!dryRun && result.getSuccess() > 0) {
            economyManager.saveEconomy();
        }

        return result;
    }


    private void migrateYml(File file, EconomyManager economyManager, boolean dryRun, MigrateResult result) {
        String fileName = file.getName();
        String uuidStr  = fileName.replace(".yml", "");

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            result.addSkipped();
            result.addMessage("§cUUID geçersiz, atlandı: " + fileName);
            return;
        }

        YamlConfiguration yml;
        try {
            yml = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "migrate/economy: " + fileName + " okunamadı", e);
            result.addFailed();
            return;
        }

        String moneyRaw = yml.getString("money");
        if (moneyRaw == null) {
            result.addSkipped();
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(moneyRaw.trim());
        } catch (NumberFormatException e) {
            result.addSkipped();
            result.addMessage("§cGeçersiz money değeri atlandı (" + fileName + "): " + moneyRaw);
            return;
        }

        if (!dryRun) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            economyManager.setBalance(op, amount);
        }
        result.addSuccess();
    }


    private void migrateDat(File file, EconomyManager economyManager, boolean dryRun, MigrateResult result) {
        plugin.getLogger().info("[Migrate] economy.dat okunuyor: " + file.getName());

        Object raw;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            raw = ois.readObject();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "migrate/economy: " + file.getName() + " okunamadı", e);
            result.addFailed();
            result.addMessage("§c" + file.getName() + " okunamadı: " + e.getMessage());
            return;
        }

        if (!(raw instanceof Map)) {
            result.addFailed();
            result.addMessage("§c" + file.getName() + " geçersiz format (Map bekleniyor).");
            return;
        }

        int fileSuccess = 0;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof UUID) || !(entry.getValue() instanceof Number)) {
                result.addSkipped();
                continue;
            }
            UUID uuid   = (UUID) entry.getKey();
            double amount = ((Number) entry.getValue()).doubleValue();

            if (!dryRun) {
                economyManager.setBalance(Bukkit.getOfflinePlayer(uuid), amount);
            }
            result.addSuccess();
            fileSuccess++;
        }
        plugin.getLogger().info("[Migrate] economy.dat: " + fileSuccess + " kayıt işlendi.");
    }
}