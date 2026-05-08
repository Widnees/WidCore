package org.widnees.widCore.module.modules.server;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.ItemEditCommand;
import org.widnees.widCore.listener.ItemEffectListener;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class ItemEditModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public ItemEditModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Item Editor"; }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.itemedit_name", false) ||
                plugin.getConfig().getBoolean("features.itemedit_lore", false) ||
                plugin.getConfig().getBoolean("features.itemedit_unbreaking", false) ||
                plugin.getConfig().getBoolean("features.itemedit_attribute", false) ||
                plugin.getConfig().getBoolean("features.itemedit_hide", false) ||
                plugin.getConfig().getBoolean("features.itemedit_color", false) ||
                plugin.getConfig().getBoolean("features.itemedit_enchant", false) ||
                plugin.getConfig().getBoolean("features.itemedit_effect", false);
    }

    @Override
    public void register() {
        ItemEditCommand itemEditExecutor = new ItemEditCommand(plugin);

        String mainDesc = plugin.getLanguageManager().getMessage("itemedit.description");
        String mainUsage = plugin.getLanguageManager().getMessage("itemedit.usage_args");

        moduleManager.registerCommand(this, "itemedit", mainDesc, mainUsage, "widcore.itemedit.name", Arrays.asList("ie"), itemEditExecutor);

        if (plugin.getConfig().getBoolean("features.itemedit_name", false)) {
            String nameDesc = plugin.getLanguageManager().getMessage("itemedit.iname_description");
            String nameUsage = plugin.getLanguageManager().getMessage("itemedit.iname_usage_args");
            moduleManager.registerCommand(this, "iname", nameDesc, nameUsage, "widcore.itemedit.name", Arrays.asList("irename"), itemEditExecutor);
        }
        if (plugin.getConfig().getBoolean("features.itemedit_lore", false)) {
            String loreDesc = plugin.getLanguageManager().getMessage("itemedit.ilore_description");
            String loreUsage = plugin.getLanguageManager().getMessage("itemedit.ilore_usage_args");
            moduleManager.registerCommand(this, "ilore", loreDesc, loreUsage, "widcore.itemedit.lore", null, itemEditExecutor);
        }
        if (plugin.getConfig().getBoolean("features.itemedit_enchant", false)) {
            String enchantDesc = plugin.getLanguageManager().getMessage("itemedit.enchant_description");
            String enchantUsage = plugin.getLanguageManager().getMessage("itemedit.enchant_usage_args");
            moduleManager.registerCommand(this, "enchant", enchantDesc, enchantUsage, "widcore.itemedit.enchant", null, itemEditExecutor);
        }
        if (plugin.getConfig().getBoolean("features.itemedit_effect", false)) {
            plugin.getServer().getPluginManager().registerEvents(new ItemEffectListener(plugin, plugin.getItemEffectManager()), plugin);
        }
    }

    @Override
    public void unregister() {}
}