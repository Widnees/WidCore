package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.HeadCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class HeadModule implements Module {

    private final Main plugin;
    private final ModuleManager moduleManager;

    public HeadModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Head";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.head", true);
    }

    @Override
    public void register() {
        String description = plugin.getLanguageManager().getMessage("head.description");
        String usage = plugin.getLanguageManager().getMessage("head.usage_args");

        String command = plugin.getAliasManager().getCommand("head");
        String permission = plugin.getAliasManager().getPermission("head");
        java.util.List<String> aliases = plugin.getAliasManager().getAliases("head");

        moduleManager.registerCommand(this, command, description, usage, permission, aliases, new HeadCommand(plugin));
    }

    @Override
    public void unregister() {
        
    }
}
