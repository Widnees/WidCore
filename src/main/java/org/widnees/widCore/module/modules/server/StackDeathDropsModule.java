package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.module.Module;

public class StackDeathDropsModule implements Module {
    private final Main plugin;
    public StackDeathDropsModule(Main plugin) { this.plugin = plugin; }
    @Override public String getName() { return "Stack Death Drops"; }
    @Override public boolean isEnabled() { return plugin.getConfig().getBoolean("features.stack_death_drops", false); }
    @Override public void register() {
    }
    @Override public void unregister() {}
        @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}