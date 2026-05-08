package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.FreezeCommand;
import org.widnees.widCore.listener.FreezeListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class FreezeModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public FreezeModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Freeze Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.freeze", false); }

    @Override
    public void register() {
        FreezeListener freezeListener = new FreezeListener(plugin, plugin.getPunishmentManager());
        FreezeCommand freezeExecutor = new FreezeCommand(plugin, plugin.getPunishmentManager());

        String freezeDesc = plugin.getLanguageManager().getMessage("freeze.description");
        String freezeUsage = plugin.getLanguageManager().getMessage("freeze.usage_args");

        String unfreezeDesc = plugin.getLanguageManager().getMessage("freeze.unfreeze_description");
        String unfreezeUsage = plugin.getLanguageManager().getMessage("freeze.unfreeze_usage_args");

        moduleManager.registerCommand(this, "freeze", freezeDesc, freezeUsage, "widcore.freeze", null, freezeExecutor);
        moduleManager.registerCommand(this, "unfreeze", unfreezeDesc, unfreezeUsage, "widcore.freeze", Arrays.asList("defrost"), freezeExecutor);

        plugin.getServer().getPluginManager().registerEvents(freezeListener, plugin);
    }

    @Override
    public void unregister() {}
}