package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.widnees.widCore.manager.CommandAccessManager;

import java.util.*;

public class TabCompleteGuardListener implements Listener {

    private final CommandAccessManager access;

    public TabCompleteGuardListener(CommandAccessManager access) {
        this.access = access;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (access.hasBypass(player)) return;

        Set<String> allowed = new HashSet<>(event.getCommands());
        for (String cmd : event.getCommands()) {
            String root = extractRoot(cmd);
            if (!access.isRootVisible(player, root) && !access.isRootVisible(player, cmd)) {
                allowed.remove(cmd);
            }
        }
        event.getCommands().clear();
        event.getCommands().addAll(allowed);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (event.isCancelled()) return;
        String buffer = event.getBuffer();
        if (buffer == null || !buffer.startsWith("/")) return; 

        Player player = event.getSender() instanceof Player ? (Player) event.getSender() : null;
        if (access.hasBypass(player)) return;

        String raw = buffer.substring(1); 
        String[] tokens = raw.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return;

        String root = tokens[0];
        if (!access.isRootVisible(player, root)) {

            event.getCompletions().clear();
            return;
        }

        boolean endsWithSpace = buffer.endsWith(" ");
        List<String> allArgs = new ArrayList<>();
        if (tokens.length > 1) {
            allArgs.addAll(Arrays.asList(tokens).subList(1, tokens.length));
        }
        List<String> prevArgs = new ArrayList<>(allArgs);
        if (!endsWithSpace && !prevArgs.isEmpty()) {

            prevArgs = prevArgs.subList(0, prevArgs.size() - 1);
        }

        if (!access.isTabPathAllowed(player, root, prevArgs)) {
            event.getCompletions().clear();
        }
    }

    private String extractRoot(String s) {
        if (s == null) return "";
        int idx = s.indexOf(':');
        if (idx >= 0 && idx + 1 < s.length()) return s.substring(idx + 1);
        return s;
    }
}
