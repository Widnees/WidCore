package org.widnees.widCore.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.widnees.widCore.Main;
import org.widnees.widCore.command.GamemodeCommand;
import org.widnees.widCore.manager.ConfigManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class GamemodeListener implements Listener {
    private final Main plugin;
    private final GamemodeCommand gamemodeExecutor;
    private final Map<String, String> commandAliases = new HashMap<>();

    public GamemodeListener(Main plugin, GamemodeCommand executor) {
        this.plugin = plugin;
        this.gamemodeExecutor = executor;

        commandAliases.put("gmc", "c");
        commandAliases.put("gms", "s");
        commandAliases.put("gma", "a");
        commandAliases.put("gmsp", "spec");
        commandAliases.put("gm1", "1");
        commandAliases.put("gm0", "0");
        commandAliases.put("gm2", "2");
        commandAliases.put("gm3", "3");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("features.gamemode", false)) {
            return;
        }

        String message = event.getMessage().substring(1);
        String[] parts = message.split(" ");
        String commandLabel = parts[0].toLowerCase();
        Player player = event.getPlayer();

        if (commandLabel.equals("gamemode")) {
            String[] args = Arrays.copyOfRange(parts, 1, parts.length);
            event.setCancelled(true);
            gamemodeExecutor.onCommand(player, null, "gm", args);
            return;
        }

        if (parts.length == 1 && commandAliases.containsKey(commandLabel)) {
            String gamemodeArg = commandAliases.get(commandLabel);
            String[] newArgs = { gamemodeArg };

            event.setCancelled(true);
            gamemodeExecutor.onCommand(player, null, "gm", newArgs);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (!ConfigManager.isConfigLoaded()) {
            return;
        }

        if (!plugin.getConfig().getBoolean("features.gamemode", false)) {
            return;
        }

        Player player = event.getPlayer();
        GameMode newMode = event.getNewGameMode();

        String modeName = getGameModeName(newMode);
        String message = plugin.getLanguageManager().getMessage("gamemode.changed")
                .replace("%mode%", modeName);

        Main.sendMessage(plugin, player, message);
    }

    private String getGameModeName(GameMode mode) {
        switch (mode) {
            case SURVIVAL:
                return "Hayatta Kalma";
            case CREATIVE:
                return "Yaratıcı";
            case ADVENTURE:
                return "Macera Modu";
            case SPECTATOR:
                return "İzleyici";
            default:
                return mode.name();
        }
    }
        @SuppressWarnings("unused")
    private static final String _0xWb8d2e = "\u0077\u0069\u0064" + "\u006e\u0065" + "\u0065\u0073";

}