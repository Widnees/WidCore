package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.FireballStickCommand;
import org.widnees.widCore.listener.FireballStickListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class FireballStickModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public FireballStickModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Fireball Stick"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.fireballstick", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("fireballstick.description");
        String usage = plugin.getLanguageManager().getMessage("fireballstick.usage_args");

        moduleManager.registerCommand(this, "fireballstick", desc, usage, "widcore.fireball", null, new FireballStickCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new FireballStickListener(plugin), plugin);
    }

    @Override
    public void unregister() {}
}