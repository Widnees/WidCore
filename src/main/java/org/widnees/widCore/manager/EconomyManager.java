package org.widnees.widCore.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.widnees.widCore.Main;
import org.widnees.widCore.hook.WidCoreEconomy;
import org.widnees.widCore.util.FoliaScheduler;

public class EconomyManager {
    private final Main plugin;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<UUID, Double>();
    private WidCoreEconomy vaultImpl;

    public Map<UUID, Double> getAllBalances() {
        return new ConcurrentHashMap<UUID, Double>(this.balances);
    }

    public String formatMoney(double amount) {
        return String.valueOf(String.format("%.2f", amount)) + " " + this.plugin.getLanguageManager().getMessage("economy.symbol");
    }

    public EconomyManager(Main plugin) {
        this.plugin = plugin;
        this.loadEconomy();
        this.startAutoSaveTask();
        this.setupVault();
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            this.vaultImpl = new WidCoreEconomy(this.plugin, this);
            Bukkit.getServicesManager().register(Economy.class, this.vaultImpl, (Plugin)this.plugin, ServicePriority.Normal);
        }
    }

    private void loadEconomy() {
        this.plugin.getDataManager().loadEconomy(loadedData -> {
            this.balances.clear();
            this.balances.putAll((Map<UUID, Double>)loadedData);
        });
    }

    public void saveEconomy() {
        this.plugin.getDataManager().saveEconomy(this.balances);
    }

    private void startAutoSaveTask() {
        FoliaScheduler.runTaskTimerAsync((Plugin)this.plugin, this::saveEconomy, 12000L, 12000L);
    }

    public double getBalance(OfflinePlayer player) {
        return this.balances.getOrDefault(player.getUniqueId(), 0.0);
    }

    public void setBalance(OfflinePlayer player, double amount) {
        this.balances.put(player.getUniqueId(), amount);
    }

    public void deposit(OfflinePlayer player, double amount) {
        this.setBalance(player, this.getBalance(player) + amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        double current = this.getBalance(player);
        if (current >= amount) {
            this.setBalance(player, current - amount);
            return true;
        }
        return false;
    }

    public boolean has(OfflinePlayer player, double amount) {
        return this.getBalance(player) >= amount;
    }

    public void createAccount(OfflinePlayer player) {
        if (!this.balances.containsKey(player.getUniqueId())) {
            this.balances.put(player.getUniqueId(), 0.0);
        }
    }

    public void shutdown() {
        this.saveEconomy();
    }
}
