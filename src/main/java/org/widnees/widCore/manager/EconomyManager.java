package org.widnees.widCore.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
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
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            this.vaultImpl = new WidCoreEconomy(this.plugin, this);
            forceRegisterEconomy();
            Bukkit.getPluginManager().registerEvents(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onServiceRegister(ServiceRegisterEvent event) {
                    if (event.getProvider().getService() == Economy.class
                            && event.getProvider().getProvider() != vaultImpl) {
                        forceRegisterEconomy();
                    }
                }
            }, this.plugin);
            // Periodically re-register for the first ~30 seconds after startup so that
            // plugins which load their economy late (EssentialsX, CMI, etc.) cannot
            // permanently override WidCore as the active Vault Economy provider.
            final int[] attempts = {0};
            final int maxAttempts = 30; // 30 × 20 ticks = ~30 seconds
            final Object[] taskHolder = {null};
            taskHolder[0] = FoliaScheduler.runTaskTimer((Plugin) this.plugin, () -> {
                attempts[0]++;
                RegisteredServiceProvider<Economy> rsp =
                        Bukkit.getServicesManager().getRegistration(Economy.class);
                if (rsp == null || rsp.getProvider() != vaultImpl) {
                    forceRegisterEconomy();
                }
                if (attempts[0] >= maxAttempts) {
                    FoliaScheduler.cancelTask(taskHolder[0]);
                }
            }, 20L, 20L);
        }
    }

    private void forceRegisterEconomy() {
        Bukkit.getServicesManager().unregister(Economy.class, this.vaultImpl);
        Bukkit.getServicesManager().register(Economy.class, this.vaultImpl, (Plugin)this.plugin, ServicePriority.Highest);
        refreshPapiVaultExpansion();
    }

    private void refreshPapiVaultExpansion() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPIPlugin");
            Object papiInstance = papiClass.getMethod("getInstance").invoke(null);
            Object expansionManager = papiInstance.getClass().getMethod("getLocalExpansionManager").invoke(papiInstance);
            Object expansions = expansionManager.getClass().getMethod("getExpansions").invoke(expansionManager);
            for (Object exp : (java.util.Collection<?>) expansions) {
                String id = (String) exp.getClass().getMethod("getIdentifier").invoke(exp);
                if ("vault".equalsIgnoreCase(id)) {
                    exp.getClass().getMethod("unregister").invoke(exp);
                    exp.getClass().getMethod("register").invoke(exp);
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void loadEconomy() {
        // Register Vault immediately so other plugins that check for an economy
        // provider during their own onEnable() find one right away. The balance
        // map will be populated shortly after by the async callback below.
        this.setupVault();
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
        // Unregister from Vault so stale provider instances don't linger after
        // a PlugMan reload — otherwise the old WidCoreEconomy object remains
        // registered and PAPI reads from it (with no balances) instead of the
        // newly registered instance.
        if (this.vaultImpl != null && Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            Bukkit.getServicesManager().unregister(Economy.class, this.vaultImpl);
        }
    }
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}