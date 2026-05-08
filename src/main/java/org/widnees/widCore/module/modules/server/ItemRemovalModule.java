package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.ItemRemovalListener;
import org.widnees.widCore.module.Module;

public class ItemRemovalModule implements Module {
    private final Main plugin;

    public ItemRemovalModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Item Removal Timer";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.item_removal", false);
    }

    @Override
    public void register() {
        plugin.getItemRemovalManager().startup();
        plugin.getServer().getPluginManager()
                .registerEvents(new ItemRemovalListener(plugin, plugin.getItemRemovalManager()), plugin);
    }

    @Override
    public void unregister() {
    }
}