package org.widnees.widCore.module.modules.world;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.WarpCommands;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;

public class WarpModule implements Module {
    private final Main plugin;
    private final ModuleManager moduleManager;

    public WarpModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() { return "Warp System"; }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.warp", false); }

    @Override
    public void register() {
        WarpCommands warpExecutor = new WarpCommands(plugin, plugin.getTeleportManager(), plugin.getWarpManager());

        String warpDesc = plugin.getLanguageManager().getMessage("warp.description");
        String warpUsage = plugin.getLanguageManager().getMessage("warp.usage_args");

        String setwarpDesc = plugin.getLanguageManager().getMessage("warp.setwarp_description");
        String setwarpUsage = plugin.getLanguageManager().getMessage("warp.setwarp_usage_args");

        String delwarpDesc = plugin.getLanguageManager().getMessage("warp.delwarp_description");
        String delwarpUsage = plugin.getLanguageManager().getMessage("warp.delwarp_usage_args");

        moduleManager.registerCommand(this, "warp", warpDesc, warpUsage, "widcore.warp", null, warpExecutor);
        moduleManager.registerCommand(this, "setwarp", setwarpDesc, setwarpUsage, "widcore.setwarp", null, warpExecutor);
        moduleManager.registerCommand(this, "delwarp", delwarpDesc, delwarpUsage, "widcore.delwarp", null, warpExecutor);

        if (plugin.getConfig().getBoolean("features.spawn", false)) {
            plugin.getServer().getPluginManager().registerEvents(new org.widnees.widCore.listener.TeleportDamageListener(plugin, plugin.getTeleportManager(), plugin.getTeleportAnimator()), plugin);
        }
    }

    @Override
    public void unregister() {}
}