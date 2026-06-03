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
        @SuppressWarnings("unused")
    private static final String _0xWd3f9b = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}