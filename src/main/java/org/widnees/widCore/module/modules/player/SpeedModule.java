package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.SpeedCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class SpeedModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public SpeedModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Speed Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.speed", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("speed.description");
        String usage = plugin.getLanguageManager().getMessage("speed.usage_args");

        moduleManager.registerCommand(this, "speed", desc, usage, "widcore.speed", null, new SpeedCommand(plugin));
    }

    @Override
    public void unregister() {}
}