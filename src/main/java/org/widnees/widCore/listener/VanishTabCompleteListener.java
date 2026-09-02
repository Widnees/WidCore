package org.widnees.widCore.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.VanishManager;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VanishTabCompleteListener implements Listener {

    private final Main plugin;

    public VanishTabCompleteListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        VanishManager vanishManager = plugin.getVanishManager();
        if (vanishManager == null || vanishManager.getVanishedCount() == 0) {
            return;
        }

        Player viewer = event.getSender() instanceof Player ? (Player) event.getSender() : null;
        if (viewer != null && (viewer.isOp() || viewer.hasPermission("widcore.vanish.see"))) {
            return;
        }

        List<String> completions = event.getCompletions();
        if (completions == null || completions.isEmpty()) {
            return;
        }

        Set<String> vanishedNames = new HashSet<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (vanishManager.isVanished(online)
                    && (viewer == null || vanishManager.isHiddenFrom(online, viewer))) {
                vanishedNames.add(online.getName());
                vanishedNames.add(online.getName().toLowerCase(Locale.ROOT));
            }
        }
        if (vanishedNames.isEmpty()) {
            return;
        }

        completions.removeIf(completion -> {
            if (completion == null) {
                return false;
            }
            String trimmed = completion.trim();
            return vanishedNames.contains(trimmed)
                    || vanishedNames.contains(trimmed.toLowerCase(Locale.ROOT));
        });
    }
}
