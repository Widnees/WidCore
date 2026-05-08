package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.GodCommand;
import org.widnees.widCore.listener.GodModeListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class GodModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public GodModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Godmode"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.godmode", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("god.description");
        String usage = plugin.getLanguageManager().getMessage("god.usage_args");

        moduleManager.registerCommand(this, "god", desc, usage, "widcore.god", Arrays.asList("godmode"), new GodCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new GodModeListener(plugin), plugin);
    }

    @Override
    public void unregister() {}
}