package org.widnees.widCore.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.BackManager;
import org.widnees.widCore.manager.ConfigManager; 

public class BackCommand implements CommandExecutor {

    private final Main plugin;
    private final BackManager backManager;

    public BackCommand(Main plugin, BackManager backManager) {
        this.plugin = plugin;
        this.backManager = backManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!ConfigManager.isConfigLoaded()) {
            return true;
        }

        if (!(sender instanceof Player)) {
            Main.sendMessage(this.plugin, sender, plugin.getLanguageManager().getMessage("general.only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("widcore.back")) {
            Main.sendNoPermission(this.plugin, player, "widcore.back");
            return true;
        }

        Location lastDeathLocation = backManager.getLastDeathLocation(player.getUniqueId());

        if (lastDeathLocation == null) {
            Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("back.no-location"));
            return true;
        }

        player.teleportAsync(lastDeathLocation).thenAccept(success -> {
            if (success) {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("back.success"));
            } else {
                Main.sendMessage(this.plugin, player, plugin.getLanguageManager().getMessage("back.fail"));

            }
        });
        return true;
    }
}