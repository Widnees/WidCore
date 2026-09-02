package org.widnees.widCore.migrate;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.EconomyManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LegacyEconomyDatMigrator implements MigrateHandler {

    private final Main plugin;

    public LegacyEconomyDatMigrator(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "economy-dat";
    }

    @Override
    public MigrateResult migrate(File sourceFolder, boolean dryRun) {
        MigrateResult result = new MigrateResult();

        File[] datFiles = sourceFolder.listFiles((dir, name) -> name.endsWith(".dat"));
        if (datFiles == null || datFiles.length == 0) {
            result.addMessage("§eKlasörde işlenebilir .dat dosyası bulunamadı.");
            return result;
        }

        EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            result.addMessage("§cEconomy modülü aktif değil. Migration durduruldu.");
            return result;
        }

        for (File datFile : datFiles) {
            plugin.getLogger().info("[Migrate] economy-dat okunuyor: " + datFile.getName());

            Object raw;
            try {
                raw = readObject(datFile);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[Migrate] economy-dat: " + datFile.getName() + " okunamadı", e);
                result.addFailed();
                result.addMessage("§c" + datFile.getName() + " okunamadı: " + e.getMessage());
                continue;
            }

            if (!(raw instanceof Map)) {
                result.addFailed();
                result.addMessage("§c" + datFile.getName() + " geçersiz format (Map bekleniyor).");
                continue;
            }

            Map<?, ?> map = (Map<?, ?>) raw;
            int fileSuccess = 0;

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof UUID)) {
                    result.addSkipped();
                    continue;
                }
                if (!(entry.getValue() instanceof Number)) {
                    result.addSkipped();
                    continue;
                }

                UUID uuid   = (UUID) entry.getKey();
                double amount = ((Number) entry.getValue()).doubleValue();

                if (!dryRun) {
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    economyManager.setBalance(offlinePlayer, amount);
                }
                result.addSuccess();
                fileSuccess++;
            }

            plugin.getLogger().info("[Migrate] economy-dat: " + datFile.getName()
                    + " → " + fileSuccess + " kayıt işlendi.");
        }

        if (!dryRun && result.getSuccess() > 0) {
            economyManager.saveEconomy();
        }

        return result;
    }

    private Object readObject(File file) throws Exception {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return ois.readObject();
        }
    }
}