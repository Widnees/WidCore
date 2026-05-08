package org.widnees.widCore.module.modules.admin;

import org.bukkit.Bukkit;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.DisguiseCommand;
import org.widnees.widCore.command.UndisguiseCommand;
import org.widnees.widCore.listener.DisguiseListener;
import org.widnees.widCore.listener.DisguisePacketHandler;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

import java.util.Arrays;
import java.util.List;

public class DisguiseModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public DisguiseModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Disguise";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.disguise", false);
    }

    @Override
    public List<String> getMissingDependencies() {
        
        try {
            Class.forName("com.github.retrooper.packetevents.PacketEvents");
            if (com.github.retrooper.packetevents.PacketEvents.getAPI() != null) {
                return List.of();
            }
        } catch (ClassNotFoundException | IllegalStateException ignored) {}

        for (org.bukkit.plugin.Plugin p : Bukkit.getPluginManager().getPlugins()) {
            if (p.getName().equalsIgnoreCase("packetevents") && p.isEnabled()) {
                return List.of();
            }
        }

        return List.of("packetevents");
    }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("disguise.description");
        String usage = plugin.getLanguageManager().getMessage("disguise.usage_args");

        moduleManager.registerCommand(this, "disguise", desc, usage, "widcore.disguise",
                Arrays.asList(), new DisguiseCommand(plugin));

        String undescDesc = plugin.getLanguageManager().getMessage("disguise.undisguise-description");
        moduleManager.registerCommand(this, "undisguise", undescDesc, "/undisguise [player]", "widcore.disguise",
                Arrays.asList(), new UndisguiseCommand(plugin));

        plugin.getServer().getPluginManager().registerEvents(new DisguiseListener(plugin), plugin);

        DisguisePacketHandler.register(plugin);
    }

    @Override
    public void unregister() {
        if (plugin.getDisguiseManager() != null) {
            plugin.getDisguiseManager().undisguiseAll();
        }
        DisguisePacketHandler.unregister();
    }
}