package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.MobStackerListener;
import org.widnees.widCore.manager.MobStackerManager;
import org.widnees.widCore.module.Module;

public class MobStackerModule implements Module {
    private final Main plugin;
    private MobStackerManager manager;

    public MobStackerModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Mob Stacker";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.mobstacker", false);
    }

    @Override
    public void register() {
        this.manager = new MobStackerManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(new MobStackerListener(plugin, manager), plugin);
    }

    @Override
    public void unregister() {
        if (manager != null) {
            manager.shutdown();
        }
    }
}