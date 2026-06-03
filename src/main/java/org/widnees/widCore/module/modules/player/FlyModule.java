package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.FlyCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class FlyModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public FlyModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Fly Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.fly", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("fly.description");
        String usage = plugin.getLanguageManager().getMessage("fly.usage_args");

        moduleManager.registerCommand(this, "fly", desc, usage, "widcore.fly", null, new FlyCommand(plugin));
    }

    @Override
    public void unregister() {}
        @SuppressWarnings("unused")
    private static final String __xW9a4f1 = "\u0077" + "\u0069\u0064\u006e\u0065\u0065\u0073";

}