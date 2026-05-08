package org.widnees.widCore.module.modules.world;

import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.SetSpawnCommand;
import org.widnees.widCore.command.SpawnCommand;
import org.widnees.widCore.listener.AliasListener;
import org.widnees.widCore.listener.SpawnListener;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class SpawnModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public SpawnModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Spawn System";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.spawn", false);
    }

    @Override
    public void register() {
        FileConfiguration spawnConfig = plugin.getConfigManager().getModuleConfig("spawn");
        SpawnCommand spawnCommandExecutor = new SpawnCommand(plugin, plugin.getSpawnLocationManager(), spawnConfig,
                plugin.getTeleportManager(), plugin.getTeleportAnimator());
        AliasManager aliasManager = plugin.getAliasManager();

        String setspawnDesc = plugin.getLanguageManager().getMessage("spawn.setspawn_description");
        String setspawnUsage = plugin.getLanguageManager().getMessage("spawn.setspawn_usage_args");

        String spawnDesc = plugin.getLanguageManager().getMessage("spawn.description");
        String spawnUsage = plugin.getLanguageManager().getMessage("spawn.usage_args");

        moduleManager.registerCommand(this, "setspawn", setspawnDesc, setspawnUsage,
                aliasManager.getPermission("setspawn"), aliasManager.getAliases("setspawn"),
                new SetSpawnCommand(plugin, plugin.getSpawnLocationManager()));
        moduleManager.registerCommand(this, "spawn", spawnDesc, spawnUsage,
                aliasManager.getPermission("spawn"), aliasManager.getAliases("spawn"),
                spawnCommandExecutor);

        plugin.getServer().getPluginManager().registerEvents(new AliasListener(plugin, spawnCommandExecutor), plugin);
        plugin.getServer().getPluginManager()
                .registerEvents(new SpawnListener(plugin, plugin.getSpawnLocationManager(), spawnConfig), plugin);

        if (plugin.getConfig().getBoolean("features.warp", false)) {
            plugin.getServer().getPluginManager()
                    .registerEvents(new org.widnees.widCore.listener.TeleportDamageListener(plugin,
                            plugin.getTeleportManager(), plugin.getTeleportAnimator()), plugin);
        }
    }

    @Override
    public void unregister() {
    }
}