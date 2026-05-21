package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.CommandAccessManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandGuardListener implements Listener {

    private final Main plugin;
    private final CommandAccessManager access;

    public CommandGuardListener(Main plugin, CommandAccessManager access) {
        this.plugin = plugin;
        this.access = access;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();

        String msg = event.getMessage(); 
        if (msg == null || msg.isEmpty()) return;

        String raw = msg.startsWith("/") ? msg.substring(1) : msg;
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return;

        String token0 = tokens[0];

        int ns = token0.indexOf(':');
        String root = ns >= 0 && ns + 1 < token0.length() ? token0.substring(ns + 1) : token0;
        List<String> args = new ArrayList<>();
        if (tokens.length > 1) {
            args.addAll(Arrays.asList(tokens).subList(1, tokens.length));
        }

        boolean serverInfoHider;
        if (plugin.getConfig().isSet("features.pluginhider")) {
            serverInfoHider = plugin.getConfig().getBoolean("features.pluginhider", false);
        } else {
            serverInfoHider = plugin.getConfig().getBoolean("features.serverinfohider", false);
        }
        if (serverInfoHider) {
            String lower = root.toLowerCase(java.util.Locale.ROOT);
            if (lower.equals("plugins") || lower.equals("pl") || lower.equals("version") || lower.equals("ver")) {
                return; 
            }
        }

        if (!access.isExecutionAllowed(player, root, args)) {

            event.setMessage("/widcore_blocked");
            return;
        }
    }
}
