package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.JailCommand;
import org.widnees.widCore.listener.JailListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class JailModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public JailModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Jail System";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.jail", false);
    }

    @Override
    public void register() {
        JailCommand jailExecutor = new JailCommand(plugin);

        String jailDesc = plugin.getLanguageManager().getMessage("jail.description");
        String jailUsage = plugin.getLanguageManager().getMessage("jail.usage_args");

        String unjailDesc = plugin.getLanguageManager().getMessage("jail.unjail_description");
        String unjailUsage = plugin.getLanguageManager().getMessage("jail.unjail_usage_args");

        String setjailDesc = plugin.getLanguageManager().getMessage("jail.setjail_description");
        String setjailUsage = plugin.getLanguageManager().getMessage("jail.setjail_usage_args");

        String deljailDesc = plugin.getLanguageManager().getMessage("jail.deljail_description");
        String deljailUsage = plugin.getLanguageManager().getMessage("jail.deljail_usage_args");

        moduleManager.registerCommand(this, "jail", jailDesc, jailUsage, "widcore.jail.use", null, jailExecutor);
        moduleManager.registerCommand(this, "unjail", unjailDesc, unjailUsage, "widcore.jail.remove", null,
                jailExecutor);
        moduleManager.registerCommand(this, "setjail", setjailDesc, setjailUsage, "widcore.jail.set", null,
                jailExecutor);
        moduleManager.registerCommand(this, "deljail", deljailDesc, deljailUsage, "widcore.jail.delete", null,
                jailExecutor);

        plugin.getServer().getPluginManager().registerEvents(new JailListener(plugin), plugin);
    }

    @Override
    public void unregister() {
    }
}