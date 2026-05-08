package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.BannedItemListener;
import org.widnees.widCore.module.Module;

public class BannedItemModule implements Module {

    private final Main plugin;

    public BannedItemModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "banneditem";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.banneditem", false);
    }

    @Override
    public void register() {
        plugin.getBannedItemManager().loadBannedItems();
        plugin.getServer().getPluginManager().registerEvents(new BannedItemListener(plugin, plugin.getBannedItemManager()), plugin);
    }

    @Override
    public void unregister() {
    }
}