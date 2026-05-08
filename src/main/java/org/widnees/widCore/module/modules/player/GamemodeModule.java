package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.GamemodeCommand;
import org.widnees.widCore.listener.GamemodeListener;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

import java.util.List;

public class GamemodeModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public GamemodeModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Gamemode Commands";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.gamemode", false);
    }

    @Override
    public void register() {
        AliasManager aliasManager = plugin.getAliasManager();
        GamemodeCommand gmExecutor = new GamemodeCommand(plugin);

        String cmdName = aliasManager.getCommand("gamemode");
        List<String> aliases = aliasManager.getAliases("gamemode");
        String permission = aliasManager.getPermission("gamemode");

        String desc = plugin.getLanguageManager().getMessage("gamemode.description");
        String usage = plugin.getLanguageManager().getMessage("gamemode.usage_args");

        moduleManager.registerCommand(this, cmdName, desc, usage, permission, aliases, gmExecutor);
        plugin.getServer().getPluginManager().registerEvents(new GamemodeListener(plugin, gmExecutor), plugin);
    }

    @Override
    public void unregister() {
    }
}