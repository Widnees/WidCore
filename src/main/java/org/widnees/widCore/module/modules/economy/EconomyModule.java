package org.widnees.widCore.module.modules.economy;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.BaltopCommand;
import org.widnees.widCore.command.EconomyCommand;
import org.widnees.widCore.command.PayCommand;
import org.widnees.widCore.listener.BaltopListener;
import org.widnees.widCore.listener.EconomyListener;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.manager.BaltopManager;
import org.widnees.widCore.manager.EconomyManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class EconomyModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;
    private EconomyManager economyManager;
    private BaltopManager baltopManager;

    public EconomyModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Economy System";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.economy", false);
    }
    
    @Override
    public java.util.List<String> getMissingDependencies() {
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return java.util.List.of("Vault");
        }
        return java.util.List.of();
    }

    @Override
    public void register() {
        this.economyManager = new EconomyManager(plugin);
        this.baltopManager = new BaltopManager(plugin, economyManager);

        AliasManager aliasManager = plugin.getAliasManager();

        String ecoDesc = plugin.getLanguageManager().getMessage("economy.eco-desc");
        String ecoUsage = plugin.getLanguageManager().getMessage("economy.eco-usage");
        
        moduleManager.registerCommand(this, "economy", ecoDesc, ecoUsage,
                aliasManager.getPermission("economy"), aliasManager.getAliases("economy"),
                new EconomyCommand(plugin, economyManager));

        String payDesc = plugin.getLanguageManager().getMessage("economy.pay-desc");
        String payUsage = plugin.getLanguageManager().getMessage("economy.pay-usage");
        moduleManager.registerCommand(this, "pay", payDesc, payUsage,
                aliasManager.getPermission("pay"), aliasManager.getAliases("pay"),
                new PayCommand(plugin, economyManager));

        String baltopDesc = "Shows the richest players.";
        String baltopUsage = "/baltop";
        moduleManager.registerCommand(this, "baltop", baltopDesc, baltopUsage,
                aliasManager.getPermission("baltop"), aliasManager.getAliases("baltop"),
                new BaltopCommand(plugin, baltopManager));

        plugin.getServer().getPluginManager().registerEvents(new EconomyListener(economyManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new BaltopListener(plugin), plugin);
    }

    @Override
    public void unregister() {
        if (economyManager != null) {
            economyManager.shutdown();
        }
    }
}