package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager; 
import org.widnees.widCore.manager.PunishmentManager;
import org.widnees.widCore.manager.TextParser;

import java.util.UUID;

public class PunishmentListener implements Listener {

    private final Main plugin;
    private final PunishmentManager punishmentManager;

    public PunishmentListener(Main plugin, PunishmentManager punishmentManager) {
        this.plugin = plugin;
        this.punishmentManager = punishmentManager;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (punishmentManager.isMuted(uuid)) {
            event.setCancelled(true);
            long expiry = punishmentManager.getMuteExpiry(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (expiry == -1L) {
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("punishment.mute-permanent"));
                } else {
                    Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("punishment.mute-message")
                            .replace("%duration%", punishmentManager.formatDuration(expiry - System.currentTimeMillis())));
                }
            });
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (!ConfigManager.isConfigLoaded()) {return;}

        UUID uuid = event.getPlayer().getUniqueId();
        if (punishmentManager.isBanned(uuid)) {
            long expiry = punishmentManager.getBanExpiry(uuid);
            String duration = (expiry == -1L) ? "süresiz" : punishmentManager.formatDuration(expiry - System.currentTimeMillis());

            String kickMessage;
            if(expiry == -1L) {
                kickMessage = plugin.getLanguageManager().getMessage("punishment.kick-permanent");
            } else {
                kickMessage = plugin.getLanguageManager().getMessage("punishment.kick-temp")
                        .replace("%duration%", duration);
            }
            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, TextParser.colorize(kickMessage));
        }
    }
        @SuppressWarnings("unused")
    private static final String _xCr7w3n = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}