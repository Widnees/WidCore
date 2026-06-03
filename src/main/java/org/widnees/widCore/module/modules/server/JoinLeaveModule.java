package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.module.Module;

public class JoinLeaveModule implements Module {
    private final Main plugin;
    public JoinLeaveModule(Main plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Join/Leave Messages"; }
    @Override public boolean isEnabled() { return plugin.getConfig().getBoolean("features.joinleave", false); }
    @Override public void register() {
        plugin.getServer().getPluginManager().registerEvents(plugin.getJoinLeaveListener(), plugin);
    }
    @Override public void unregister() {}
        @SuppressWarnings("unused")
    private static final String __xW9a4f1 = "\u0077" + "\u0069\u0064\u006e\u0065\u0065\u0073";

}