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
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}