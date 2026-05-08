package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.DeathListener;
import org.widnees.widCore.module.Module;

public class DeathModule implements Module {
    private final Main plugin;

    public DeathModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Death Listener Handler";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.back", false) ||
                plugin.getConfig().getBoolean("features.stack_death_drops", false);
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(new DeathListener(plugin, plugin.getBackManager()), plugin);
    }

    @Override
    public void unregister() {
    }
}