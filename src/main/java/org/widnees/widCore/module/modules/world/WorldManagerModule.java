package org.widnees.widCore.module.modules.world;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.WorldManagerCommand;
import org.widnees.widCore.listener.WorldManagerListener;
import org.widnees.widCore.manager.WorldManagerGUI;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

import java.util.Arrays;

public class WorldManagerModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;
    private WorldManagerGUI worldManagerGUI;
    private WorldManagerListener worldManagerListener;

    public WorldManagerModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "World Manager";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.worldmanager", false);
    }

    @Override
    public void register() {

        this.worldManagerGUI = new WorldManagerGUI(plugin);

        WorldManagerCommand worldManagerCommand = new WorldManagerCommand(plugin, plugin.getWorldDataManager());
        worldManagerCommand.setWorldManagerGUI(worldManagerGUI);

        this.worldManagerListener = new WorldManagerListener(plugin, worldManagerGUI);
        plugin.getServer().getPluginManager().registerEvents(worldManagerListener, plugin);

        String desc = plugin.getLanguageManager().getMessage("worldmanager.description");
        String usage = plugin.getLanguageManager().getMessage("worldmanager.usage_args");

        moduleManager.registerCommand(this, "worldmanager", desc, usage, "widcore.worldmanager.tp", Arrays.asList("wm"),
                worldManagerCommand);
    }

    @Override
    public void unregister() {

        this.worldManagerGUI = null;
        this.worldManagerListener = null;
    }

    public WorldManagerGUI getWorldManagerGUI() {
        return worldManagerGUI;
    }
}