package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.listener.CommandGuardListener;
import org.widnees.widCore.listener.ServerInfoHiderListener;
import org.widnees.widCore.listener.TabCompleteGuardListener;
import org.widnees.widCore.manager.CommandAccessManager;
import org.widnees.widCore.module.Module;

import java.lang.reflect.Method;

public class ServerInfoHiderModule implements Module {
    private final Main plugin;

    public ServerInfoHiderModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Plugin Hider";
    }

    @Override
    public boolean isEnabled() {

        if (plugin.getConfig().isSet("features.pluginhider")) {
            return plugin.getConfig().getBoolean("features.pluginhider", false);
        }
        return plugin.getConfig().getBoolean("features.serverinfohider", false);
    }

    @Override
    public void register() {
        var cfg = plugin.getConfigManager().getModuleConfig("plugin-hider");

        plugin.getServer().getPluginManager().registerEvents(new ServerInfoHiderListener(plugin, cfg), plugin);

        CommandAccessManager accessManager = new CommandAccessManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(new CommandGuardListener(plugin, accessManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TabCompleteGuardListener(accessManager), plugin);

        try {
            Class<?> hiderClass = Class.forName("org.widnees.widCore.listener.PacketEventsBrandHider");
            Method regMethod = hiderClass.getMethod("register", Main.class,
                    org.bukkit.configuration.file.FileConfiguration.class);
            regMethod.invoke(null, plugin, cfg);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {

        } catch (Exception ignored) {}
    }

    @Override
    public void unregister() {
    }

    @Override
    public java.util.List<String> getMissingOptionalDependencies() {
        if (!isPacketEventsAvailable()) {
            return java.util.List.of("brand spoofing disabled (requires packetevents)");
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
