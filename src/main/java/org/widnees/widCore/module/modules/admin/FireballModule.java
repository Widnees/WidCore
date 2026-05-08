package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.FireballCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class FireballModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public FireballModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Fireball Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.fireball", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("fireball.description");
        String usage = plugin.getLanguageManager().getMessage("fireball.usage_args");

        moduleManager.registerCommand(this, "fireball", desc, usage, "widcore.fireball", null, new FireballCommand(plugin));
    }

    @Override
    public void unregister() {}
}