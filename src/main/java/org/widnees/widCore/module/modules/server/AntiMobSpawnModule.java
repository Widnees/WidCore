package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.AntiMobSpawnListener;
import org.widnees.widCore.module.Module;

public class AntiMobSpawnModule implements Module {

    private final Main plugin;

    public AntiMobSpawnModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "antimobspawn";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.antimobspawn", false);
    }

    @Override
    public void register() {
        plugin.getAntiMobSpawnManager().loadConfig();
        plugin.getServer().getPluginManager().registerEvents(new AntiMobSpawnListener(plugin.getAntiMobSpawnManager()), plugin);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _xW3c9f4 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}