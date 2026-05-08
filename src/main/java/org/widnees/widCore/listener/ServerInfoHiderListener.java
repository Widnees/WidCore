package org.widnees.widCore.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ServerInfoHiderListener implements Listener {

    private final FileConfiguration cfg;
    private final org.widnees.widCore.Main plugin;

    private final Set<String> pluginCmdRoots = new HashSet<>(Arrays.asList(
            "plugins", "pl", "bukkit:plugins", "bukkit:pl"
    ));
    private final Set<String> versionCmdRoots = new HashSet<>(Arrays.asList(
            "version", "ver", "about", "?", "help", "bukkit:version", "bukkit:ver", "bukkit:about", "bukkit:help", "bukkit:?", "icanhasbukkit"
    ));

    public ServerInfoHiderListener(org.widnees.widCore.Main plugin, FileConfiguration moduleConfig) {
        this.plugin = plugin;
        this.cfg = moduleConfig;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        Player p = event.getPlayer();
        String msg = event.getMessage();
        if (msg == null || msg.isEmpty()) return;
        String raw = msg.startsWith("/") ? msg.substring(1) : msg;
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length == 0) return;
        String root = tokens[0].toLowerCase(Locale.ROOT);

        if (p.isOp()) {
            return;
        }

        String toSend = null;
        if (pluginCmdRoots.contains(root)) {
            toSend = cfg.getString("messages.plugins-blocked", "&cPlugin list is hidden.");
        } else if (versionCmdRoots.contains(root)) {
            toSend = cfg.getString("messages.version-blocked", "&cServer version is hidden.");
        }

        if (toSend != null) {
            event.setCancelled(true);
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', toSend));
        }
    }
}
