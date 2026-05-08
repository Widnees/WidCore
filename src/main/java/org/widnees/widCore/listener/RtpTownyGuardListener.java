package org.widnees.widCore.listener;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.hook.TownyHook;
import org.widnees.widCore.manager.RtpManager;
import org.widnees.widCore.manager.RtpRetryService;

import java.util.UUID;

public class RtpTownyGuardListener implements Listener {

    private final Main plugin;
    private final RtpManager rtpManager;
    private final TownyHook townyHook;
    private final FileConfiguration cfg;

    public RtpTownyGuardListener(Main plugin, RtpManager rtpManager, TownyHook townyHook, FileConfiguration moduleConfig) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
        this.townyHook = townyHook;
        this.cfg = moduleConfig;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRtpTeleport(PlayerTeleportEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();

        if (!rtpManager.isPlayerInRtp(uuid)) return;

        boolean respectClaims = cfg.getBoolean("default-world.towny.respect-claims",
                cfg.getBoolean("towny.respect-claims", false));
        if (!respectClaims) return;
        if (!townyHook.isAvailable()) return;

        final Location to = event.getTo();
        if (to == null) return;

        if (!townyHook.isClaimed(to)) return;

        event.setCancelled(true);
        RtpRetryService.markTownyCancelled(uuid);

        rtpManager.removeFromRtp(uuid);
        boolean retryEnabled = cfg.getBoolean("retry", false);
        String key = retryEnabled ? "rtp.towny-claimed" : "rtp.no-safe-location";
        String msg = plugin.getLanguageManager().getMessage(key);
        if (msg != null && !msg.isEmpty()) Main.sendMessage(plugin, player, msg);
    }
}
