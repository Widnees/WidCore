package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.TempFlyCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class TempFlyModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public TempFlyModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "TempFly Command";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.tempfly", false);
    }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("tempfly.description");
        String usage = plugin.getLanguageManager().getMessage("tempfly.usage_args");

        TempFlyCommand command = new TempFlyCommand(plugin);
        moduleManager.registerCommand(this, "tempfly", desc, usage, null, null, command);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _0xNe3s7b = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
