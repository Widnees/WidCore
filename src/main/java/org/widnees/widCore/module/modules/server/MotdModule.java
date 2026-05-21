package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.MotdListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class MotdModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public MotdModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "MOTD";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.motd", false);
    }

    @Override
    public void register() {
        plugin.getServer().getPluginManager().registerEvents(new MotdListener(plugin), plugin);
    }

    @Override
    public void unregister() {
    }

    @Override
    public java.util.List<String> getMissingOptionalDependencies() {
        if (!isPacketEventsAvailable()) {
            return java.util.List.of("player-list hover hiding disabled (requires packetevents)");
        }
        return java.util.List.of();
    }

    private static boolean isPacketEventsAvailable() {
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            return com.github.retrooper.packetevents.PacketEvents.getAPI() != null;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }
}