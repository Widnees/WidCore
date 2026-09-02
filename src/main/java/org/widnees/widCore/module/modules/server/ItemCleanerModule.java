package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.ItemCleanerListener;
import org.widnees.widCore.module.Module;

public class ItemCleanerModule implements Module {
    private final Main plugin;

    public ItemCleanerModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Item Cleaner";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.itemcleaner", false);
    }

    @Override
    public void register() {
        plugin.getItemCleanerManager().startup();
        plugin.getServer().getPluginManager()
                .registerEvents(new ItemCleanerListener(plugin, plugin.getItemCleanerManager()), plugin);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
