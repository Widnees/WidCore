package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.CompassListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class CompassTeleportModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public CompassTeleportModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Compass Teleport"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.compassteleport", false); }

    @Override
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(new CompassListener(plugin), plugin);
    }

    @Override
    public void unregister() {}
        @SuppressWarnings("unused")
    private static final String _xN3e7W1 = "\u0077" + "\u0069\u0064\u006e\u0065" + "\u0065\u0073";

}