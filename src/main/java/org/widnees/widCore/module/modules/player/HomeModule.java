package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.HomeCommand;
import org.widnees.widCore.listener.HomeListener;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

import java.util.List;

public class HomeModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public HomeModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Home System";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.home", false);
    }

    @Override
    public void register() {
        AliasManager aliasManager = plugin.getAliasManager();
        HomeListener homeListener = new HomeListener(plugin);
        HomeCommand homeExecutor = new HomeCommand(plugin, homeListener);

        String homeDesc = plugin.getLanguageManager().getMessage("home.description");
        String homeUsage = plugin.getLanguageManager().getMessage("home.usage_args");
        registerCommand(homeExecutor, "home", homeDesc, homeUsage,
                aliasManager.getPermission("home"),
                aliasManager.getAliases("home"));

        String sethomeDesc = plugin.getLanguageManager().getMessage("home.sethome_description");
        String sethomeUsage = plugin.getLanguageManager().getMessage("home.sethome_usage_args");
        registerCommand(homeExecutor, "sethome", sethomeDesc, sethomeUsage,
                aliasManager.getPermission("sethome"),
                aliasManager.getAliases("sethome"));

        String delhomeDesc = plugin.getLanguageManager().getMessage("home.delhome_description");
        String delhomeUsage = plugin.getLanguageManager().getMessage("home.delhome_usage_args");
        registerCommand(homeExecutor, "delhome", delhomeDesc, delhomeUsage,
                aliasManager.getPermission("delhome"),
                aliasManager.getAliases("delhome"));

        plugin.getServer().getPluginManager().registerEvents(homeListener, plugin);
    }

    private void registerCommand(HomeCommand executor, String name, String desc, String usage, String perm,
            List<String> aliases) {
        moduleManager.registerCommand(this, name, desc, usage, perm, aliases, executor);
    }

    @Override
    public void unregister() {
    }
}
