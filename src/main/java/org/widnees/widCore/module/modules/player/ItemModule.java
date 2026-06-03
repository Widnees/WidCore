package org.widnees.widCore.module.modules.player;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.ItemCommand;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class ItemModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public ItemModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Item Command";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.item", false);
    }

    @Override
    public void register() {
        String desc = plugin.getLanguageManager().getMessage("item.description");
        String usage = plugin.getLanguageManager().getMessage("item.usage_args");

        moduleManager.registerCommand(this, "item", desc, usage, "widcore.i", Arrays.asList("i"),
                new ItemCommand(plugin));
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String __Wc6d8x2 = "\u0077\u0069" + "\u0064\u006e" + "\u0065\u0065\u0073";

}