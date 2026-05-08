package org.widnees.widCore.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.ConfigManager;

public class FeedCommand implements CommandExecutor {

    private final Main plugin;

    public FeedCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("widcore.feed")) {
                Main.sendNoPermission(this.plugin, player, "widcore.feed");
                return true;
            }

            player.setFoodLevel(20);
            player.setSaturation(20F);
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("feed.success-self"));
            return true;

        } else if (args.length == 1) {
            if (!sender.hasPermission("widcore.feed.other")) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("feed.no-perm-other"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.player-not-found")
                        .replace("%player%", args[0]));
                return true;
            }

            target.setFoodLevel(20);
            target.setSaturation(20F);
            Main.sendMessage(this.plugin, sender,
                    plugin.getLanguageManager().getMessage("feed.success-other").replace("%player%", target.getName()));
            Main.sendMessage(plugin, target, plugin.getLanguageManager().getMessage("feed.success-target"));
            return true;

        } else {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("feed.usage"));
            return true;
        }
    }
}