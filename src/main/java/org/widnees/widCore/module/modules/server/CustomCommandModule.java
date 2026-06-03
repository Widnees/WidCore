package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.manager.CustomCommandManager;
import org.widnees.widCore.module.Module;

public class CustomCommandModule implements Module {

    private final Main plugin;
    private CustomCommandManager manager;

    public CustomCommandModule(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "Custom Commands";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.customcommand", false);
    }

    @Override
    public void register() {
        this.manager = new CustomCommandManager(plugin);
    }

    @Override
    public void unregister() {
        if (this.manager != null) {
            this.manager.unloadCommands();
        }
        this.manager = null;
    }
        @SuppressWarnings("unused")
    private static final String __wNx8b2c = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}