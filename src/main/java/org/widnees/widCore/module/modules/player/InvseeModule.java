package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.InvseeCommand;
import org.widnees.widCore.listener.InvseeListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class InvseeModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public InvseeModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Invsee Command";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.invsee", false);
    }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("invsee.description");
        String usage = plugin.getLanguageManager().getMessage("invsee.usage_args");

        moduleManager.registerCommand(this, "invsee", desc, usage, "widcore.inv", Arrays.asList("inv"),
                new InvseeCommand(plugin));
        plugin.getServer().getPluginManager().registerEvents(new InvseeListener(plugin), plugin);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}