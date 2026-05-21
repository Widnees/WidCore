package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class GamemodeCommand implements CommandExecutor {

    private final Main plugin;

    public GamemodeCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length == 0) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("gamemode.usage"));
            return true;
        }

        GameMode gameMode;
        String permission;
        String modeName;

        String modeKey = plugin.getAliasManager().matchMode("gamemode", args[0]);

        if (modeKey == null) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("gamemode.invalid-mode"));
            return true;
        }

        switch (modeKey.toLowerCase()) {
            case "survival":
                gameMode = GameMode.SURVIVAL;
                permission = "widcore.gamemode.survival";
                modeName = "Hayatta Kalma";
                break;
            case "creative":
                gameMode = GameMode.CREATIVE;
                permission = "widcore.gamemode.creative";
                modeName = "Yaratıcı";
                break;
            case "adventure":
                gameMode = GameMode.ADVENTURE;
                permission = "widcore.gamemode.adventure";
                modeName = "Maceracı";
                break;
            case "spectator":
                gameMode = GameMode.SPECTATOR;
                permission = "widcore.gamemode.spectator";
                modeName = "İzleyici";
                break;
            default:
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("gamemode.invalid-mode"));
                return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender,
                        plugin.getLanguageManager().getMessage("gamemode.specify-player"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission(permission)) {
                Main.sendNoPermission(this.plugin, player, permission);
                return true;
            }
            player.setGameMode(gameMode);

        } else {
            if (!sender.hasPermission("widcore.gamemode.other")) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("gamemode.no-perm-other"));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[1]));
                return true;
            }
            target.setGameMode(gameMode);

            String msgSender = plugin.getLanguageManager().getMessage("gamemode.changed-other")
                    .replace("%player%", target.getName())
                    .replace("%mode%", modeName);
            Main.sendMessage(this.plugin, sender, msgSender);

            String msgTarget = plugin.getLanguageManager().getMessage("gamemode.changed-by")
                    .replace("%player%", sender.getName())
                    .replace("%mode%", modeName);
            Main.sendMessage(this.plugin, target, msgTarget);
        }
        return true;
    }
}