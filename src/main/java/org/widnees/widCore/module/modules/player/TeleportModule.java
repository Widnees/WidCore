package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.TeleportCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class TeleportModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public TeleportModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Teleport Commands";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.teleport", false);
    }

    @Override
    public void register() {
        TeleportCommand teleportExecutor = new TeleportCommand(plugin);

        String tpDesc = plugin.getLanguageManager().getMessage("teleport.description");
        String tpUsage = plugin.getLanguageManager().getMessage("teleport.usage_args");

        String tpAllDesc = plugin.getLanguageManager().getMessage("teleport.tpall_description");
        String tpAllUsage = plugin.getLanguageManager().getMessage("teleport.tpall_usage_args");

        moduleManager.registerCommand(this, "teleport", tpDesc, tpUsage, "widcore.tp", null, teleportExecutor);

        moduleManager.registerCommand(this, "tphere", tpDesc, tpUsage, "widcore.tphere", null, teleportExecutor);
        moduleManager.registerCommand(this, "tpall", tpAllDesc, tpAllUsage, "widcore.tp.all", null, teleportExecutor);
    }

    @Override
    public void unregister() {
    }
}