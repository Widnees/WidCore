package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.CommandAccessManager;
import org.widnees.widCore.manager.VanishManager;

import java.util.*;

public class TabCompleteGuardListener implements Listener {

    private final CommandAccessManager access;
    private final Main plugin;

    public TabCompleteGuardListener(CommandAccessManager access, Main plugin) {
        this.access = access;
        this.plugin = plugin;
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (access.hasBypass(player)) return;

        Set<String> allowed = new HashSet<>(event.getCommands());
        for (String cmd : event.getCommands()) {
            if (cmd.contains(":")) {
                allowed.remove(cmd);
                continue;
            }
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

        filterVanishedPlayerCompletions(event, player);

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


    private void filterVanishedPlayerCompletions(TabCompleteEvent event, Player viewer) {
        if (plugin == null) return;
        VanishManager vanishManager = plugin.getVanishManager();
        if (vanishManager == null || vanishManager.getVanishedCount() == 0) return;
        if (viewer != null && viewer.hasPermission("widcore.vanish.see")) return;

        List<String> completions = event.getCompletions();
        if (completions == null || completions.isEmpty()) return;

        Set<String> vanishedNames = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (vanishManager.isVanished(online)
                    && (viewer == null || !vanishManager.canSee(viewer, online))) {
                vanishedNames.add(online.getName());
                vanishedNames.add(online.getName().toLowerCase(Locale.ROOT));
            }
        }
        if (vanishedNames.isEmpty()) return;

        completions.removeIf(completion -> {
            if (completion == null) return false;
            String trimmed = completion.trim();
            return vanishedNames.contains(trimmed) || vanishedNames.contains(trimmed.toLowerCase(Locale.ROOT));
        });
    }

    private String extractRoot(String s) {

        if (s == null) return "";
        int idx = s.indexOf(':');
        if (idx >= 0 && idx + 1 < s.length()) return s.substring(idx + 1);
        return s;
    }
        @SuppressWarnings("unused")
    private static final String __W5e9c3x = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
