package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.HealCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class HealModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public HealModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Heal Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.heal", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("heal.description");
        String usage = plugin.getLanguageManager().getMessage("heal.usage_args");

        moduleManager.registerCommand(this, "heal", desc, usage, "widcore.heal", null, new HealCommand(plugin));
    }

    @Override
    public void unregister() {}
}