package org.widnees.widCore.module.modules.world;

import org.widnees.widCore.Main;
import org.widnees.widCore.command.RtpCommand;
import org.widnees.widCore.listener.RtpListener;
import org.widnees.widCore.listener.RtpTownyGuardListener;
import org.widnees.widCore.listener.RtpSuccessListener;
import org.widnees.widCore.manager.RtpManager;
import org.widnees.widCore.module.Module;
import org.widnees.widCore.module.ModuleManager;
import org.widnees.widCore.hook.TownyHook;
import org.widnees.widCore.manager.RtpRetryService;

public class RtpModule implements Module {

    private final Main plugin;
    private final ModuleManager moduleManager;
    private RtpListener rtpListener;
    private RtpTownyGuardListener townyGuardListener;
    private RtpSuccessListener rtpSuccessListener;

    public RtpModule(Main plugin) {
        this.plugin = plugin;
        this.moduleManager = plugin.getModuleManager();
    }

    @Override
    public String getName() {
        return "RTP System";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("features.rtp", false);
    }

    @Override
    public void register() {
        RtpManager rtpManager = plugin.getRtpManager();
        RtpCommand rtpCommand = new RtpCommand(plugin, rtpManager);

        String rtpDesc = plugin.getLanguageManager().getMessage("rtp.description");
        String rtpUsage = plugin.getLanguageManager().getMessage("rtp.usage_args");

        moduleManager.registerCommand(this, "rtp", rtpDesc, rtpUsage, "widcore.rtp", null, rtpCommand);

        RtpRetryService.initialize(plugin);

        this.rtpListener = new RtpListener(plugin, rtpManager);
        plugin.getServer().getPluginManager().registerEvents(rtpListener, plugin);

        this.rtpSuccessListener = new RtpSuccessListener(plugin, rtpManager);
        plugin.getServer().getPluginManager().registerEvents(rtpSuccessListener, plugin);

        org.bukkit.configuration.file.FileConfiguration rtpCfg = plugin.getConfigManager().getModuleConfig("rtp");
        boolean respectClaims = rtpCfg.getBoolean("default-world.towny.respect-claims",
                rtpCfg.getBoolean("towny.respect-claims", false));
        if (respectClaims) {
            TownyHook townyHook = new TownyHook(plugin);
            if (townyHook.isAvailable()) {
                this.townyGuardListener = new RtpTownyGuardListener(plugin, rtpManager, townyHook, rtpCfg);
                plugin.getServer().getPluginManager().registerEvents(townyGuardListener, plugin);
            }
        }
    }

    @Override
    public void unregister() {
        RtpManager rtpManager = plugin.getRtpManager();
        if (rtpManager != null) {
            rtpManager.shutdown();
        }
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
