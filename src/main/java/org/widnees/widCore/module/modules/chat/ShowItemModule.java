package org.widnees.widCore.module.modules.chat;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.ShowItemListener; 
import org.widnees.widCore.module.Module;

public class ShowItemModule implements Module {
    private final Main plugin;
    public ShowItemModule(Main plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Show Item in Chat"; }
    @Override public boolean isEnabled() { return plugin.getConfig().getBoolean("features.show-item", false); }

    @Override public void register() {
        plugin.getServer().getPluginManager().registerEvents(new ShowItemListener(plugin, plugin.getShowItemManager()), plugin);
    }
    @Override public void unregister() {}
}