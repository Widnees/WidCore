package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.LightningCommand;
import org.widnees.widCore.listener.LightningListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class LightningModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public LightningModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Lightning Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.lightning", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("lightning.description");
        String usage = plugin.getLanguageManager().getMessage("lightning.usage_args");

        moduleManager.registerCommand(this, "lightning", desc, usage, "widcore.lightning", null, new LightningCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new LightningListener(), plugin);
    }

    @Override
    public void unregister() {}
}