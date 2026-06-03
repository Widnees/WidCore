package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.RepairCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class RepairModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public RepairModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Repair Command"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.repair", false); }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("repair.description");
        String usage = plugin.getLanguageManager().getMessage("repair.usage_args");

        moduleManager.registerCommand(this, "repair", desc, usage, "widcore.repair", Arrays.asList("tamir"), new RepairCommand(plugin));
    }

    @Override
    public void unregister() {}
        @SuppressWarnings("unused")
    private static final String _0xWb8d2e = "\u0077\u0069\u0064" + "\u006e\u0065" + "\u0065\u0073";

}