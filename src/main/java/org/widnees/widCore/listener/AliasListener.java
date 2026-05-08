package org.widnees.widCore.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.SpawnCommand;
import org.widnees.widCore.manager.AliasManager;
import org.widnees.widCore.manager.ConfigManager;

import java.util.List;
import java.util.stream.Collectors;

public class AliasListener implements Listener {

    private final Main plugin;
    private final SpawnCommand spawnCommandExecutor;
    private List<String> spawnAliases;

    public AliasListener(Main plugin, SpawnCommand spawnCommandExecutor) {
        this.plugin = plugin;
        this.spawnCommandExecutor = spawnCommandExecutor;
        loadAliases();
    }

    public void loadAliases() {
        AliasManager aliasManager = plugin.getAliasManager();
        this.spawnAliases = aliasManager.getAliases("spawn")
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("features.spawn", false)) {
            return;
        }

        String[] parts = event.getMessage().substring(1).split(" ");
        if (parts.length == 0) {
            return;
        }

        String commandLabel = parts[0].toLowerCase();
        Player player = event.getPlayer();

        if (spawnAliases.contains(commandLabel)) {
            event.setCancelled(true);
            spawnCommandExecutor.onCommand(player, null, commandLabel, new String[0]);
        }
    }
}