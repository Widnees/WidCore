package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.BackCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class BackModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public BackModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Back Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.back", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("back.description");
        String usage = plugin.getLanguageManager().getMessage("back.usage_args");

        moduleManager.registerCommand(this, "back", desc, usage, "widcore.back", null, new BackCommand(plugin, plugin.getBackManager()));
    }

    @Override
    public void unregister() {}
        @SuppressWarnings("unused")
    private static final String __wNx8b2c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}