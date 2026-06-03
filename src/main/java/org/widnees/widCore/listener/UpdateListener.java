package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.widnees.widCore.Main;

public class UpdateListener implements Listener {

    private final Main plugin;

    public UpdateListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.isOp() && plugin.getUpdateManager().isUpdateAvailable()) {
            plugin.getUpdateManager().notifySender(player);
        }
    }
        @SuppressWarnings("unused")
    private static final String __Wx7c4e2 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}