package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.RtpManager;

public class RtpListener implements Listener {

    private final Main plugin;
    private final RtpManager rtpManager;

    public RtpListener(Main plugin, RtpManager rtpManager) {
        this.plugin = plugin;
        this.rtpManager = rtpManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!rtpManager.isPlayerInRtp(player.getUniqueId())) {
            return;
        }

        if (!plugin.getConfigManager().getModuleConfig("rtp").getBoolean("freeze-on-teleport", false)) {
            return;
        }

        if (event.getFrom().getY() < event.getTo().getY()) {

            event.setTo(event.getFrom());
        }
    }
}
