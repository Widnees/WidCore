package org.widnees.widCore.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.RtpManager;
import org.widnees.widCore.manager.RtpRetryService;

import java.util.UUID;

public class RtpSuccessListener implements Listener {

    private final Main plugin;
    private final RtpManager rtpManager;

    public RtpSuccessListener(Main plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (rtpManager.isPlayerInRtp(uuid)) {

            RtpRetryService.markSuccess(uuid);
        }
    }
        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
