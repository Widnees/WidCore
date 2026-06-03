package org.widnees.widCore.module.modules.admin;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.TrollCommand;
import org.widnees.widCore.listener.TrollListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class TrollModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public TrollModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Troll Commands"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.troll", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("troll.description");
        String usage = plugin.getLanguageManager().getMessage("troll.usage_args");

        TrollCommand trollCommandExecutor = new TrollCommand(plugin, plugin.getTrollManager());
        moduleManager.registerCommand(this, "troll", desc, usage, "widcore.troll", null, trollCommandExecutor);
        plugin.getServer().getPluginManager().registerEvents(new TrollListener(plugin, plugin.getTrollManager(), trollCommandExecutor), plugin);
    }

    @Override
    public void unregister() {}
        @SuppressWarnings("unused")
    private static final String __Wc6d8x2 = "\u0077\u0069" + "\u0064\u006e" + "\u0065\u0065\u0073";

}