package org.widnees.widCore.module.modules.world;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.SetVoidSpawnCommand;
import org.widnees.widCore.listener.VoidSpawnListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class VoidSpawnModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public VoidSpawnModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Void Spawn"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.void-spawn", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("voidspawn.setvoidspawn_description");
        String usage = plugin.getLanguageManager().getMessage("voidspawn.setvoidspawn_usage_args");

        moduleManager.registerCommand(this, "setvoidspawn", desc, usage, "widcore.voidspawn.set", null, new SetVoidSpawnCommand(plugin, plugin.getVoidSpawnManager()));
        plugin.getServer().getPluginManager().registerEvents(new VoidSpawnListener(plugin, plugin.getVoidSpawnManager()), plugin);
    }

    @Override
    public void unregister() {}
}