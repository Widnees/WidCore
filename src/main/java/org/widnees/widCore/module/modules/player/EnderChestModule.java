package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.EnderChestCommand;
import org.widnees.widCore.database.PlayerDataListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class EnderChestModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public EnderChestModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "EnderChest Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.enderchest", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("enderchest.description");
        String usage = plugin.getLanguageManager().getMessage("enderchest.usage_args");

        moduleManager.registerCommand(this, "enderchest", desc, usage, "widcore.ec", null, new EnderChestCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new PlayerDataListener(plugin), plugin);
    }

    @Override
    public void unregister() {}
}