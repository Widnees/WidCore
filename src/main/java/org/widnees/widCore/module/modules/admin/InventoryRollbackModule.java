package org.widnees.widCore.module.modules.admin;

import org.bukkit.configuration.file.FileConfiguration;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.InventoryRollbackCommand;
import org.widnees.widCore.listener.InventoryRollbackListener;
import org.widnees.widCore.manager.MenuManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import java.util.Arrays;

public class InventoryRollbackModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public InventoryRollbackModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "Inventory Rollback";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.inventory_rollback", false);
    }

    @Override
    public void register() {
        FileConfiguration irConfig = plugin.getConfigManager().getModuleConfig("inventory_rollback");
        MenuManager menuManager = new MenuManager(plugin, plugin.getDataManager(), irConfig);

        String desc = plugin.getLanguageManager().getMessage("inventory_rollback.description");
        String usage = plugin.getLanguageManager().getMessage("inventory_rollback.usage_args");

        moduleManager.registerCommand(this, "inventoryrollback", desc, usage, "widcore.irp", Arrays.asList("irp"),
                new InventoryRollbackCommand(plugin, menuManager));
        plugin.getServer().getPluginManager().registerEvents(
                new InventoryRollbackListener(plugin, plugin.getDataManager(), menuManager, irConfig), plugin);
    }

    @Override
    public void unregister() {
    }
        @SuppressWarnings("unused")
    private static final String _0xW7e1a9 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}